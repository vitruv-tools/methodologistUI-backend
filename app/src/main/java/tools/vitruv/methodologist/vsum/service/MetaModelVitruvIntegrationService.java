package tools.vitruv.methodologist.vsum.service;

import static tools.vitruv.methodologist.messages.Error.FAT_JAR_NOT_FOUND_ERROR;
import static tools.vitruv.methodologist.messages.Error.METAMODEL_PAIR_COUNT_MISMATCH_ERROR;
import static tools.vitruv.methodologist.messages.Error.METAMODEL_PAIR_REQUIRED_ERROR;
import static tools.vitruv.methodologist.messages.Error.REACTION_FILE_REQUIRED_ERROR;
import static tools.vitruv.methodologist.messages.Error.VITRUV_CLI_ERROR;
import static tools.vitruv.methodologist.messages.Error.VITRUV_CLI_EXECUTION_FAILED_ERROR;

import freemarker.template.TemplateException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import tools.vitruv.methodologist.exception.CLIExecuteException;
import tools.vitruv.methodologist.exception.VsumBuildingException;
import tools.vitruv.methodologist.general.FileEnumType;
import tools.vitruv.methodologist.general.model.FileStorage;
import tools.vitruv.methodologist.vitruvcli.GenModelPrecheckStatus;
import tools.vitruv.methodologist.vitruvcli.VitruvCliProperties;
import tools.vitruv.methodologist.vitruvcli.VitruvCliService;
import tools.vitruv.methodologist.vsum.lowcode.reactions.template.dto.request.CompositeReactionsRequest;
import tools.vitruv.methodologist.vsum.lowcode.reactions.template.service.LowCodeReactionService;
import tools.vitruv.methodologist.vsum.model.FineGranularMetaModelRelation;
import tools.vitruv.methodologist.vsum.model.MetaModelRelation;
import tools.vitruv.methodologist.vsum.reaction.ReactionParserUtil;

/** Service that prepares metamodel inputs and runs the Vitruv CLI to produce a fat JAR. */
@Slf4j
@Service
public class MetaModelVitruvIntegrationService {
  private static final String FAT_JAR_RELATIVE_PATH =
      "vsum/target/"
          + "tools.vitruv.methodologisttemplate."
          + "vsum-0.1.0-SNAPSHOT-jar-with-dependencies.jar";

  private final VitruvCliService vitruvCliService;
  private final VitruvCliProperties vitruvCliProperties;
  private final LowCodeReactionService lowCodeReactionService;

  /**
   * Creates a new service instance for Vitruv CLI integration.
   *
   * @param lowCodeReactionService service used to build composite reaction files
   * @param vitruvCliService service used to invoke the external CLI
   * @param vitruvCliProperties configuration used for working directory and execution settings
   */
  public MetaModelVitruvIntegrationService(
      LowCodeReactionService lowCodeReactionService,
      VitruvCliService vitruvCliService,
      VitruvCliProperties vitruvCliProperties) {
    this.lowCodeReactionService = lowCodeReactionService;
    this.vitruvCliService = vitruvCliService;
    this.vitruvCliProperties = vitruvCliProperties;
  }

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

