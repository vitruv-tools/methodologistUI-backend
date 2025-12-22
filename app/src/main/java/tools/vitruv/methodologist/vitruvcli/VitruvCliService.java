package tools.vitruv.methodologist.vitruvcli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
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
    String metamodelArg =
        metamodels.stream()
            .map(
                mm ->
                    mm.getEcorePath().getFileName().toString()
                        + ","
                        + mm.getGenmodelPath().getFileName().toString())
            .collect(Collectors.joining(";"));

    List<String> command =
        List.of(
            properties.getBinary(),
            "-jar",
            properties.getJar(),
            "-f",
            ".",
            "-m",
            metamodelArg,
            "-rs",
            reactionsDir.getFileName().toString(),
            "-u",
            "default");

    log.info("Running Vitruv-CLI with command: {}", String.join(" ", command));

    try {
      Files.createDirectories(jobDir);

      ProcessBuilder processBuilder = new ProcessBuilder(command);
      processBuilder.directory(jobDir.toFile());
      processBuilder.redirectErrorStream(false);

      Process process = processBuilder.start();
      boolean finished = process.waitFor(properties.getTimeoutSeconds(), TimeUnit.SECONDS);

      if (!finished) {
        process.destroyForcibly();
        throw new IllegalStateException(
            "Vitruv-CLI timed out after " + properties.getTimeoutSeconds() + " seconds");
      }

      int exitCode = process.exitValue();
      String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);

      log.info(
          "Vitruv-CLI finished with exitCode={}, stdout={}, stderr={}",
          exitCode,
          truncate(stdout),
          truncate(stderr));

      return VitruvCliResult.builder().exitCode(exitCode).stdout(stdout).stderr(stderr).build();

    } catch (IOException | InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new CLIExecuteException(e.getMessage());
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
