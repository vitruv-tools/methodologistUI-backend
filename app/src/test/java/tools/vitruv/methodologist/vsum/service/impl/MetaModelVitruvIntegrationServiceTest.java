package tools.vitruv.methodologist.vsum.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.vitruv.methodologist.exception.VsumBuildingException;
import tools.vitruv.methodologist.general.model.FileStorage;
import tools.vitruv.methodologist.vitruvcli.VitruvCliProperties;
import tools.vitruv.methodologist.vitruvcli.VitruvCliService;
import tools.vitruv.methodologist.vsum.service.MetaModelVitruvIntegrationService;

@Slf4j
@ExtendWith(MockitoExtension.class)
class MetaModelVitruvIntegrationServiceTest {

  @Mock VitruvCliService vitruvCliService;
  @Mock VitruvCliProperties vitruvCliProperties;

  @InjectMocks MetaModelVitruvIntegrationService service;

  private FileStorage fs(String name) {
    FileStorage f = new FileStorage();
    f.setFilename(name);
    f.setData(("content-" + name).getBytes());
    return f;
  }

  @BeforeEach
  void setup() {
    lenient().when(vitruvCliProperties.getWorkingDir()).thenReturn("fake/workdir");
  }

  @Test
  void runVitruvAndGetFatJarBytes_success_whenCliSuccess_andJarExists() {
    var ecores = List.of(fs("model.ecore"), fs("model2.ecore"));
    var gens = List.of(fs("model.genmodel"), fs("model2.genmodel"));
    var reactions = List.of(fs("a.reactions"), fs("b.reactions"));

    byte[] jarBytes = "FAKE_JAR_BYTES".getBytes();

    var cliResult =
        VitruvCliService.VitruvCliResult.builder().exitCode(0).stdout("OK").stderr("").build();

    when(vitruvCliService.run(any(Path.class), anyList(), any(Path.class))).thenReturn(cliResult);

    try (MockedStatic<Files> filesMock = org.mockito.Mockito.mockStatic(Files.class)) {

      filesMock
          .when(() -> Files.createDirectories(any(Path.class)))
          .thenAnswer(inv -> inv.getArgument(0));
      filesMock
          .when(() -> Files.write(any(Path.class), any(byte[].class)))
          .thenAnswer(inv -> inv.getArgument(0));
      filesMock
          .when(() -> Files.writeString(any(Path.class), any(String.class), any()))
          .thenAnswer(inv -> inv.getArgument(0));

      filesMock
          .when(() -> Files.exists(any(Path.class)))
          .thenAnswer(
              inv -> {
                Path p = inv.getArgument(0);
                String pathStr = p.toString().replace("\\", "/");
                return pathStr.contains("vsum/target")
                    && pathStr.contains(
                        "tools.vitruv.methodologisttemplate.vsum-"
                            + "0.1.0-SNAPSHOT-jar-with-dependencies.jar");
              });

      filesMock.when(() -> Files.readAllBytes(any(Path.class))).thenReturn(jarBytes);

      filesMock.when(() -> Files.walk(any(Path.class))).thenReturn(Stream.of());

      byte[] out = service.runVitruvAndGetFatJarBytes(ecores, gens, reactions);

      assertThat(out).isEqualTo(jarBytes);
    }

    verify(vitruvCliService, times(1)).run(any(Path.class), anyList(), any(Path.class));
  }

