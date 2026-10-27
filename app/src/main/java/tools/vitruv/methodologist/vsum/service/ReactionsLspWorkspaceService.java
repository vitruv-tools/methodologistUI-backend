package tools.vitruv.methodologist.vsum.service;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.vitruv.methodologist.general.model.FileStorage;
import tools.vitruv.methodologist.vsum.model.MetaModel;
import tools.vitruv.methodologist.vsum.utilitymethod.EcoreInstanceClassNames;
import tools.vitruv.methodologist.vsum.utilitymethod.EcoreTypeIndex;
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

  static final String PROJECT_DIR = "UserProject";
  static final String MODEL_DIR = "model";
  static final String CLASSES_DIR = "classes";

  /** Class file version the language server reads, i.e. the Java version it is built for. */
  private static final String RELEASE = "17";

  private final MetaModelService metaModelService;
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
        metaModelService.findAccessibleByProject(vsumId).stream()
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
      log.warn("Metamodel {} keeps unresolved types in the reactions editor", ecore.getId(), e);
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
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    if (compiler == null) {
      log.warn("No Java compiler available, the reactions editor cannot resolve the {}", what);
      return false;
    }
    try {
      Files.createDirectories(outputDir);
    } catch (IOException e) {
      log.warn("Could not create {} for the reactions editor", outputDir, e);
      return false;
    }
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    List<String> options =
        List.of(
            "-proc:none",
            "-nowarn",
            "--release",
            RELEASE,
            "-classpath",
            classpath.stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator)),
            "-d",
            outputDir.toString());
    List<JavaFileObject> units = new ArrayList<>();
    sources.forEach((name, source) -> units.add(new SourceUnit(name, source)));
    boolean compiled;
    try (StandardJavaFileManager fileManager =
        compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
      compiled = compiler.getTask(null, fileManager, diagnostics, options, null, units).call();
    } catch (IOException e) {
      log.warn("Could not compile the {} for the reactions editor", what, e);
      return false;
    }
    if (!compiled) {
      log.warn(
          "Could not compile the {} for the reactions editor: {}",
          what,
          diagnostics.getDiagnostics().stream()
              .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
              .map(d -> d.getMessage(null))
              .limit(5)
              .collect(Collectors.joining("; ")));
    }
    return compiled;
  }

  /** An in-memory source file. */
  private static final class SourceUnit extends SimpleJavaFileObject {
    private final String source;

    SourceUnit(String qualifiedName, String source) {
      super(
          URI.create("string:///" + qualifiedName.replace('.', '/') + Kind.SOURCE.extension),
          Kind.SOURCE);
      this.source = source;
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
      return source;
    }
  }
}
