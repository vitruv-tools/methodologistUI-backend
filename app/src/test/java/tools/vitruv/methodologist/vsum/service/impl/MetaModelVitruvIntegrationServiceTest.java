package tools.vitruv.methodologist.vsum.service.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.vitruv.methodologist.exception.VsumBuildingException;
import tools.vitruv.methodologist.general.model.FileStorage;
import tools.vitruv.methodologist.vitruvcli.VitruvCliService;
import tools.vitruv.methodologist.vsum.service.MetaModelVitruvIntegrationService;

@ExtendWith(MockitoExtension.class)
class MetaModelVitruvIntegrationServiceTest {

  @Mock VitruvCliService vitruvCliService;

  @InjectMocks MetaModelVitruvIntegrationService service;

  private FileStorage fs(String name) {
    FileStorage f = new FileStorage();
    f.setFilename(name);
    f.setData(("content-" + name).getBytes());
    return f;
  }

  @Test
  void runVitruvForMetaModels_success_whenCliSuccess() {
    FileStorage firstEcore = fs("first.ecore");
    FileStorage firstGen = fs("first.genmodel");
    FileStorage secondEcore = fs("second.ecore");
    FileStorage secondGen = fs("second.genmodel");
    FileStorage reaction = fs("reactions.reactions");

    VitruvCliService.VitruvCliResult cliResult =
        VitruvCliService.VitruvCliResult.builder().exitCode(0).stdout("OK").stderr("").build();

    when(vitruvCliService.run(any(Path.class), anyList(), any(Path.class))).thenReturn(cliResult);

    service.runVitruvForMetaModels(firstEcore, firstGen, secondEcore, secondGen, reaction);

    ArgumentCaptor<Path> projectFolderCap = ArgumentCaptor.forClass(Path.class);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<VitruvCliService.MetamodelInput>> metamodelsCap =
        ArgumentCaptor.forClass(List.class);
    ArgumentCaptor<Path> reactionCap = ArgumentCaptor.forClass(Path.class);

    verify(vitruvCliService)
        .run(projectFolderCap.capture(), metamodelsCap.capture(), reactionCap.capture());

    List<VitruvCliService.MetamodelInput> metamodels = metamodelsCap.getValue();
    Path reactionPath = reactionCap.getValue();

    org.assertj.core.api.Assertions.assertThat(metamodels).hasSize(2);
    org.assertj.core.api.Assertions.assertThat(
            metamodels.get(0).getEcorePath().getFileName().toString())
        .isEqualTo("first.ecore");
    org.assertj.core.api.Assertions.assertThat(
            metamodels.get(0).getGenmodelPath().getFileName().toString())
        .isEqualTo("first.genmodel");
    org.assertj.core.api.Assertions.assertThat(
            metamodels.get(1).getEcorePath().getFileName().toString())
        .isEqualTo("second.ecore");
    org.assertj.core.api.Assertions.assertThat(
            metamodels.get(1).getGenmodelPath().getFileName().toString())
        .isEqualTo("second.genmodel");

    org.assertj.core.api.Assertions.assertThat(reactionPath.getFileName().toString())
        .isEqualTo("reactions.reactions");
  }

  @Test
  void runVitruvForMetaModels_throwsVsumBuildingException_whenCliFailsWithStderr() {
    FileStorage firstEcore = fs("first.ecore");
    FileStorage firstGen = fs("first.genmodel");
    FileStorage secondEcore = fs("second.ecore");
    FileStorage secondGen = fs("second.genmodel");
    FileStorage reaction = fs("reactions.reactions");

    VitruvCliService.VitruvCliResult cliResult =
        VitruvCliService.VitruvCliResult.builder()
            .exitCode(1)
            .stdout("some stdout")
            .stderr("Parsing failed. Reason: Unrecognized option")
            .build();

    when(vitruvCliService.run(any(Path.class), anyList(), any(Path.class))).thenReturn(cliResult);

    assertThatThrownBy(
            () ->
                service.runVitruvForMetaModels(
                    firstEcore, firstGen, secondEcore, secondGen, reaction))
        .isInstanceOf(VsumBuildingException.class)
        .hasMessageContaining("Vitruv-CLI Error")
        .hasMessageContaining("Parsing failed");
  }

  @Test
  void runVitruvForMetaModels_throwsVsumBuildingException_whenCliFailsWithOnlyStdout() {
    FileStorage firstEcore = fs("first.ecore");
    FileStorage firstGen = fs("first.genmodel");
    FileStorage secondEcore = fs("second.ecore");
    FileStorage secondGen = fs("second.genmodel");
    FileStorage reaction = fs("reactions.reactions");

    VitruvCliService.VitruvCliResult cliResult =
        VitruvCliService.VitruvCliResult.builder()
            .exitCode(1)
            .stdout("Parsing failed. Reason: Missing required options")
            .stderr("   ")
            .build();

    when(vitruvCliService.run(any(Path.class), anyList(), any(Path.class))).thenReturn(cliResult);

    assertThatThrownBy(
            () ->
                service.runVitruvForMetaModels(
                    firstEcore, firstGen, secondEcore, secondGen, reaction))
        .isInstanceOf(VsumBuildingException.class)
        .hasMessageContaining("Vitruv-CLI Error")
        .hasMessageContaining("Missing required options");
  }

  @Test
  void runVitruvForMetaModels_throwsVsumBuildingException_whenIOExceptionOccurs() {
    FileStorage firstEcore = fs("first.ecore");
    FileStorage firstGen = fs("first.genmodel");
    FileStorage secondEcore = fs("second.ecore");
    FileStorage secondGen = fs("second.genmodel");
    FileStorage reaction = fs("reactions.reactions");

    try (MockedStatic<Files> filesMock = org.mockito.Mockito.mockStatic(Files.class)) {
      filesMock
          .when(() -> Files.createTempDirectory("vitruv-job-"))
          .thenThrow(new IOException("disk full"));

      assertThatThrownBy(
              () ->
                  service.runVitruvForMetaModels(
                      firstEcore, firstGen, secondEcore, secondGen, reaction))
          .isInstanceOf(VsumBuildingException.class)
          .hasMessageContaining("Vitruv-CLI execution failed");
    }
  }
}
