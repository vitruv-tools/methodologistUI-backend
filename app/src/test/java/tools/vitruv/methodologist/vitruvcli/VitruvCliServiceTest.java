package tools.vitruv.methodologist.vitruvcli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import tools.vitruv.methodologist.exception.CLIExecuteException;

class VitruvCliServiceTest {

  private VitruvCliProperties properties;
  private VitruvCliService service;

  @BeforeEach
  void setUp() {
    properties = new VitruvCliProperties();
    properties.setBinary("java");
    properties.setJar("/opt/vitruv/vitruv-cli.jar");
    properties.setWorkingDir("build/vitruv-cli-test-workdir");
    properties.setTimeoutSeconds(5);

    service = new VitruvCliService(properties);
  }

  @Test
  void run_returnsSuccessResult_whenProcessExitsZero_andNoStderr() {
    Path folder = Path.of("/tmp/project");
    Path reactionsDir = Path.of("/tmp/reactions");
    VitruvCliService.MetamodelInput mm =
        VitruvCliService.MetamodelInput.builder()
            .ecorePath(Path.of("/tmp/model.ecore"))
            .genmodelPath(Path.of("/tmp/model.genmodel"))
            .build();

    try (MockedConstruction<ProcessBuilder> mocked =
        mockConstruction(
            ProcessBuilder.class,
            (pbMock, context) -> {
              List<String> cmd = context.arguments().stream().map(Object::toString).toList();
              assertThat(cmd.toString()).contains("-rs").contains("reactions");

              when(pbMock.directory(any(File.class))).thenReturn(pbMock);
              when(pbMock.redirectErrorStream(false)).thenReturn(pbMock);

              Process process = mock(Process.class);
              when(pbMock.start()).thenReturn(process);

              when(process.waitFor(properties.getTimeoutSeconds(), TimeUnit.SECONDS))
                  .thenReturn(true);
              when(process.exitValue()).thenReturn(0);
              when(process.getInputStream())
                  .thenReturn(
                      new ByteArrayInputStream("OK STDOUT".getBytes(StandardCharsets.UTF_8)));
              when(process.getErrorStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
            })) {

      VitruvCliService.VitruvCliResult result = service.run(folder, List.of(mm), reactionsDir);

      assertThat(result.getExitCode()).isZero();
      assertThat(result.getStdout()).contains("OK STDOUT");
      assertThat(result.getStderr()).isEmpty();
      assertThat(result.isSuccess()).isTrue();
      assertThat(mocked.constructed()).hasSize(1);
    }
  }

  @Test
  void run_returnsFailureResult_whenExitCodeNonZero() {
    Path folder = Path.of("/tmp/project");
    Path reactionsDir = Path.of("/tmp/reactions");
    VitruvCliService.MetamodelInput mm =
        VitruvCliService.MetamodelInput.builder()
            .ecorePath(Path.of("/tmp/model.ecore"))
            .genmodelPath(Path.of("/tmp/model.genmodel"))
            .build();

    try (MockedConstruction<ProcessBuilder> mocked =
        mockConstruction(
            ProcessBuilder.class,
            (pbMock, context) -> {
              when(pbMock.directory(any(File.class))).thenReturn(pbMock);
              when(pbMock.redirectErrorStream(false)).thenReturn(pbMock);

              Process process = mock(Process.class);
              when(pbMock.start()).thenReturn(process);

              when(process.waitFor(properties.getTimeoutSeconds(), TimeUnit.SECONDS))
                  .thenReturn(true);
              when(process.exitValue()).thenReturn(1);
              when(process.getInputStream())
                  .thenReturn(
                      new ByteArrayInputStream("some stdout".getBytes(StandardCharsets.UTF_8)));
              when(process.getErrorStream())
                  .thenReturn(
                      new ByteArrayInputStream("Parsing failed".getBytes(StandardCharsets.UTF_8)));
            })) {

      VitruvCliService.VitruvCliResult result = service.run(folder, List.of(mm), reactionsDir);

      assertThat(result.getExitCode()).isEqualTo(1);
      assertThat(result.getStdout()).contains("some stdout");
      assertThat(result.getStderr()).contains("Parsing failed");
      assertThat(result.isSuccess()).isFalse();
      assertThat(mocked.constructed()).hasSize(1);
    }
  }

  @Test
  void run_throwsIllegalState_whenProcessTimesOut() {
    Path folder = Path.of("/tmp/project");
    Path reactionsDir = Path.of("/tmp/reactions");
    VitruvCliService.MetamodelInput mm =
        VitruvCliService.MetamodelInput.builder()
            .ecorePath(Path.of("/tmp/model.ecore"))
            .genmodelPath(Path.of("/tmp/model.genmodel"))
            .build();

    try (MockedConstruction<ProcessBuilder> mocked =
        mockConstruction(
            ProcessBuilder.class,
            (pbMock, context) -> {
              when(pbMock.directory(any(File.class))).thenReturn(pbMock);
              when(pbMock.redirectErrorStream(false)).thenReturn(pbMock);

              Process process = mock(Process.class);
              when(pbMock.start()).thenReturn(process);

              when(process.waitFor(properties.getTimeoutSeconds(), TimeUnit.SECONDS))
                  .thenReturn(false);
            })) {

      ThrowingCallable callable = () -> service.run(folder, List.of(mm), reactionsDir);

      assertThatThrownBy(callable)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("timed out");
    }
  }

  @Test
  void run_wrapsIOException_inCLIExecuteException() {
    Path folder = Path.of("/tmp/project");
    Path reactionsDir = Path.of("/tmp/reactions");
    VitruvCliService.MetamodelInput mm =
        VitruvCliService.MetamodelInput.builder()
            .ecorePath(Path.of("/tmp/model.ecore"))
            .genmodelPath(Path.of("/tmp/model.genmodel"))
            .build();

    try (MockedConstruction<ProcessBuilder> mocked =
        mockConstruction(
            ProcessBuilder.class,
            (pbMock, context) -> {
              when(pbMock.directory(any(File.class))).thenReturn(pbMock);
              when(pbMock.redirectErrorStream(false)).thenReturn(pbMock);

              when(pbMock.start()).thenThrow(new IOException("cannot start process"));
            })) {

      ThrowingCallable callable = () -> service.run(folder, List.of(mm), reactionsDir);

      assertThatThrownBy(callable)
          .isInstanceOf(CLIExecuteException.class)
          .hasMessageContaining("cannot start process");
    }
  }

  @Test
  void run_wrapsInterruptedException_inCLIExecuteException_andInterruptsThread() {
    Path folder = Path.of("/tmp/project");
    Path reactionsDir = Path.of("/tmp/reactions");
    VitruvCliService.MetamodelInput mm =
        VitruvCliService.MetamodelInput.builder()
            .ecorePath(Path.of("/tmp/model.ecore"))
            .genmodelPath(Path.of("/tmp/model.genmodel"))
            .build();

    try (MockedConstruction<ProcessBuilder> mocked =
        mockConstruction(
            ProcessBuilder.class,
            (pbMock, context) -> {
              when(pbMock.directory(any(File.class))).thenReturn(pbMock);
              when(pbMock.redirectErrorStream(false)).thenReturn(pbMock);

              Process process = mock(Process.class);
              when(pbMock.start()).thenReturn(process);

              when(process.waitFor(properties.getTimeoutSeconds(), TimeUnit.SECONDS))
                  .thenThrow(new InterruptedException("interrupted"));
            })) {

      ThrowingCallable callable = () -> service.run(folder, List.of(mm), reactionsDir);

      assertThatThrownBy(callable)
          .isInstanceOf(CLIExecuteException.class)
          .hasMessageContaining("interrupted");
    }
  }
}
