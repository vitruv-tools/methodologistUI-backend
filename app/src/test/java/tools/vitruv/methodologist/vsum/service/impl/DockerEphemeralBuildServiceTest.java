package tools.vitruv.methodologist.vsum.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.vitruv.methodologist.vsum.service.MetamodelBuildService;
import tools.vitruv.methodologist.vsum.service.MetamodelBuildService.BuildResult;
import tools.vitruv.methodologist.vsum.service.MetamodelBuildService.MetamodelBuildInput;

@Slf4j
class DockerEphemeralBuildServiceTest {

  private static final byte[] ECORE = "dummy-ecore".getBytes(StandardCharsets.UTF_8);
  private static final byte[] GEN = "dummy-gen".getBytes(StandardCharsets.UTF_8);

  private TestableService testableService;

  @BeforeEach
  void setUp() {
    testableService = new TestableService();
    testableService.image = "eclipse-temurin:21-jre";
    testableService.jarPath = "/opt/methodologist/methodologist-build.jar";
    testableService.timeoutSeconds = 5;
  }

  @Test
  void success_whenResultJsonPresent() throws Exception {
    String json =
        """
        { "success": true, "errors": 0, "warnings": 1,
          "report":"OK", "nsUris": ["http://a","http://b"] }\
        """;
    testableService.fake = FakeProcess.withResultJson(json, 0, "");
    MetamodelBuildInput in =
        MetamodelBuildService.MetamodelBuildInput.builder()
            .metaModelId(42L)
            .ecoreBytes(ECORE)
            .genModelBytes(GEN)
            .runMwe2(true)
            .build();

    BuildResult res = testableService.buildAndValidate(in);

    assertThat(res.isSuccess()).isTrue();
    assertThat(res.getErrors()).isZero();
    assertThat(res.getWarnings()).isEqualTo(1);
    assertThat(res.getReport()).isEqualTo("OK");
    assertThat(res.getDiscoveredNsUris()).isEqualTo("http://a, http://b");
  }

  @Test
  void timeout_returnsFailure() throws Exception {
    testableService.fake = FakeProcess.neverEnds();
    testableService.timeoutSeconds = 1;

    MetamodelBuildInput in =
        MetamodelBuildService.MetamodelBuildInput.builder()
            .metaModelId(1L)
            .ecoreBytes(ECORE)
            .genModelBytes(GEN)
            .runMwe2(true)
            .build();

    BuildResult res = testableService.buildAndValidate(in);

    assertThat(res.isSuccess()).isFalse();
    assertThat(res.getErrors()).isEqualTo(1);
    assertThat(res.getReport()).contains("Timeout");
  }

  @Test
  void noResultJson_exitZero_usesConsole() throws Exception {
    testableService.fake = FakeProcess.withExit(0, "console ok");

    MetamodelBuildInput in =
        MetamodelBuildService.MetamodelBuildInput.builder()
            .metaModelId(2L)
            .ecoreBytes(ECORE)
            .genModelBytes(GEN)
            .runMwe2(true)
            .build();

    BuildResult res = testableService.buildAndValidate(in);

    assertThat(res.isSuccess()).isTrue();
    assertThat(res.getErrors()).isZero();
    assertThat(res.getReport()).contains("console ok");
  }

  @Test
  void noResultJson_exitNonZero_isFailure() throws Exception {
    testableService.fake = FakeProcess.withExit(7, "boom");

    MetamodelBuildInput in =
        MetamodelBuildService.MetamodelBuildInput.builder()
            .metaModelId(3L)
            .ecoreBytes(ECORE)
            .genModelBytes(GEN)
            .runMwe2(true)
            .build();

    BuildResult res = testableService.buildAndValidate(in);

    assertThat(res.isSuccess()).isFalse();
    assertThat(res.getErrors()).isEqualTo(1);
    assertThat(res.getReport()).contains("boom");
  }

  @Test
  void crash_exception_isFailure() throws Exception {
    testableService.throwOnStart = new IOException("cannot start");
    MetamodelBuildInput in =
        MetamodelBuildService.MetamodelBuildInput.builder()
            .metaModelId(4L)
            .ecoreBytes(ECORE)
            .genModelBytes(GEN)
            .runMwe2(true)
            .build();

    BuildResult res = testableService.buildAndValidate(in);

    assertThat(res.isSuccess()).isFalse();
    assertThat(res.getErrors()).isEqualTo(1);
    assertThat(res.getReport()).contains("Crash: cannot start");
  }

  static class TestableService extends DockerEphemeralBuildService {
    FakeProcess fake;
    IOException throwOnStart;

    @Override
    protected Process startProcess(List<String> cmd) throws IOException {
      if (throwOnStart != null) {
        throw throwOnStart;
      }
      return fake == null ? FakeProcess.withExit(0, "") : fake;
    }
  }

  static class FakeProcess extends Process {
    private final int code;
    private final ByteArrayInputStream out;
    private final long sleepMillis;
    private final Path resultDir;

    private FakeProcess(int code, String stdout, long sleepMillis, Path resultDir) {
      this.code = code;
      this.out = new ByteArrayInputStream(stdout.getBytes(StandardCharsets.UTF_8));
      this.sleepMillis = sleepMillis;
      this.resultDir = resultDir;
    }

    static FakeProcess withExit(int code, String stdout) {
      return new FakeProcess(code, stdout, 0, null);
    }

    static FakeProcess neverEnds() {
      return new FakeProcess(0, "", Long.MAX_VALUE, null);
    }

    static FakeProcess withResultJson(String json, int exit, String stdout) throws IOException {
      Path temp = Files.createTempDirectory("mm-test-out-" + Instant.now().toEpochMilli());
      Files.createDirectories(temp);
      return new FakeProcess(exit, stdout, 50, temp) {
        @Override
        public boolean waitFor(long timeout, java.util.concurrent.TimeUnit unit)
            throws InterruptedException {
          boolean r = super.waitFor(timeout, unit);
          try {
            Path newestOut =
                Files.list(Path.of(System.getProperty("java.io.tmpdir")))
                    .filter(p -> p.getFileName().toString().startsWith("mm-"))
                    .map(p -> p.resolve("output"))
                    .filter(Files::isDirectory)
                    .max(
                        (a, b) -> {
                          try {
                            return Files.getLastModifiedTime(a)
                                .compareTo(Files.getLastModifiedTime(b));
                          } catch (IOException e) {
                            return 0;
                          }
                        })
                    .orElse(null);
            if (newestOut != null) {
              Files.createDirectories(newestOut);
              Files.writeString(newestOut.resolve("result.json"), json);
            }
          } catch (IOException e) {
            log.error(e.getMessage());
          }
          return r;
        }
      };
    }

    @Override
    public OutputStream getOutputStream() {
      return OutputStream.nullOutputStream();
    }

    @Override
    public InputStream getInputStream() {
      return out;
    }

    @Override
    public InputStream getErrorStream() {
      return InputStream.nullInputStream();
    }

    @Override
    public int waitFor() {
      return code;
    }

    @Override
    public boolean waitFor(long timeout, java.util.concurrent.TimeUnit unit)
        throws InterruptedException {
      if (sleepMillis == Long.MAX_VALUE) {
        Thread.sleep(unit.toMillis(timeout) + 5);
        return false;
      }
      Thread.sleep(Math.min(unit.toMillis(timeout), sleepMillis));
      return true;
    }

    @Override
    public int exitValue() {
      return code;
    }

    /* For test*/
    public void destroy() {}

    @Override
    public Process destroyForcibly() {
      return this;
    }

    @Override
    public boolean isAlive() {
      return false;
    }
  }
}
