package tools.vitruv.methodologist.vsum.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.vitruv.methodologist.vsum.service.MetamodelBuildService;

/**
 * Executes the metamodel builder JAR in an isolated ephemeral job. Creates a temp directory, writes
 * model files, runs the process, and parses result.json into a structured build result.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DockerEphemeralBuildService implements MetamodelBuildService {

  private static final ObjectMapper OM = new ObjectMapper();

  /** Base Docker image to use for the process (defaults to Eclipse Temurin JRE). */
  @Value("${builder.baseImage:eclipse-temurin:21-jre}")
  protected String image;

  /** Path to the builder JAR file on the host. */
  @Value("${builder.jarPath}")
  protected String jarPath;

  /** Maximum time to wait for the process before failing. */
  @Value("${builder.timeoutSeconds:300}")
  protected int timeoutSeconds;

  /**
   * Runs the builder JAR on the given metamodel input, writes files to a temp job dir, waits for
   * the process, parses the JSON output, and maps it into a BuildResult. Cleans up all temporary
   * files after completion.
   */
  @Override
  public BuildResult buildAndValidate(MetamodelBuildInput input) {
    Path job = null;
    try {
      job = createJobDirectory(input);
      Path inDir = createInputDirectory(job);
      Path outDir = createOutputDirectory(job);
      Path ecore = inDir.resolve("model.ecore");
      Path gen = inDir.resolve("model.genmodel");
      writeInputFiles(ecore, gen, input);
      List<String> cmd = buildCommand(ecore, gen, outDir);
      Process process = startProcess(cmd);
      return evaluateProcessResult(process, outDir);
    } catch (Exception e) {
      return buildCrashResult(e);
    } finally {
      cleanupJobDirectory(job);
    }
  }

  private Path createJobDirectory(MetamodelBuildInput input) throws IOException {
    return Files.createTempDirectory("mm-" + input.getMetaModelId() + "-");
  }

  private Path createInputDirectory(Path job) throws IOException {
    return Files.createDirectories(job.resolve("input"));
  }

  private Path createOutputDirectory(Path job) throws IOException {
    return Files.createDirectories(job.resolve("output"));
  }

  private void writeInputFiles(Path ecore, Path gen, MetamodelBuildInput input) throws IOException {
    Files.write(ecore, input.getEcoreBytes());
    Files.write(gen, input.getGenModelBytes());
  }

  private List<String> buildCommand(Path ecore, Path gen, Path outDir) {
    return List.of(
        "java",
        "-jar",
        jarPath,
        "--ecore",
        ecore.toAbsolutePath().toString(),
        "--genmodel",
        gen.toAbsolutePath().toString(),
        "--out",
        outDir.toAbsolutePath().toString());
  }

  private BuildResult evaluateProcessResult(Process process, Path outDir)
      throws IOException, InterruptedException {
    boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
    if (!finished) {
      return handleTimeout(process);
    }
    String console = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    Path resultJson = outDir.resolve("result.json");
    if (Files.exists(resultJson)) {
      return buildResultFromJson(resultJson, console);
    }
    return buildResultFromExitCode(process.exitValue(), console);
  }

  private BuildResult handleTimeout(Process process) {
    process.destroyForcibly();
    return BuildResult.builder()
        .success(false)
        .errors(1)
        .warnings(0)
        .report("Timeout after " + timeoutSeconds + "s")
        .discoveredNsUris(null)
        .build();
  }

  private BuildResult buildResultFromJson(Path resultJson, String console) throws IOException {
    JsonNode dto = OM.readTree(resultJson.toFile());
    boolean ok = dto.path("success").asBoolean(false);
    int errors = dto.path("errors").asInt(0);
    int warnings = dto.path("warnings").asInt(0);
    String report = dto.path("report").asText(console);
    String ns = extractNsUris(dto);
    return BuildResult.builder()
        .success(ok)
        .errors(errors)
        .warnings(warnings)
        .report(report)
        .discoveredNsUris(ns)
        .build();
  }

  private String extractNsUris(JsonNode dto) {
    JsonNode nsNode = dto.get("nsUris");
    if (nsNode == null || !nsNode.isArray() || nsNode.isEmpty()) {
      return null;
    }
    List<String> nsList =
        OM.convertValue(
            nsNode, OM.getTypeFactory().constructCollectionType(List.class, String.class));
    if (nsList == null || nsList.isEmpty()) {
      return null;
    }
    return String.join(", ", nsList);
  }

  private BuildResult buildResultFromExitCode(int exitCode, String console) {
    boolean success = exitCode == 0;
    int errors = success ? 0 : 1;
    String report = console.isBlank() ? "Process exit " + exitCode : console;
    return BuildResult.builder()
        .success(success)
        .errors(errors)
        .warnings(0)
        .report(report)
        .discoveredNsUris(null)
        .build();
  }

  private BuildResult buildCrashResult(Exception e) {
    return BuildResult.builder()
        .success(false)
        .errors(1)
        .warnings(0)
        .report("Crash: " + e.getMessage())
        .discoveredNsUris(null)
        .build();
  }

  private void cleanupJobDirectory(Path job) {
    if (job == null) {
      return;
    }
    try {
      Files.walk(job)
          .sorted(Comparator.reverseOrder())
          .forEach(
              p -> {
                try {
                  Files.deleteIfExists(p);
                } catch (IOException e) {
                  log.error(e.getMessage(), e);
                }
              });
    } catch (IOException e) {
      log.error(e.getMessage(), e);
    }
  }

  /**
   * Starts an external process using the given command list. Configures the process to merge
   * standard error into standard output. Returns the running Process handle for further
   * interaction.
   */
  protected Process startProcess(List<String> cmd) throws IOException {
    return new ProcessBuilder(cmd).redirectErrorStream(true).start();
  }
}
