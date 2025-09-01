package tools.vitruv.methodologist.builder;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class MainTest {

  private static final ObjectMapper OM = new ObjectMapper();

  private ProcessResult runMain(String... args) throws Exception {
    String javaBin =
        System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
    String classpath = System.getProperty("java.class.path");

    List<String> command = new ArrayList<>();
    command.add(javaBin);
    command.add("-cp");
    command.add(classpath);
    command.add("tools.vitruv.methodologist.builder.Main");
    command.addAll(Arrays.asList(args));

    ProcessBuilder pb = new ProcessBuilder(command);
    pb.redirectErrorStream(true);
    Process p = pb.start();
    byte[] out = p.getInputStream().readAllBytes();
    int code = p.waitFor();
    return new ProcessResult(code, new String(out, StandardCharsets.UTF_8));
  }

  private JsonNode readResult(Path outDir) throws Exception {
    return OM.readTree(outDir.resolve("result.json").toFile());
  }

  @Test
  void runWithoutArgs_writesFailureJson_andExit1() throws Exception {
    Path out = Files.createTempDirectory("mm-out-");
    ProcessResult pr = runMain("--out", out.toAbsolutePath().toString());
    assertThat(pr.exitCode).isEqualTo(1);
    JsonNode json = readResult(out);
    assertThat(json.path("success").asBoolean()).isFalse();
    assertThat(json.path("errors").asInt()).isEqualTo(1);
    assertThat(json.path("report").asText())
        .contains("Build failed:")
        .contains("Provide either --pairs");
  }

  @Test
  void withPairsBadFormat_writesFailureJson_andExit1() throws Exception {
    Path out = Files.createTempDirectory("mm-out-");
    ProcessResult pr =
        runMain("--pairs", "onlyOnePartNoComma", "--out", out.toAbsolutePath().toString());
    assertThat(pr.exitCode).isEqualTo(1);
    JsonNode json = readResult(out);
    assertThat(json.path("success").asBoolean()).isFalse();
    assertThat(json.path("errors").asInt()).isEqualTo(1);
    assertThat(json.path("report").asText()).contains("Build failed:");
  }

  private static final class ProcessResult {
    final int exitCode;
    final String output;

    ProcessResult(int exitCode, String output) {
      this.exitCode = exitCode;
      this.output = output;
    }
  }
}
