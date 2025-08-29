package tools.vitruv.methodologist.vsum.service.imp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.vitruv.methodologist.vsum.service.MetamodelBuildService;

@Service
@RequiredArgsConstructor
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
      Path outDir = Files.createDirectories(job.resolve("output"));

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

      Path resultJson = outDir.resolve("result.json");
      if (Files.exists(resultJson)) {
        JsonNode dto = OM.readTree(resultJson.toFile());
        boolean ok = dto.path("success").asBoolean(false);
        int err = dto.path("errors").asInt(0);
        int warn = dto.path("warnings").asInt(0);
        String rep = dto.path("report").asText("");
        String ns =
            dto.has("nsUris") && dto.get("nsUris").isArray()
                ? String.join(
                    ", ",
                    (CharSequence)
                        OM.convertValue(
                            dto.get("nsUris"),
                            OM.getTypeFactory().constructCollectionType(List.class, String.class)))
                : null;
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
      if (job != null)
        try {
          Files.walk(job)
              .sorted(Comparator.reverseOrder())
              .forEach(
                  path -> {
                    try {
                      Files.deleteIfExists(path);
                    } catch (Exception ignored) {
                    }
                  });
        } catch (Exception ignored) {
        }
    }
  }
}
