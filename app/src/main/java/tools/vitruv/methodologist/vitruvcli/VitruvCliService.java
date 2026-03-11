package tools.vitruv.methodologist.vitruvcli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.*;
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

    try {
      Path workDir = Path.of(properties.getWorkingDir());
      if (!workDir.isAbsolute() && properties.isSystemTempDir()) {
        workDir = Path.of(System.getProperty("java.io.tmpdir"), workDir.toString());
      }
      Files.createDirectories(workDir);

      // Relative jar paths should be relative to this program rather than the temp working dir of the subprocess
      String jar = Path.of(properties.getJar()).toAbsolutePath().toString();

      List<String> command =
              List.of(
                      properties.getBinary(),
                      "-jar",
                      jar,
                      "--folder",
                      projectFolder.toString(),
                      "--metamodel",
                      metamodelArg,
                      "--reactions",
                      reactionFile.getParent().toString(),
                      "--userinteractor",
                      "default");

      log.info("Running Vitruv-CLI with command: {}", String.join(" ", command));

      ProcessBuilder processBuilder = new ProcessBuilder(command);
      processBuilder.directory(workDir.toFile());

      Process process = processBuilder.start();
      ExecutorService exec = Executors.newFixedThreadPool(2);
      Future<String> out = exec.submit(() -> readStream(process.getInputStream()));
      Future<String> err = exec.submit(() -> readStream(process.getErrorStream()));
      boolean finished = process.waitFor(properties.getTimeoutSeconds(), TimeUnit.SECONDS);

      if (!finished) {
        process.destroyForcibly();
        throw new IllegalStateException(
            "Vitruv-CLI timed out after " + properties.getTimeoutSeconds() + " seconds");
      }

      int exitCode = process.exitValue();
      String stdout = out.get();
      String stderr = err.get();

      log.info(
          "Vitruv-CLI finished with exitCode={}, stdout={}, stderr={}",
          exitCode,
          truncate(stdout),
          truncate(stderr));

      return VitruvCliResult.builder().exitCode(exitCode).stdout(stdout).stderr(stderr).build();
    } catch (IOException | InterruptedException | ExecutionException e) {
      Thread.currentThread().interrupt();
      throw new CLIExecuteException(e.getMessage());
    }
  }

  private static String readStream(InputStream stream) throws IOException {
    StringBuilder sb = new StringBuilder();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        sb.append(line).append(System.lineSeparator());
      }
    }
    return sb.toString();
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