  @Test
  void runVitruvAndGetFatJarBytes_throws_whenCliFails_prefersStderr() {
    var ecores = List.of(fs("model.ecore"));
    var gens = List.of(fs("model.genmodel"));
    var reactions = List.of(fs("a.reactions"));

    var cliResult =
        VitruvCliService.VitruvCliResult.builder()
            .exitCode(1)
            .stdout("some stdout")
            .stderr("Parsing failed. Reason: Unrecognized option")
            .build();

    when(vitruvCliService.run(any(Path.class), anyList(), any(Path.class))).thenReturn(cliResult);

    try (MockedStatic<Files> filesMock = org.mockito.Mockito.mockStatic(Files.class)) {
      filesMock
          .when(() -> Files.createDirectories(any(Path.class)))
          .thenAnswer(inv -> inv.getArgument(0));
      filesMock
          .when(() -> Files.write(any(Path.class), any(byte[].class)))
          .thenAnswer(inv -> inv.getArgument(0));
      filesMock.when(() -> Files.walk(any(Path.class))).thenReturn(Stream.of());

      assertThatThrownBy(() -> service.runVitruvAndGetFatJarBytes(ecores, gens, reactions))
          .isInstanceOf(VsumBuildingException.class)
          .hasMessageContaining("Vitruv-CLI Error")
          .hasMessageContaining("Parsing failed");
    }

    verify(vitruvCliService, times(1)).run(any(Path.class), anyList(), any(Path.class));
  }

  @Test
  void runVitruvAndGetFatJarBytes_throws_whenCliFails_fallsBackToStdout() {
    var ecores = List.of(fs("model.ecore"));
    var gens = List.of(fs("model.genmodel"));
    var reactions = List.of(fs("a.reactions"));

    var cliResult =
        VitruvCliService.VitruvCliResult.builder()
            .exitCode(1)
            .stdout("Parsing failed. Reason: Missing required options")
            .stderr("   ")
            .build();

    when(vitruvCliService.run(any(Path.class), anyList(), any(Path.class))).thenReturn(cliResult);

    try (MockedStatic<Files> filesMock = org.mockito.Mockito.mockStatic(Files.class)) {
      filesMock
          .when(() -> Files.createDirectories(any(Path.class)))
          .thenAnswer(inv -> inv.getArgument(0));
      filesMock
          .when(() -> Files.write(any(Path.class), any(byte[].class)))
          .thenAnswer(inv -> inv.getArgument(0));
      filesMock.when(() -> Files.walk(any(Path.class))).thenReturn(Stream.of());

      assertThatThrownBy(() -> service.runVitruvAndGetFatJarBytes(ecores, gens, reactions))
          .isInstanceOf(VsumBuildingException.class)
          .hasMessageContaining("Vitruv-CLI Error")
          .hasMessageContaining("Missing required options");
    }

    verify(vitruvCliService, times(1)).run(any(Path.class), anyList(), any(Path.class));
  }

  @Test
  void runVitruvAndGetFatJarBytes_throws_whenFatJarNotFound() {
    var ecores = List.of(fs("model.ecore"));
    var gens = List.of(fs("model.genmodel"));
    var reactions = List.of(fs("a.reactions"));

    var cliResult =
        VitruvCliService.VitruvCliResult.builder().exitCode(0).stdout("OK").stderr("").build();

    when(vitruvCliService.run(any(Path.class), anyList(), any(Path.class))).thenReturn(cliResult);

    try (MockedStatic<Files> filesMock = org.mockito.Mockito.mockStatic(Files.class)) {
      filesMock
          .when(() -> Files.createDirectories(any(Path.class)))
          .thenAnswer(inv -> inv.getArgument(0));
      filesMock
          .when(() -> Files.write(any(Path.class), any(byte[].class)))
          .thenAnswer(inv -> inv.getArgument(0));
      filesMock.when(() -> Files.exists(any(Path.class))).thenReturn(false);
      filesMock.when(() -> Files.walk(any(Path.class))).thenReturn(Stream.of());

      assertThatThrownBy(() -> service.runVitruvAndGetFatJarBytes(ecores, gens, reactions))
          .isInstanceOf(VsumBuildingException.class)
          .hasMessageContaining("Fat jar not found");
    }

    verify(vitruvCliService, times(1)).run(any(Path.class), anyList(), any(Path.class));
  }

