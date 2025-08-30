package tools.vitruv.methodologist.vsum.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
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
      job = Files.createTempDirectory("mm-" + in.getMetaModelId() + "-");
      Path inDir = Files.createDirectories(job.resolve("input"));

      // write inputs
      Path ecore = inDir.resolve("model.ecore");
      Path gen = inDir.resolve("model.genmodel");
      Files.write(ecore, in.getEcoreBytes());
      Files.write(gen, in.getGenModelBytes());

      // copy builder jar into job dir
      Path jarCopy = job.resolve("methodologist-build.jar");
      Files.copy(Paths.get(jarPath), jarCopy, StandardCopyOption.REPLACE_EXISTING);

      List<String> cmd =
          List.of(
              "docker",
              "run",
              "--rm",
              "--read-only",
              "--network",
              "none",
              "--cpus",
              "1",
              "--memory",
              "1g",
              "--cap-drop",
              "ALL",
              "--security-opt",
              "no-new-privileges",
              "--pids-limit",
              "256",
              "--tmpfs",
              "/tmp:rw,noexec,nosuid,size=256m",
              "-v",
              job.toAbsolutePath() + ":/work:rw",
              image,
              "java",
              "-jar",
              "/work/methodologist-build.jar",
              "--ecore",
              "/work/input/model.ecore",
              "--genmodel",
              "/work/input/model.genmodel",
              "--out",
              "/work/output",
              "--run-mwe2",
              String.valueOf(in.isRunMwe2()));

      Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
      String console = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      if (!p.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
        p.destroyForcibly();
        return BuildResult.builder()
            .success(false)
            .errors(1)
            .warnings(0)
            .report("Timeout")
            .discoveredNsUris(null)
            .build();
      }

      Path outDir = Files.createDirectories(job.resolve("output"));
      Path resultJson = outDir.resolve("result.json");
      if (Files.exists(resultJson)) {
        JsonNode dto = OM.readTree(resultJson.toFile());
        boolean ok = dto.path("success").asBoolean(false);
        int err = dto.path("errors").asInt(0);
        int warn = dto.path("warnings").asInt(0);
        String rep = dto.path("report").asText("");

        String ns = null;
        if (dto.has("nsUris") && dto.get("nsUris").isArray()) {
          List<String> list =
              OM.convertValue(dto.get("nsUris"), new TypeReference<List<String>>() {});
          if (list != null && !list.isEmpty()) {
            ns = String.join(", ", list);
          }
        }

        return BuildResult.builder()
            .success(ok)
            .errors(err)
            .warnings(warn)
            .report(rep)
            .discoveredNsUris(ns)
            .build();
      }

      int exit = p.exitValue();
      return BuildResult.builder()
          .success(exit == 0)
          .errors(exit == 0 ? 0 : 1)
          .warnings(0)
          .report(console)
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
                    } catch (Exception e) {
                      log.error(e.getMessage(), e);
                    }
                  });
        } catch (Exception e) {
          log.error(e.getMessage(), e);
        }
      }
    }
  }
}
