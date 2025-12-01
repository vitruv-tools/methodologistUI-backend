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
   * Executes Vitruv-CLI for the given project folder, metamodels and reaction file.
   *
   * @param projectFolder the folder passed to {@code -f/--folder}
   * @param metamodels a list of metamodel inputs (ecore + genmodel pairs)
   * @param reactionFile the reaction file passed to {@code -r/--reation}
   * @return the result of the CLI execution
   */
  public VitruvCliResult run(
      Path projectFolder, List<MetamodelInput> metamodels, Path reactionFile) {

    if (metamodels == null || metamodels.isEmpty()) {
      throw new IllegalArgumentException("At least one metamodel must be provided");
    }

    String metamodelArg =
        metamodels.stream()
            .map(mm -> mm.getEcorePath() + "," + mm.getGenmodelPath())
            .collect(Collectors.joining(";"));

    List<String> command =
        List.of(
            properties.getBinary(),
            "-jar",
            properties.getJar(),
            "--folder",
            projectFolder.toString(),
            "--metamodel",
            metamodelArg,
            "--reaction",
            reactionFile.toString(),
            "--userinteractor",
            "default");

    log.info("Running Vitruv-CLI with command: {}", String.join(" ", command));

    try {
      Path workDir = Path.of(properties.getWorkingDir());
      Files.createDirectories(workDir);

      ProcessBuilder pb = new ProcessBuilder(command);
      pb.directory(workDir.toFile());
      pb.redirectErrorStream(false);

      Process process = pb.start();

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
      return exitCode == 0 && (stderr == null || stderr.isBlank());
    }
  }
}
