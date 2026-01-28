package tools.vitruv.methodologist.vsum.service;

import static tools.vitruv.methodologist.messages.Error.FAT_JAR_NOT_FOUND_ERROR;
import static tools.vitruv.methodologist.messages.Error.METAMODEL_PAIR_COUNT_MISMATCH_ERROR;
import static tools.vitruv.methodologist.messages.Error.METAMODEL_PAIR_REQUIRED_ERROR;
import static tools.vitruv.methodologist.messages.Error.REACTION_FILE_REQUIRED_ERROR;
import static tools.vitruv.methodologist.messages.Error.VITRUV_CLI_ERROR;
import static tools.vitruv.methodologist.messages.Error.VITRUV_CLI_EXECUTION_FAILED_ERROR;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.vitruv.methodologist.exception.VsumBuildingException;
import tools.vitruv.methodologist.general.model.FileStorage;
import tools.vitruv.methodologist.vitruvcli.VitruvCliProperties;
import tools.vitruv.methodologist.vitruvcli.VitruvCliService;

/**
 * Service that prepares metamodel inputs and runs the Vitruv CLI to produce a fat JAR.
 *
 * <p>This service writes provided Ecore/GenModel and reaction files into a temporary job directory
 * under the configured working directory, concatenates reaction files, invokes the Vitruv CLI,
 * reads the produced fat JAR and attempts to remove the job directory. IO errors and CLI failures
 * are wrapped in {@link VsumBuildingException}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetaModelVitruvIntegrationService {

  private static final String FAT_JAR_RELATIVE_PATH =
      "vsum/target/"
          + "tools.vitruv.methodologisttemplate."
          + "vsum-0.1.0-SNAPSHOT-jar-with-dependencies.jar";
  private final VitruvCliService vitruvCliService;
  private final VitruvCliProperties vitruvCliProperties;

  /**
   * Returns the given byte array or an empty byte array if {@code data} is null.
   *
   * @param data the input byte array, may be {@code null}
   * @return a non-null byte array (empty if input was {@code null})
   */
  private static byte[] nonNullBytes(byte[] data) {
    return data == null ? new byte[0] : data;
  }

  /**
   * Produces a filesystem-safe name by replacing path separators with underscores. If the provided
   * name is null or blank, returns the provided fallback.
   *
   * @param name the original filename or identifier
   * @param fallback fallback name used when {@code name} is null or blank
   * @return a sanitized filename suitable for use within a job directory
   */
  private static String safeName(String name, String fallback) {
    if (name == null || name.isBlank()) {
      return fallback;
    }
    return name.replace("\\", "/").replace("/", "_");
  }

  /**
   * Runs the Vitruv CLI to build a fat JAR for the given metamodel inputs and reaction files. The
   * method creates a temporary job directory under the configured working directory, writes
   * provided files, invokes the CLI, reads the produced fat JAR bytes, and then attempts to remove
   * the job directory.
   *
   * <p>The method wraps IO errors and CLI failures into {@link VsumBuildingException}.
   *
   * @param ecoreFiles the list of Ecore file storage objects (one per metamodel)
   * @param genModelFiles the list of corresponding GenModel file storage objects
   * @param reactionFiles the list of reaction files to include
   * @return the bytes of the generated fat JAR
   * @throws VsumBuildingException if inputs are invalid, the CLI fails, or IO errors occur
   */
  public byte[] runVitruvAndGetFatJarBytes(
      List<FileStorage> ecoreFiles,
      List<FileStorage> genModelFiles,
      List<FileStorage> reactionFiles) {

    validateInputs(ecoreFiles, genModelFiles, reactionFiles);

    Path jobDir = null;
    try {
      Path baseWorkDir = Path.of(requireWorkingDir());
      Files.createDirectories(baseWorkDir);

      jobDir = baseWorkDir.resolve("job-" + System.currentTimeMillis());
      Files.createDirectories(jobDir);

      List<VitruvCliService.MetamodelInput> metamodels =
          writeMetamodels(jobDir, ecoreFiles, genModelFiles);

      Path reactionsDir = jobDir.resolve("reactions");
      Files.createDirectories(reactionsDir);
      writeReactionFiles(reactionsDir, reactionFiles);

      VitruvCliService.VitruvCliResult result =
          vitruvCliService.run(jobDir, metamodels, reactionsDir);

      if (!result.isSuccess()) {
        String msg =
            (result.getStderr() == null || result.getStderr().isBlank())
                ? result.getStdout()
                : result.getStderr();
        throw new VsumBuildingException(VITRUV_CLI_ERROR + msg);
      }

      Path jarPath = jobDir.resolve(FAT_JAR_RELATIVE_PATH);
      if (!Files.exists(jarPath)) {
        throw new VsumBuildingException(FAT_JAR_NOT_FOUND_ERROR + jarPath);
      }

      return Files.readAllBytes(jarPath);
    } catch (IOException e) {
      throw new VsumBuildingException(VITRUV_CLI_EXECUTION_FAILED_ERROR + e.getMessage());
    } finally {
      if (jobDir != null) {
        try {
          deleteRecursively(jobDir);
        } catch (IOException e) {
          log.warn("Failed to delete job directory: {}", jobDir, e);
        }
      }
    }
  }

  /**
   * Validates method inputs for {@link #runVitruvAndGetFatJarBytes(List, List, List)}.
   *
   * @param ecoreFiles list of Ecore files (must not be null or empty)
   * @param genModelFiles list of GenModel files (must match size of ecoreFiles)
   * @param reactionFiles list of reaction files (must not be null or empty)
   * @throws VsumBuildingException when validation fails
   */
  private void validateInputs(
      List<FileStorage> ecoreFiles,
      List<FileStorage> genModelFiles,
      List<FileStorage> reactionFiles) {

    if (ecoreFiles == null
        || genModelFiles == null
        || ecoreFiles.isEmpty()
        || genModelFiles.isEmpty()) {
      throw new VsumBuildingException(METAMODEL_PAIR_REQUIRED_ERROR);
    }
    if (ecoreFiles.size() != genModelFiles.size()) {
      throw new VsumBuildingException(METAMODEL_PAIR_COUNT_MISMATCH_ERROR);
    }
    if (reactionFiles == null || reactionFiles.isEmpty()) {
      throw new VsumBuildingException(REACTION_FILE_REQUIRED_ERROR);
    }
  }

  /**
   * Returns the configured working directory for Vitruv CLI jobs.
   *
   * @return the configured working directory path string
   * @throws VsumBuildingException if the property is not configured
   */
  private String requireWorkingDir() {
    String wd = vitruvCliProperties.getWorkingDir();
    if (wd == null || wd.isBlank()) {
      throw new VsumBuildingException("vitruv.cli.working-dir is not configured");
    }
    return wd;
  }

  /**
   * Recursively deletes a directory and its contents.
   *
   * @param dir the directory to delete
   * @throws IOException when file system operations fail
   */
  private void deleteRecursively(Path dir) throws IOException {
    if (dir == null || !Files.exists(dir)) {
      return;
    }
    try (var walk = Files.walk(dir)) {
      walk.sorted(Comparator.reverseOrder())
          .forEach(
              path -> {
                try {
                  Files.deleteIfExists(path);
                } catch (IOException e) {
                  throw new UncheckedIOException(e);
                }
              });
    } catch (UncheckedIOException uio) {
      throw uio.getCause();
    }
  }

  /**
   * Writes Ecore and GenModel files into the given job directory and returns inputs for the CLI.
   *
   * @param jobDir the directory where files will be written
   * @param ecoreFiles Ecore file storage entries
   * @param genModelFiles GenModel file storage entries
   * @return list of MetamodelInput records with paths to the written files
   * @throws IOException when file IO fails
   */
  private List<VitruvCliService.MetamodelInput> writeMetamodels(
      Path jobDir, List<FileStorage> ecoreFiles, List<FileStorage> genModelFiles)
      throws IOException {

    List<VitruvCliService.MetamodelInput> inputs = new ArrayList<>(ecoreFiles.size());

    for (int i = 0; i < ecoreFiles.size(); i++) {
      FileStorage ecore = ecoreFiles.get(i);
      FileStorage gen = genModelFiles.get(i);

      String ecoreName = safeName(ecore.getFilename(), "model-" + i + ".ecore");
      String genName = safeName(gen.getFilename(), "model-" + i + ".genmodel");

      Path ecorePath = jobDir.resolve(ecoreName);
      Path genPath = jobDir.resolve(genName);

      Files.write(ecorePath, nonNullBytes(ecore.getData()));
      Files.write(genPath, nonNullBytes(gen.getData()));

      inputs.add(
          VitruvCliService.MetamodelInput.builder()
              .ecorePath(ecorePath)
              .genmodelPath(genPath)
              .build());
    }

    return inputs;
  }

  /**
   * Writes the provided reaction files into the given directory.
   *
   * <p>Each entry from {@code reactionFiles} is written to {@code reactionsDir} using a
   * filesystem-safe name (the implementation falls back to {@code "reactions-<i>.reactions"} when a
   * filename is missing). Null file data is treated as an empty byte array.
   *
   * <p>IO errors encountered while writing are propagated as an unchecked {@link RuntimeException}.
   *
   * @param reactionsDir the directory where reaction files will be written; must not be {@code
   *     null}
   * @param reactionFiles the list of reaction {@code FileStorage} objects to write; must not be
   *     {@code null}
   * @throws RuntimeException if an I/O error occurs while writing any reaction file
   */
  private void writeReactionFiles(Path reactionsDir, List<FileStorage> reactionFiles)
      throws IOException {

    for (int i = 0; i < reactionFiles.size(); i++) {
      FileStorage rf = reactionFiles.get(i);
      String name = safeName(rf.getFilename(), "reactions-" + i + ".reactions");
      Path p = reactionsDir.resolve(name);
      Files.write(p, nonNullBytes(rf.getData()));
    }
  }
}
