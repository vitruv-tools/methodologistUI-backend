package tools.vitruv.methodologist.vsum.utilitymethod;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import tools.vitruv.methodologist.general.model.FileStorage;

/**
 * The classpath utility methods are compiled against: the API EMF generates for the VSUM's
 * metamodels, compiled from {@link ModelApiSourceGenerator}'s sources, and the reactions editor's
 * language server jar, which provides EMF and the Xbase library in the versions reactions use.
 *
 * <p>The compiled metamodel API is cached per set of metamodels, so that previews only compile the
 * utility methods.
 */
@Slf4j
@Component
public class CompileClasspath {

  /** Number of compiled metamodel APIs kept. */
  static final int MAX_CACHED = 32;

  private final Resource serverJarResource;
  private final Map<String, Path> modelApis =
      new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Path> eldest) {
          if (size() <= MAX_CACHED) {
            return false;
          }
          deleteQuietly(eldest.getValue());
          return true;
        }
      };
  private Path workDir;
  private Path serverJar;

  /**
   * The entries of a classpath.
   *
   * @param paths directories and jars, in lookup order
   */
  public record Entries(List<Path> paths) {

    /** Copies the list. */
    public Entries {
      paths = List.copyOf(paths);
    }
  }

  /**
   * Creates the classpath provider.
   *
   * @param serverJarResource the reactions language server jar
   */
  public CompileClasspath(
      @Value("${reactions.ide.jar.path:classpath:lsp/tools.vitruv.dsls.reactions.ide.jar}")
          Resource serverJarResource) {
    this.serverJarResource = serverJarResource;
  }

  /**
   * Returns the classpath for compiling the utility methods of a VSUM.
   *
   * @param ecores the VSUM's metamodel files
   * @return the compiled metamodel API, if there are metamodels, followed by the server jar
   * @throws UtilityMethodCompilationException if the metamodel API does not compile
   */
  public synchronized Entries forVsum(Collection<FileStorage> ecores) {
    List<FileStorage> files = ecores.stream().filter(file -> file.getData() != null).toList();
    List<Path> paths = new ArrayList<>();
    if (!files.isEmpty()) {
      paths.add(modelApi(files));
    }
    paths.add(reactionsServerJar());
    return new Entries(paths);
  }

  /**
   * Returns the reactions language server jar, extracted to a private temporary file on first use.
   *
   * @return the jar
   * @throws UncheckedIOException if the jar cannot be extracted
   */
  public synchronized Path reactionsServerJar() {
    if (serverJar == null) {
      Path target = workDir().resolve("reactions-ide.jar");
      try (InputStream in = serverJarResource.getInputStream()) {
        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
      } catch (IOException e) {
        throw new UncheckedIOException("The reactions language server jar cannot be read", e);
      }
      serverJar = target;
    }
    return serverJar;
  }

  /** Deletes all extracted and compiled files. */
  @PreDestroy
  public synchronized void cleanUp() {
    modelApis.clear();
    if (workDir != null) {
      deleteQuietly(workDir);
      workDir = null;
      serverJar = null;
    }
  }

  private Path modelApi(List<FileStorage> ecores) {
    String key = key(ecores);
    Path cached = modelApis.get(key);
    if (cached != null && Files.isDirectory(cached)) {
      return cached;
    }
    EcoreTypeIndex index =
        EcoreTypeIndex.of(
            ecores.stream()
                .map(file -> new EcoreTypeIndex.EcoreFile(file.getFilename(), file.getData()))
                .toList());
    Map<String, String> sources = ModelApiSourceGenerator.generate(index);
    Path outputDir = workDir().resolve("model-api-" + key.substring(0, 16));
    try {
      JavaSourceCompiler.Outcome outcome =
          JavaSourceCompiler.compile(sources, List.of(reactionsServerJar()), outputDir);
      if (!outcome.compiled()) {
        deleteQuietly(outputDir);
        throw new UtilityMethodCompilationException(
            "The Java API of the project's metamodels does not compile: "
                + String.join("; ", outcome.errors()));
      }
    } catch (IOException | IllegalStateException e) {
      deleteQuietly(outputDir);
      throw new UtilityMethodCompilationException(
          "The Java API of the project's metamodels cannot be compiled: " + e.getMessage(), e);
    }
    modelApis.put(key, outputDir);
    return outputDir;
  }

  private Path workDir() {
    if (workDir == null) {
      try {
        workDir =
            Files.createTempDirectory(
                "utility-methods-",
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
      } catch (UnsupportedOperationException e) {
        workDir = createTempDirectory();
      } catch (IOException e) {
        throw new UncheckedIOException("No temporary directory for compiling utility methods", e);
      }
    }
    return workDir;
  }

  private static Path createTempDirectory() {
    try {
      return Files.createTempDirectory("utility-methods-");
    } catch (IOException e) {
      throw new UncheckedIOException("No temporary directory for compiling utility methods", e);
    }
  }

  /** SHA-256 over the names and contents of the metamodel files, independent of their order. */
  private static String key(List<FileStorage> ecores) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      List<String> fileHashes = new ArrayList<>();
      for (FileStorage ecore : ecores) {
        MessageDigest fileDigest = MessageDigest.getInstance("SHA-256");
        fileDigest.update(String.valueOf(ecore.getFilename()).getBytes(StandardCharsets.UTF_8));
        fileDigest.update((byte) 0);
        fileDigest.update(ecore.getData());
        fileHashes.add(HexFormat.of().formatHex(fileDigest.digest()));
      }
      fileHashes.stream()
          .sorted()
          .forEach(hash -> digest.update(hash.getBytes(StandardCharsets.UTF_8)));
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }

  private static void deleteQuietly(Path path) {
    if (!Files.exists(path)) {
      return;
    }
    try (Stream<Path> walk = Files.walk(path)) {
      walk.sorted(Comparator.reverseOrder()).forEach(CompileClasspath::deleteFile);
    } catch (IOException e) {
      log.warn("Could not delete {}", path, e);
    }
  }

  private static void deleteFile(Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (IOException e) {
      log.warn("Could not delete {}", path, e);
    }
  }
}
