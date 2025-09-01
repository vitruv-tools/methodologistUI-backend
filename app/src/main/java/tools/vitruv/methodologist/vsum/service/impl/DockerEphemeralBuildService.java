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
      job = Files.createTempDirectory("mm-" + input.getMetaModelId() + "-");
      Path inDir = Files.createDirectories(job.resolve("input"));
      Path outDir = Files.createDirectories(job.resolve("output"));

      Path ecore = inDir.resolve("model.ecore");
      Path gen = inDir.resolve("model.genmodel");
      Files.write(ecore, input.getEcoreBytes());
      Files.write(gen, input.getGenModelBytes());

      List<String> cmd =
          List.of(
              "java",
              "-jar",
              jarPath,
              "--ecore",
              ecore.toAbsolutePath().toString(),
              "--genmodel",
              gen.toAbsolutePath().toString(),
              "--out",
              outDir.toAbsolutePath().toString());
      Process p = startProcess(cmd);

      boolean finished = p.waitFor(timeoutSeconds, TimeUnit.SECONDS);
      if (!finished) {
        p.destroyForcibly();
        return BuildResult.builder()
            .success(false)
            .errors(1)
            .warnings(0)
            .report("Timeout after " + timeoutSeconds + "s")
            .discoveredNsUris(null)
            .build();
      }
      String console = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

      Path resultJson = outDir.resolve("result.json");
      if (Files.exists(resultJson)) {
        JsonNode dto = OM.readTree(resultJson.toFile());
        boolean ok = dto.path("success").asBoolean(false);
        int errors = dto.path("errors").asInt(0);
        int warnings = dto.path("warnings").asInt(0);
        String report = dto.path("report").asText(console);

        List<String> nsList = null;
        if (dto.has("nsUris") && dto.get("nsUris").isArray()) {
          nsList =
              OM.convertValue(
                  dto.get("nsUris"),
                  OM.getTypeFactory().constructCollectionType(List.class, String.class));
        }
        String ns = (nsList == null || nsList.isEmpty()) ? null : String.join(", ", nsList);

        return BuildResult.builder()
            .success(ok)
            .errors(errors)
            .warnings(warnings)
            .report(report)
            .discoveredNsUris(ns)
            .build();
      }

      int exit = p.exitValue();
      return BuildResult.builder()
          .success(exit == 0)
          .errors(exit == 0 ? 0 : 1)
          .warnings(0)
          .report(console.isBlank() ? ("Process exit " + exit) : console)
          .discoveredNsUris(null)
          .build();

    } catch (Exception e) {
      return BuildResult.builder()
          .success(false)
          .errors(1)
          .warnings(0)
          .report("Crash: " + e.getMessage())
          .discoveredNsUris(null)
          .build();
    } finally {
      if (job != null) {
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
