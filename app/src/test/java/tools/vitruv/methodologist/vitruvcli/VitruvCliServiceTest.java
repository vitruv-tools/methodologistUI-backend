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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

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
  void run_throwsIllegalArgument_whenMetamodelsNull() {
    Path folder = Path.of("/tmp/project");
    Path reaction = Path.of("/tmp/reaction.reactions");

    assertThatThrownBy(() -> service.run(folder, null, reaction))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("At least one metamodel must be provided");
  }

  @Test
  void run_throwsIllegalArgument_whenMetamodelsEmpty() {
    Path folder = Path.of("/tmp/project");
    Path reaction = Path.of("/tmp/reaction.reactions");

    assertThatThrownBy(() -> service.run(folder, List.of(), reaction))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("At least one metamodel must be provided");
  }

  @Test
  void run_returnsSuccessResult_whenProcessExitsZero_andNoStderr() throws Exception {
    Path folder = Path.of("/tmp/project");
    Path reaction = Path.of("/tmp/reaction.reactions");
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
              when(process.exitValue()).thenReturn(0);
              when(process.getInputStream())
                  .thenReturn(
                      new ByteArrayInputStream("OK STDOUT".getBytes(StandardCharsets.UTF_8)));
              when(process.getErrorStream())
                  .thenReturn(new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8)));
            })) {

      VitruvCliService.VitruvCliResult result = service.run(folder, List.of(mm), reaction);

      assertThat(result.getExitCode()).isEqualTo(0);
      assertThat(result.getStdout()).contains("OK STDOUT");
      assertThat(result.getStderr()).isEmpty();
      assertThat(result.isSuccess()).isTrue();
      assertThat(mocked.constructed()).hasSize(1);
    }
  }

  @Test
  void run_returnsFailureResult_whenExitCodeNonZero_orStderrNotBlank() throws Exception {
    Path folder = Path.of("/tmp/project");
    Path reaction = Path.of("/tmp/reaction.reactions");
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

      VitruvCliService.VitruvCliResult result = service.run(folder, List.of(mm), reaction);

      assertThat(result.getExitCode()).isEqualTo(1);
      assertThat(result.getStdout()).contains("some stdout");
      assertThat(result.getStderr()).contains("Parsing failed");
      assertThat(result.isSuccess()).isFalse();
      assertThat(mocked.constructed()).hasSize(1);
    }
  }

  @Test
  void run_throwsIllegalState_whenProcessTimesOut() throws Exception {
    Path folder = Path.of("/tmp/project");
    Path reaction = Path.of("/tmp/reaction.reactions");
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
                  .thenReturn(false); // timeout
            })) {

      assertThatThrownBy(() -> service.run(folder, List.of(mm), reaction))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("timed out");
    }
  }

  @Test
  void run_wrapsIOException_inRuntimeException() throws Exception {
    Path folder = Path.of("/tmp/project");
    Path reaction = Path.of("/tmp/reaction.reactions");
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

      assertThatThrownBy(() -> service.run(folder, List.of(mm), reaction))
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("Failed to execute Vitruv-CLI")
          .hasCauseInstanceOf(IOException.class);
    }
  }

  @Test
  void run_wrapsInterruptedException_inRuntimeException_andKeepsMessage() throws Exception {
    Path folder = Path.of("/tmp/project");
    Path reaction = Path.of("/tmp/reaction.reactions");
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

      assertThatThrownBy(() -> service.run(folder, List.of(mm), reaction))
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("Failed to execute Vitruv-CLI")
          .hasCauseInstanceOf(InterruptedException.class);
    }
  }
}
