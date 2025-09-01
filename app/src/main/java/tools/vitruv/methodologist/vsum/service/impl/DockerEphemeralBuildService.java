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

@Service
@RequiredArgsConstructor
@Slf4j
public class DockerEphemeralBuildService implements MetamodelBuildService {

  private static final ObjectMapper OM = new ObjectMapper();

  @Value("${builder.baseImage:eclipse-temurin:21-jre}")
  private String image;

  @Value("${builder.jarPath}")
  private String jarPath; // host path to methodologist-build.jar

  @Value("${builder.timeoutSeconds:300}")
  private int timeoutSeconds;

  @Override
  public BuildResult buildAndValidate(MetamodelBuildInput in) {
    Path job = null;
    try {
      // 1) temp workspace
      job = Files.createTempDirectory("mm-" + in.getMetaModelId() + "-");
      Path inDir = Files.createDirectories(job.resolve("input"));
      Path outDir = Files.createDirectories(job.resolve("output"));

      // 2) write inputs from DB bytes
      Path ecore = inDir.resolve("model.ecore");
      Path gen = inDir.resolve("model.genmodel");
      Files.write(ecore, in.getEcoreBytes());
      Files.write(gen, in.getGenModelBytes());

      // 3) run the jar
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
      Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();

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

      // 4) parse result.json if present
      Path resultJson = outDir.resolve("result.json");
      if (Files.exists(resultJson)) {
        JsonNode dto = OM.readTree(resultJson.toFile());
        boolean ok = dto.path("success").asBoolean(false);
        int errors = dto.path("errors").asInt(0);
        int warnings = dto.path("warnings").asInt(0);
        String report = dto.path("report").asText(console);

        String ns =
            dto.has("nsUris") && dto.get("nsUris").isArray()
                ? String.join(
                    (CharSequence) ", ",
                    (Iterable<? extends CharSequence>) OM.convertValue(
                        dto.get("nsUris"),
                        OM.getTypeFactory()
                            .constructCollectionType(List.class, String.class)))
                : null;

        return BuildResult.builder()
            .success(ok)
            .errors(errors)
            .warnings(warnings)
            .report(report)
            .discoveredNsUris(ns)
            .build();
      }

      // fallback: use exit code + console
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
      // 5) cleanup
      if (job != null) {
        try {
          Files.walk(job)
              .sorted(Comparator.reverseOrder())
              .forEach(
                  p -> {
                    try {
                      Files.deleteIfExists(p);
                    } catch (IOException ignored) {
                    }
                  });
        } catch (IOException ignored) {
        }
      }
    }
  }
}