    validateMetamodelPairs(ecoreFiles, genModelFiles);
    validateReactionFiles(reactionFiles);

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
   * Runs Vitruv CLI in GenModel precheck mode for the provided metamodel pairs.
   *
   * @param ecoreFiles Ecore file storage entries
   * @param genModelFiles matching GenModel file storage entries
   * @param applyChanges whether CLI should apply detected fixes automatically
   * @return the precheck execution result, including updated GenModel bytes when fixes were applied
   */
  public GenModelPrecheckExecutionResult precheckGenModels(
      List<FileStorage> ecoreFiles, List<FileStorage> genModelFiles, boolean applyChanges) {

    validateMetamodelPairs(ecoreFiles, genModelFiles);

    Path jobDir = null;
    try {
      Path baseWorkDir = Path.of(requireWorkingDir());
      Files.createDirectories(baseWorkDir);

      jobDir = Files.createTempDirectory(baseWorkDir, "precheck-");

      List<VitruvCliService.MetamodelInput> metamodels =
          writeMetamodels(jobDir, ecoreFiles, genModelFiles);

      VitruvCliService.GenModelPrecheckResult result =
          vitruvCliService.precheckGenmodels(jobDir, metamodels, applyChanges);

      List<byte[]> updatedGenModelBytes = List.of();
      if (result.getStatus() == GenModelPrecheckStatus.FIXES_APPLIED) {
        updatedGenModelBytes =
            metamodels.stream()
                .map(VitruvCliService.MetamodelInput::getGenmodelPath)
                .map(
                    path -> {
                      try {
                        return Files.readAllBytes(path);
                      } catch (IOException e) {
                        throw new UncheckedIOException(e);
                      }
                    })
                .toList();
      }

      return GenModelPrecheckExecutionResult.builder()
          .exitCode(result.getExitCode())
          .stdout(result.getStdout())
          .stderr(result.getStderr())
          .status(result.getStatus())
          .updatedGenModelBytes(updatedGenModelBytes)
          .build();
    } catch (UncheckedIOException | IOException e) {
      log.error("GenModel precheck failed due to I/O error", e);
      String reason =
          "I/O error during GenModel precheck ("
              + e.getClass().getSimpleName()
              + "): "
              + (e.getMessage() != null ? e.getMessage() : "no detailed message available");
      throw new CLIExecuteException(reason);
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
   * @throws VsumBuildingException when validation fails
   */
  private void validateMetamodelPairs(
      List<FileStorage> ecoreFiles, List<FileStorage> genModelFiles) {
    if (ecoreFiles == null
        || genModelFiles == null
        || ecoreFiles.isEmpty()
        || genModelFiles.isEmpty()) {
      throw new VsumBuildingException(METAMODEL_PAIR_REQUIRED_ERROR);
    }
    if (ecoreFiles.size() != genModelFiles.size()) {
      throw new VsumBuildingException(METAMODEL_PAIR_COUNT_MISMATCH_ERROR);
    }
  }

  /**
   * Validates that at least one reaction file is present for a build run.
   *
   * @param reactionFiles reaction files to validate
   */
  private void validateReactionFiles(List<FileStorage> reactionFiles) {
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

  /**
   * Gets the build parameters for the given relation.
   *
   * @param relation the meta-model relation
   * @return the build parameters
   */
  public @NonNull BuildParameters getBuildParameters(MetaModelRelation relation) {
    ArrayList<FileStorage> additionalReactionFiles =
        new ArrayList<>(
            relation.getFineGranularMetaModelRelationSet().stream()
                .map(FineGranularMetaModelRelation::getReactionFileStorage)
                .filter(Objects::nonNull)
                .toList());
    if (relation.getReactionFileStorage() != null) {
      additionalReactionFiles.add(relation.getReactionFileStorage());
    }
    FileStorage compositeReactionFile;
    if (additionalReactionFiles.isEmpty()) {
      return new BuildParameters(additionalReactionFiles, null);
    } else if (additionalReactionFiles.size() == 1) {
      compositeReactionFile = additionalReactionFiles.get(0);
      additionalReactionFiles.remove(0);
    } else {
      // TODO: this is a quick and dirty way to get the required information, but there is simply no
      // other way to get it without actually parsing the file.
      ReactionParserUtil.ReactionFileInfo reactionFileInfo =
          ReactionParserUtil.parse(
              new String(additionalReactionFiles.get(0).getData(), StandardCharsets.UTF_8));
      CompositeReactionsRequest compositeReactionsRequest = new CompositeReactionsRequest();
      compositeReactionsRequest.setRegenerate(true);
      compositeReactionsRequest.setModel1Uri(reactionFileInfo.modelUri1());
      compositeReactionsRequest.setModel2Uri(reactionFileInfo.modelUri2());
      compositeReactionsRequest.setModel1Alias(reactionFileInfo.modelAlias1());
      compositeReactionsRequest.setModel2Alias(reactionFileInfo.modelAlias2());
      compositeReactionsRequest.setReactionName("compositeReaction");
      var imports =
          additionalReactionFiles.stream()
              .map(
                  fileStorage -> {
                    var importReactionFileInfo =
                        ReactionParserUtil.parse(
                            new String(fileStorage.getData(), StandardCharsets.UTF_8));
                    if (!Objects.equals(
                        reactionFileInfo.modelAlias1(), importReactionFileInfo.modelAlias1())) {
                      throw new RuntimeException(
                          String.format(
                              "All reaction files must be between the same pair of model aliases. "
                                  + "Found source model alias %s in reaction %s, but "
                                  + "source model alias %s in reaction %s!",
                              reactionFileInfo.modelAlias1(),
                              reactionFileInfo.reactionName(),
                              importReactionFileInfo.modelAlias1(),
                              importReactionFileInfo.reactionName()));
                    }
                    if (!Objects.equals(
                        reactionFileInfo.modelAlias2(), importReactionFileInfo.modelAlias2())) {
                      throw new RuntimeException(
                          String.format(
                              "All reaction files must be between the same pair of model aliases. "
                                  + "Found target model alias %s in reaction %s, but target "
                                  + "model alias %s in reaction %s!",
                              reactionFileInfo.modelAlias2(),
                              reactionFileInfo.reactionName(),
                              importReactionFileInfo.modelAlias2(),
                              importReactionFileInfo.reactionName()));
                    }
                    if (!Objects.equals(
                        reactionFileInfo.modelUri1(), importReactionFileInfo.modelUri1())) {
                      throw new RuntimeException(
                          String.format(
                              "All reaction files must be between the same pair of model uris. "
                                  + "Found source model uri %s in reaction %s, but source "
                                  + "model uri %s in reaction %s!",
                              reactionFileInfo.modelUri1(),
                              reactionFileInfo.reactionName(),
                              importReactionFileInfo.modelUri1(),
                              importReactionFileInfo.reactionName()));
                    }
                    if (!Objects.equals(
                        reactionFileInfo.modelUri2(), importReactionFileInfo.modelUri2())) {
                      throw new RuntimeException(
                          String.format(
                              "All reaction files must be between the same pair of model uris. "
                                  + "Found target model uri %s in reaction %s, but target "
                                  + "model uri %s in reaction %s!",
                              reactionFileInfo.modelUri2(),
                              reactionFileInfo.reactionName(),
                              importReactionFileInfo.modelUri2(),
                              importReactionFileInfo.reactionName()));
                    }
                    return importReactionFileInfo.reactionName();
                  })
              .toList();
      List<String> duplicates =
          imports.stream().filter(e -> Collections.frequency(imports, e) > 1).distinct().toList();
      if (!duplicates.isEmpty()) {
        throw new RuntimeException(
            String.format("Reaction names must be unique. Found duplicates: %s", duplicates));
      }
      compositeReactionsRequest.setImports(imports.toArray(new String[0]));

      try {
        var compositeReactionContent =
            lowCodeReactionService.applyTemplate(compositeReactionsRequest);
        compositeReactionFile =
            FileStorage.builder()
                .data(compositeReactionContent.getBytes(StandardCharsets.UTF_8))
                .filename("compositeReaction.reactions")
                .type(FileEnumType.REACTION)
                .contentType("text/plain")
                .build();
      } catch (IOException | TemplateException e) {
        throw new RuntimeException(e);
      }
    }
    return new BuildParameters(additionalReactionFiles, compositeReactionFile);
  }

  /**
   * Record representing the build parameters.
   *
   * @param additionalReactionFiles list of additional reaction files
   * @param compositeReactionFile the composite reaction file
   */
  public record BuildParameters(
      ArrayList<FileStorage> additionalReactionFiles, FileStorage compositeReactionFile) {}

  /** Immutable precheck result returned by the GenModel validation flow. */
  @Value
  @Builder
  public static class GenModelPrecheckExecutionResult {
    int exitCode;
    String stdout;
    String stderr;
    GenModelPrecheckStatus status;
    @Builder.Default List<byte[]> updatedGenModelBytes = List.of();

    /**
     * Returns true when precheck exits successfully and ends in CLEAN or FIXES_APPLIED status.
     *
     * @return true if precheck is successful
     */
    public boolean isSuccess() {
      return exitCode == 0
          && (status == GenModelPrecheckStatus.CLEAN
              || status == GenModelPrecheckStatus.FIXES_APPLIED);
    }

    /**
     * Returns true when updated GenModel content should overwrite stored files.
     *
     * @return true if fixes were applied and updated bytes are available
     */
    public boolean shouldOverwriteGenModels() {
      return status == GenModelPrecheckStatus.FIXES_APPLIED && !updatedGenModelBytes.isEmpty();
    }
  }
}
