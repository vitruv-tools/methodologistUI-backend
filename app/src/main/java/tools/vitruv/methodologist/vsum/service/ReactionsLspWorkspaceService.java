package tools.vitruv.methodologist.vsum.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.vitruv.methodologist.general.model.FileStorage;
import tools.vitruv.methodologist.vsum.model.MetaModel;
import tools.vitruv.methodologist.vsum.utilitymethod.EcoreInstanceClassNames;
import tools.vitruv.methodologist.vsum.utilitymethod.EcoreTypeIndex;
import tools.vitruv.methodologist.vsum.utilitymethod.JavaSourceCompiler;
import tools.vitruv.methodologist.vsum.utilitymethod.ModelApiSourceGenerator;
import tools.vitruv.methodologist.vsum.utilitymethod.UtilityMethodClassGenerator;

/**
 * Prepares the workspace of a reactions editor's language server.
 *
 * <p>The language server resolves Java types from its own classpath only and derives the Java type
 * of a metaclass from the metaclass's instance class name. Besides the VSUM's {@code .ecore} files,
 * the workspace therefore gets the API EMF would generate for the metamodels and the utility method
 * class, compiled into a directory that is put on the server's classpath, and the {@code .ecore}
 * files name the generated interfaces as instance classes. If that is not possible, for example
 * because the backend runs without a Java compiler, the workspace only gets the plain {@code
 * .ecore} files, and metaclasses and utility methods stay unresolved in the editor.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReactionsLspWorkspaceService {

  /** Whether the missing Java compiler was logged, so that it is logged only once. */
  private static final AtomicBoolean NO_COMPILER_LOGGED = new AtomicBoolean();

  static final String PROJECT_DIR = "UserProject";
  static final String MODEL_DIR = "model";
  static final String CLASSES_DIR = "classes";

  private final VsumMetaModelService vsumMetaModelService;
  private final UtilityMethodService utilityMethodService;

  /**
   * A prepared workspace.
   *
   * @param projectDir the directory to start the language server in, the workspace root
   * @param classpath directories to put on the language server's classpath, possibly empty
   */
  public record Workspace(Path projectDir, List<Path> classpath) {

    /**
     * The command that starts the language server for this workspace.
     *
     * @param javaExecutable the {@code java} executable
     * @param serverJar the language server jar
     * @return the command
     * @throws IOException if the jar cannot be read or names no main class
     */
    public List<String> launchCommand(String javaExecutable, Path serverJar) throws IOException {
      if (classpath.isEmpty()) {
        return List.of(javaExecutable, "-jar", serverJar.toString());
      }
      List<String> entries = new ArrayList<>();
      entries.add(serverJar.toString());
      classpath.forEach(entry -> entries.add(entry.toString()));
      return List.of(
          javaExecutable, "-cp", String.join(File.pathSeparator, entries), mainClass(serverJar));
    }

    private static String mainClass(Path serverJar) throws IOException {
      try (JarFile jar = new JarFile(serverJar.toFile())) {
        Manifest manifest = jar.getManifest();
        String mainClass =
            manifest == null
                ? null
                : manifest.getMainAttributes().getValue(Attributes.Name.MAIN_CLASS);
        if (mainClass == null || mainClass.isBlank()) {
          throw new IOException("The language server jar names no main class: " + serverJar);
        }
        return mainClass;
      }
    }
  }

  /**
   * Writes the workspace of the given VSUM into a session directory.
   *
   * @param sessionDir the session's empty temporary directory
   * @param vsumId the VSUM ID, or {@code null} for a workspace without metamodels
   * @param serverJar the language server jar, which provides EMF for compiling the model API
   * @return the workspace
   * @throws IOException if the {@code .ecore} files cannot be written
   */
  public Workspace prepare(Path sessionDir, Long vsumId, Path serverJar) throws IOException {
    Path projectDir = sessionDir.resolve(PROJECT_DIR);
    Path modelDir = Files.createDirectories(projectDir.resolve(MODEL_DIR));
    List<FileStorage> ecores =
        vsumMetaModelService.findAccessibleByProject(vsumId).stream()
            .map(MetaModel::getEcoreFile)
            .toList();

    Path classesDir = sessionDir.resolve(CLASSES_DIR);
    boolean modelCompiled = compileModelApi(ecores, serverJar, classesDir);
    for (FileStorage ecore : ecores) {
      Files.write(
          modelDir.resolve(ecore.getFilename()),
          modelCompiled ? withInstanceClassNames(ecore) : ecore.getData());
    }
    boolean utilityMethodsCompiled =
        vsumId != null && compileUtilityMethods(vsumId, ecores, serverJar, classesDir);
    List<Path> classpath =
        modelCompiled || utilityMethodsCompiled ? List.of(classesDir) : List.of();
    return new Workspace(projectDir, classpath);
  }

  private boolean compileModelApi(List<FileStorage> ecores, Path serverJar, Path classesDir) {
    if (ecores.isEmpty()) {
      return false;
    }
    Map<String, String> sources = ModelApiSourceGenerator.generate(index(ecores));
    return compile("metamodel API", sources, List.of(serverJar), classesDir);
  }

  private boolean compileUtilityMethods(
      Long vsumId, List<FileStorage> ecores, Path serverJar, Path classesDir) {
    Optional<String> source = utilityMethodService.generateEditorSource(vsumId, ecores);
    return source.isPresent()
        && compile(
            "utility methods",
            Map.of(UtilityMethodClassGenerator.QUALIFIED_CLASS_NAME, source.get()),
            List.of(serverJar, classesDir),
            classesDir);
  }

  private static byte[] withInstanceClassNames(FileStorage ecore) {
    try {
      return EcoreInstanceClassNames.add(ecore.getData());
    } catch (IOException e) {
      log.warn(
          "Metamodel file {} keeps unresolved types in the reactions editor: {}",
          ecore.getId(),
          e.toString());
      return ecore.getData();
    }
  }

  private static EcoreTypeIndex index(List<FileStorage> ecores) {
    return EcoreTypeIndex.of(
        ecores.stream()
            .filter(file -> file.getData() != null)
            .map(file -> new EcoreTypeIndex.EcoreFile(file.getFilename(), file.getData()))
            .toList());
  }

  // ── compiling ────────────────────────────────────────────────────────────

  /**
   * Compiles the sources into {@code outputDir}.
   *
   * @param what what is compiled, for the log
   * @param sources the sources, keyed by the qualified name of their type
   * @return whether the sources compiled
   */
  private static boolean compile(
      String what, Map<String, String> sources, List<Path> classpath, Path outputDir) {
    if (sources.isEmpty()) {
      return false;
    }
    if (!JavaSourceCompiler.isAvailable()) {
      if (NO_COMPILER_LOGGED.compareAndSet(false, true)) {
        log.warn(
            "No Java compiler available (a JRE instead of a JDK?), the reactions editor cannot"
                + " resolve metamodel types and utility methods");
      }
      return false;
    }
    try {
      JavaSourceCompiler.Outcome outcome =
          JavaSourceCompiler.compile(sources, classpath, outputDir);
      if (!outcome.compiled()) {
        // the user sees these errors in the editor; for utility methods they are user mistakes
        log.debug(
            "Could not compile the {} for the reactions editor: {}",
            what,
            String.join("; ", outcome.errors()));
      }
      return outcome.compiled();
    } catch (IOException e) {
      log.warn("Could not compile the {} for the reactions editor", what, e);
      return false;
    }
  }
}