  @Test
  void runVitruvAndGetFatJarBytes_throws_whenWorkingDirCannotBeCreated() {
    var ecores = List.of(fs("model.ecore"));
    var gens = List.of(fs("model.genmodel"));
    var reactions = List.of(fs("a.reactions"));

    try (MockedStatic<Files> filesMock = org.mockito.Mockito.mockStatic(Files.class)) {
      filesMock
          .when(() -> Files.createDirectories(any(Path.class)))
          .thenThrow(new IOException("Permission denied"));

      assertThatThrownBy(() -> service.runVitruvAndGetFatJarBytes(ecores, gens, reactions))
          .isInstanceOf(VsumBuildingException.class)
          .hasMessageContaining("execution failed");
    }

    verify(vitruvCliService, times(0)).run(any(Path.class), anyList(), any(Path.class));
  }

  @Test
  void runVitruvAndGetFatJarBytes_throws_whenWriteFails() {
    var ecores = List.of(fs("model.ecore"));
    var gens = List.of(fs("model.genmodel"));
    var reactions = List.of(fs("a.reactions"));

    try (MockedStatic<Files> filesMock = org.mockito.Mockito.mockStatic(Files.class)) {
      filesMock
          .when(() -> Files.createDirectories(any(Path.class)))
          .thenAnswer(inv -> inv.getArgument(0));
      filesMock
          .when(() -> Files.write(any(Path.class), any(byte[].class)))
          .thenThrow(new IOException("disk full"));

      assertThatThrownBy(() -> service.runVitruvAndGetFatJarBytes(ecores, gens, reactions))
          .isInstanceOf(VsumBuildingException.class)
          .hasMessageContaining("execution failed");
    }

    verify(vitruvCliService, times(0)).run(any(Path.class), anyList(), any(Path.class));
  }

  @Test
  void runVitruvAndGetFatJarBytes_validationFails_whenMetamodelListsMissing() {
    ThrowingCallable callable =
        () -> service.runVitruvAndGetFatJarBytes(null, null, List.of(fs("a.reactions")));

    assertThatThrownBy(callable).isInstanceOf(VsumBuildingException.class);

    ThrowingCallable nextCallable =
        () -> service.runVitruvAndGetFatJarBytes(List.of(), List.of(), List.of(fs("a.reactions")));

    assertThatThrownBy(nextCallable).isInstanceOf(VsumBuildingException.class);

    verify(vitruvCliService, times(0)).run(any(Path.class), anyList(), any(Path.class));
  }

  @Test
  void runVitruvAndGetFatJarBytes_validationFails_whenMetamodelCountMismatch() {
    var ecores = List.of(fs("a.ecore"), fs("b.ecore"));
    var gens = List.of(fs("a.genmodel"));
    var reactions = List.of(fs("a.reactions"));

    assertThatThrownBy(() -> service.runVitruvAndGetFatJarBytes(ecores, gens, reactions))
        .isInstanceOf(VsumBuildingException.class);

    verify(vitruvCliService, times(0)).run(any(Path.class), anyList(), any(Path.class));
  }

  @Test
  void runVitruvAndGetFatJarBytes_validationFails_whenReactionsMissing() {
    var ecores = List.of(fs("a.ecore"));
    var gens = List.of(fs("a.genmodel"));

    assertThatThrownBy(() -> service.runVitruvAndGetFatJarBytes(ecores, gens, null))
        .isInstanceOf(VsumBuildingException.class);

    ThrowingCallable callable = () -> service.runVitruvAndGetFatJarBytes(ecores, gens, List.of());

    assertThatThrownBy(callable).isInstanceOf(VsumBuildingException.class);

    verify(vitruvCliService, times(0)).run(any(Path.class), anyList(), any(Path.class));
  }

