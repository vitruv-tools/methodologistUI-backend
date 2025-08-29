package tools.vitruv.methodologist.vsum.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DockerEphemeralBuildService implements MetamodelBuildService {

  // path inside the container where we mount
  private static final String WORK_IN_CONTAINER = "/work";

  // Make image configurable; default to a public JRE
  @Value("${builder.baseImage:eclipse-temurin:21-jre}")
  private String baseImage;

  @Override
  public BuildResult buildAndValidate(MetamodelBuildInput input) {
    Path jobDir = null;
    try {
      jobDir = Files.createTempDirectory("mm-" + input.getMetaModelId() + "-");
      Path inDir = Files.createDirectories(jobDir.resolve("input"));
      Path outDir = Files.createDirectories(jobDir.resolve("output"));

      // 1) Write inputs (if you changed your input to byte[] or paths, adapt here)
      Path ecorePath = inDir.resolve("model.ecore");
      Path genPath = inDir.resolve("model.genmodel");
      Files.write(ecorePath, input.getEcoreFile().getData());
      Files.write(genPath, input.getGenModelFile().getData());

      // 2) Drop the builder jar into jobDir from resources
      Path builderJar = jobDir.resolve("methodologist-build.jar");
      try (var is = getClass().getResourceAsStream("/builder/methodologist-build.jar")) {
        if (is == null) throw new IllegalStateException("builder jar not on classpath");
        Files.copy(is, builderJar, StandardCopyOption.REPLACE_EXISTING);
      }

      // 3) Build docker run command
      List<String> cmd =
          new ArrayList<>(
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
                  "--tmpfs",
                  "/tmp:rw,noexec,nosuid,size=256m",
                  "-v",
                  jobDir.toAbsolutePath() + ":" + WORK_IN_CONTAINER + ":rw",
                  baseImage,
                  "java",
                  "-jar",
                  WORK_IN_CONTAINER + "/methodologist-build.jar",
                  "--ecore",
                  WORK_IN_CONTAINER + "/input/model.ecore",
                  "--genmodel",
                  WORK_IN_CONTAINER + "/input/model.genmodel",
                  "--out",
                  WORK_IN_CONTAINER + "/output",
                  "--run-mwe2",
                  String.valueOf(input.isRunMwe2())));

      // 4) Run it
      ProcessBuilder pb = new ProcessBuilder(cmd);
      pb.redirectErrorStream(true);
      Process p = pb.start();
      String console = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

      boolean finished = p.waitFor(5, TimeUnit.MINUTES);
      if (!finished) {
        p.destroyForcibly();
        throw new IllegalStateException("Builder timeout");
      }
      int exit = p.exitValue();

      // 5) Parse result.json (preferred) or fallback to console
      Path resultJson = jobDir.resolve("output").resolve("result.json");
      if (Files.exists(resultJson)) {
        var dto = new ObjectMapper().readValue(resultJson.toFile(), ResultDto.class);
        return BuildResult.builder()
            .success(dto.success)
            .errors(dto.errors)
            .warnings(dto.warnings)
            .report(dto.report)
            .discoveredNsUris(dto.nsUris == null ? null : String.join(", ", dto.nsUris))
            .build();
      }

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
          .report("Builder crashed: " + e.getMessage())
          .discoveredNsUris(null)
          .build();
    } finally {
      // 6) Cleanup temp folder
      if (jobDir != null) {
        try {
          Files.walk(jobDir).sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (Exception ignore) {
        }
      }
    }
  }

  // DTO matches your builder's JSON
  @lombok.Data
  private static class ResultDto {
    public boolean success;
    public int errors;
    public int warnings;
    public String report;
    public List<String> nsUris;
  }
}
