package tools.vitruv.methodologist.vitruvcli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.vitruv.methodologist.exception.CLIExecuteException;

/** Service for invoking Vitruv-CLI as an external process. */
@Slf4j
@Service
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class VitruvCliService {

  static final String GENMODEL_PRECHECK_STATUS_PREFIX = "GENMODEL_PRECHECK_STATUS:";
  private static final Pattern GENMODEL_PRECHECK_STATUS_PATTERN =
      Pattern.compile(
          "(?m)^" + Pattern.quote(GENMODEL_PRECHECK_STATUS_PREFIX) + "\\s*([A-Z_]+)\\s*$");

  VitruvCliProperties properties;

  /**
   * Invoke the external Vitruv-CLI process to build models.
   *
   * <p>Constructs the command line for Vitruv-CLI using the provided properties and the list of
   * metamodel inputs. The {@code -m} argument is a semicolon-separated list of {@code
   * ecore,genmodel} filename pairs. The {@code -r} argument is set from the provided {@code
   * reactionsDir} path's filename (the CLI is executed with {@code jobDir} as working directory).
   *
   * @param jobDir the working directory for the CLI invocation; created if missing
   * @param metamodels list of metamodel input pairs (ecore + genmodel); must not be {@code null}
   * @param reactionsDir path referencing the reactions file or directory (its filename is passed to
   *     the CLI)
   * @return a {@link VitruvCliResult} containing the process exit code and captured stdout/stderr
   * @throws CLIExecuteException on I/O or interruption errors while executing the CLI
   */
  public VitruvCliResult run(Path jobDir, List<MetamodelInput> metamodels, Path reactionsDir) {
    List<String> command =
        List.of(
            properties.getBinary(),
            "-jar",
            properties.getJar(),
            "-f",
            ".",
            "-m",
            buildMetamodelArg(metamodels),
            "-rs",
            reactionsDir.getFileName().toString(),
            "-u",
            "default");
    return execute(jobDir, command);
  }

  /**
   * Invoke the external Vitruv-CLI process in standalone GenModel precheck mode.
   *
   * @param jobDir the working directory containing the metamodel files
   * @param metamodels list of metamodel input pairs to validate
   * @param applyChanges whether detected fixes should be applied automatically
   * @return the CLI execution result enriched with the parsed precheck status marker
   */
  public GenModelPrecheckResult precheckGenmodels(
      Path jobDir, List<MetamodelInput> metamodels, boolean applyChanges) {
    List<String> command =
        new ArrayList<>(
            List.of(
                properties.getBinary(),
                "-jar",
                properties.getJar(),
                "-m",
                buildMetamodelArg(metamodels),
                "-pg"));
    if (applyChanges) {
      command.add("--apply");
    }

    VitruvCliResult result = execute(jobDir, command);
    return GenModelPrecheckResult.builder()
        .exitCode(result.getExitCode())
        .stdout(result.getStdout())
        .stderr(result.getStderr())
        .status(extractPrecheckStatus(result.getStdout()))
        .build();
  }

  private String buildMetamodelArg(List<MetamodelInput> metamodels) {
    return metamodels.stream()
        .map(
            metamodelInput ->
                metamodelInput.getEcorePath().getFileName()
                    + ","
                    + metamodelInput.getGenmodelPath().getFileName())
        .collect(Collectors.joining(";"));
  }

  private VitruvCliResult execute(Path jobDir, List<String> command) {
    log.info("Running Vitruv-CLI with command: {}", String.join(" ", command));

    try {
      Files.createDirectories(jobDir);

      ProcessBuilder processBuilder = new ProcessBuilder(command);
      processBuilder.directory(jobDir.toFile());
      processBuilder.redirectErrorStream(false);

      Process process = processBuilder.start();
      process.getOutputStream().close();

      var outFuture =
          java.util.concurrent.CompletableFuture.supplyAsync(
              () -> readStream(process.getInputStream()));
      var errFuture =
          java.util.concurrent.CompletableFuture.supplyAsync(
              () -> readStream(process.getErrorStream()));

      boolean finished = process.waitFor(properties.getTimeoutSeconds(), TimeUnit.SECONDS);
      if (!finished) {
        process.destroyForcibly();
        throw new IllegalStateException(
            "Vitruv-CLI timed out after " + properties.getTimeoutSeconds() + " seconds");
      }

      int exitCode = process.exitValue();
      String stdout = outFuture.join();
      String stderr = errFuture.join();

      log.info(
          "Vitruv-CLI finished with exitCode={}, stdout={}, stderr={}",
          exitCode,
          truncate(stdout),
          truncate(stderr));

      return VitruvCliResult.builder().exitCode(exitCode).stdout(stdout).stderr(stderr).build();

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new CLIExecuteException(e.getMessage());
    } catch (IOException e) {
      throw new CLIExecuteException(e.getMessage());
    }
  }

  private GenModelPrecheckStatus extractPrecheckStatus(String stdout) {
    if (stdout == null || stdout.isBlank()) {
      return GenModelPrecheckStatus.UNKNOWN;
    }

    Matcher matcher = GENMODEL_PRECHECK_STATUS_PATTERN.matcher(stdout);
    String lastStatus = null;
    while (matcher.find()) {
      lastStatus = matcher.group(1);
    }

    if (lastStatus == null) {
      return GenModelPrecheckStatus.UNKNOWN;
    }

    try {
      return GenModelPrecheckStatus.valueOf(lastStatus);
    } catch (IllegalArgumentException ex) {
      return GenModelPrecheckStatus.UNKNOWN;
    }
  }

  private String readStream(java.io.InputStream in) {
    try {
      return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
    } catch (IOException e) {
      return "FAILED_TO_READ_STREAM: " + e.getMessage();
    }
  }

  private String truncate(String s) {
    if (s == null) {
      return "";
    }
    return s.length() > 2000 ? s.substring(0, 2000) + "...(truncated)" : s;
  }

  /**
   * Immutable holder describing a metamodel input pair supplied to Vitruv-CLI.
   *
   * <p>Contains the filesystem paths for the required .ecore file and its corresponding .genmodel.
   * Instances are immutable and created via the Lombok-generated builder.
   */
  @Value
  @Builder
  public static class MetamodelInput {
    Path ecorePath;
    Path genmodelPath;
  }

  /**
   * Result of a GenModel precheck CLI execution.
   *
   * <p>Includes the parsed machine-readable status marker reported in stdout.
   */
  @Value
  @Builder
  public static class GenModelPrecheckResult {
    int exitCode;
    String stdout;
    String stderr;
    GenModelPrecheckStatus status;

    public boolean isSuccess() {
      return exitCode == 0
          && (status == GenModelPrecheckStatus.CLEAN
              || status == GenModelPrecheckStatus.FIXES_APPLIED);
    }
  }

  /**
   * Result of a Vitruv-CLI execution.
   *
   * <p>Holds the process exit code and captured standard output and error streams. Use {@link
   * #isSuccess()} to determine whether the invocation completed successfully.
   */
  @Value
  @Builder
  public static class VitruvCliResult {
    int exitCode;
    String stdout;
    String stderr;

    public boolean isSuccess() {
      return exitCode == 0;
    }
  }
}