  @Test
  void runVitruvAndGetFatJarBytes_throws_whenReadAllBytesFails() {
    var ecores = List.of(fs("model.ecore"));
    var gens = List.of(fs("model.genmodel"));
    var reactions = List.of(fs("a.reactions"));

    var cliResult =
        VitruvCliService.VitruvCliResult.builder().exitCode(0).stdout("OK").stderr("").build();

    when(vitruvCliService.run(any(Path.class), anyList(), any(Path.class))).thenReturn(cliResult);

    try (MockedStatic<Files> filesMock = org.mockito.Mockito.mockStatic(Files.class)) {
      filesMock
          .when(() -> Files.createDirectories(any(Path.class)))
          .thenAnswer(inv -> inv.getArgument(0));
      filesMock
          .when(() -> Files.write(any(Path.class), any(byte[].class)))
          .thenAnswer(inv -> inv.getArgument(0));

      filesMock
          .when(() -> Files.exists(any(Path.class)))
          .thenAnswer(
              inv -> {
                Path p = inv.getArgument(0);
                String pathStr = p.toString().replace("\\", "/");
                return pathStr.contains("vsum/target")
                    && pathStr.contains(
                        "tools.vitruv.methodologisttemplate.vsum-0.1.0-SNAPSHOT-"
                            + "jar-with-dependencies.jar");
              });

      filesMock
          .when(() -> Files.readAllBytes(any(Path.class)))
          .thenThrow(new IOException("read failed"));
      filesMock.when(() -> Files.walk(any(Path.class))).thenReturn(Stream.of());

      assertThatThrownBy(() -> service.runVitruvAndGetFatJarBytes(ecores, gens, reactions))
          .isInstanceOf(VsumBuildingException.class)
          .hasMessageContaining("execution failed");
    }

    verify(vitruvCliService, times(1)).run(any(Path.class), anyList(), any(Path.class));
  }

  @Test
  void shouldAttemptCleanup_whenCliFails() {
    var ecores = List.of(fs("model.ecore"));
    var gens = List.of(fs("model.genmodel"));
    var reactions = List.of(fs("a.reactions"));

    VitruvCliService.VitruvCliResult failResult =
        VitruvCliService.VitruvCliResult.builder()
            .exitCode(1)
            .stdout("")
            .stderr("Build failed")
            .build();

    when(vitruvCliService.run(any(Path.class), anyList(), any(Path.class))).thenReturn(failResult);

    try (MockedStatic<Files> filesMock = org.mockito.Mockito.mockStatic(Files.class)) {
      filesMock
          .when(() -> Files.createDirectories(any(Path.class)))
          .thenAnswer(inv -> inv.getArgument(0));
      filesMock
          .when(() -> Files.write(any(Path.class), any(byte[].class)))
          .thenAnswer(inv -> inv.getArgument(0));
      filesMock.when(() -> Files.exists(any(Path.class))).thenReturn(true);
      filesMock.when(() -> Files.walk(any(Path.class))).thenReturn(Stream.empty());
      filesMock
          .when(
              () ->
                  Files.walk(any(Path.class), anyInt(), any(java.nio.file.FileVisitOption[].class)))
          .thenReturn(Stream.empty());
      filesMock
          .when(() -> Files.walk(any(Path.class), any(java.nio.file.FileVisitOption[].class)))
          .thenReturn(Stream.empty());

      assertThatThrownBy(() -> service.runVitruvAndGetFatJarBytes(ecores, gens, reactions))
          .isInstanceOf(VsumBuildingException.class);

      boolean walkInvoked = false;
      try {
        filesMock.verify(() -> Files.walk(any(Path.class)));
        walkInvoked = true;
      } catch (AssertionError ignored) {
        log.warn("Expected Files.walk to be invoked once");
      }
      try {
        filesMock.verify(
            () ->
                Files.walk(any(Path.class), anyInt(), any(java.nio.file.FileVisitOption[].class)));
        walkInvoked = true;
      } catch (AssertionError ignored) {
        log.warn("Expected Files.walk to be invoked once");
      }
      try {
        filesMock.verify(
            () -> Files.walk(any(Path.class), any(java.nio.file.FileVisitOption[].class)));
        walkInvoked = true;
      } catch (AssertionError ignored) {
        log.warn("Expected Files.walk to be invoked once");
      }
      assertThat(walkInvoked).isTrue();
    }
  }
}
