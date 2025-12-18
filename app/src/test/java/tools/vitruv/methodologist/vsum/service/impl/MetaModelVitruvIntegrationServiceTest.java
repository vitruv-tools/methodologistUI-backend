// package tools.vitruv.methodologist.vsum.service.impl;
//
// import static org.assertj.core.api.Assertions.assertThatThrownBy;
// import static org.mockito.ArgumentMatchers.any;
// import static org.mockito.ArgumentMatchers.anyList;
// import static org.mockito.Mockito.times;
// import static org.mockito.Mockito.verify;
// import static org.mockito.Mockito.when;
//
// import java.io.IOException;
// import java.nio.file.Files;
// import java.nio.file.Path;
// import java.util.List;
// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.Test;
// import org.junit.jupiter.api.extension.ExtendWith;
// import org.mockito.InjectMocks;
// import org.mockito.Mock;
// import org.mockito.MockedStatic;
// import org.mockito.junit.jupiter.MockitoExtension;
// import tools.vitruv.methodologist.exception.VsumBuildingException;
// import tools.vitruv.methodologist.general.model.FileStorage;
// import tools.vitruv.methodologist.vitruvcli.VitruvCliProperties;
// import tools.vitruv.methodologist.vitruvcli.VitruvCliService;
// import tools.vitruv.methodologist.vsum.service.MetaModelVitruvIntegrationService;
//
// @ExtendWith(MockitoExtension.class)
// class MetaModelVitruvIntegrationServiceTest {
//
//  @Mock VitruvCliService vitruvCliService;
//  @Mock VitruvCliProperties vitruvCliProperties;
//
//  @InjectMocks MetaModelVitruvIntegrationService service;
//
//  private FileStorage fs(String name) {
//    FileStorage f = new FileStorage();
//    f.setFilename(name);
//    f.setData(("content-" + name).getBytes());
//    return f;
//  }
//
//  @BeforeEach
//  void setup() {
//    when(vitruvCliProperties.getWorkingDir()).thenReturn("fake/workdir");
//  }
//
//  @Test
//  void runVitruvForMetaModels_success_whenCliSuccess() {
//    FileStorage firstEcore = fs("first.ecore");
//    FileStorage firstGen = fs("first.genmodel");
//    FileStorage secondEcore = fs("second.ecore");
//    FileStorage secondGen = fs("second.genmodel");
//    FileStorage reaction = fs("reactions.reactions");
//
//    VitruvCliService.VitruvCliResult cliResult =
//        VitruvCliService.VitruvCliResult.builder().exitCode(0).stdout("OK").stderr("").build();
//
//    when(vitruvCliService.run(any(Path.class), anyList(), any(Path.class))).thenReturn(cliResult);
//
//    try (MockedStatic<Files> filesMock = org.mockito.Mockito.mockStatic(Files.class)) {
//      filesMock
//          .when(() -> Files.createDirectories(any(Path.class)))
//          .thenAnswer(inv -> inv.getArgument(0));
//
//      filesMock
//          .when(() -> Files.write(any(Path.class), any(byte[].class)))
//          .thenAnswer(inv -> inv.getArgument(0));
//
//      service.runVitruvForMetaModels(firstEcore, firstGen, secondEcore, secondGen, reaction);
//    }
//
//    verify(vitruvCliService, times(1)).run(any(Path.class), anyList(), any(Path.class));
//  }
//
//  @Test
//  void runVitruvForMetaModels_throwsVsumBuildingException_whenCliFailsWithStderr() {
//    FileStorage firstEcore = fs("first.ecore");
//    FileStorage firstGen = fs("first.genmodel");
//    FileStorage secondEcore = fs("second.ecore");
//    FileStorage secondGen = fs("second.genmodel");
//    FileStorage reaction = fs("reactions.reactions");
//
//    VitruvCliService.VitruvCliResult cliResult =
//        VitruvCliService.VitruvCliResult.builder()
//            .exitCode(1)
//            .stdout("some stdout")
//            .stderr("Parsing failed. Reason: Unrecognized option")
//            .build();
//
//    when(vitruvCliService.run(any(Path.class), anyList(), any(Path.class))).thenReturn(cliResult);
//
//    try (MockedStatic<Files> filesMock = org.mockito.Mockito.mockStatic(Files.class)) {
//      filesMock
//          .when(() -> Files.createDirectories(any(Path.class)))
//          .thenAnswer(inv -> inv.getArgument(0));
//      filesMock
//          .when(() -> Files.write(any(Path.class), any(byte[].class)))
//          .thenAnswer(inv -> inv.getArgument(0));
//
//      assertThatThrownBy(
//              () ->
//                  service.runVitruvForMetaModels(
//                      firstEcore, firstGen, secondEcore, secondGen, reaction))
//          .isInstanceOf(VsumBuildingException.class)
//          .hasMessageContaining("Vitruv-CLI Error")
//          .hasMessageContaining("Parsing failed");
//    }
//
//    verify(vitruvCliService, times(1)).run(any(Path.class), anyList(), any(Path.class));
//  }
//
//  @Test
//  void runVitruvForMetaModels_throwsVsumBuildingException_whenCliFailsWithOnlyStdout() {
//    FileStorage firstEcore = fs("first.ecore");
//    FileStorage firstGen = fs("first.genmodel");
//    FileStorage secondEcore = fs("second.ecore");
//    FileStorage secondGen = fs("second.genmodel");
//    FileStorage reaction = fs("reactions.reactions");
//
//    VitruvCliService.VitruvCliResult cliResult =
//        VitruvCliService.VitruvCliResult.builder()
//            .exitCode(1)
//            .stdout("Parsing failed. Reason: Missing required options")
//            .stderr("   ")
//            .build();
//
//    when(vitruvCliService.run(any(Path.class), anyList(), any(Path.class))).thenReturn(cliResult);
//
//    try (MockedStatic<Files> filesMock = org.mockito.Mockito.mockStatic(Files.class)) {
//      filesMock
//          .when(() -> Files.createDirectories(any(Path.class)))
//          .thenAnswer(inv -> inv.getArgument(0));
//      filesMock
//          .when(() -> Files.write(any(Path.class), any(byte[].class)))
//          .thenAnswer(inv -> inv.getArgument(0));
//
//      assertThatThrownBy(
//              () ->
//                  service.runVitruvForMetaModels(
//                      firstEcore, firstGen, secondEcore, secondGen, reaction))
//          .isInstanceOf(VsumBuildingException.class)
//          .hasMessageContaining("Vitruv-CLI Error")
//          .hasMessageContaining("Missing required options");
//    }
//
//    verify(vitruvCliService, times(1)).run(any(Path.class), anyList(), any(Path.class));
//  }
//
//  @Test
//  void runVitruvForMetaModels_throwsVsumBuildingException_whenWorkingDirCannotBeCreated() {
//    try (MockedStatic<Files> filesMock = org.mockito.Mockito.mockStatic(Files.class)) {
//      filesMock
//          .when(() -> Files.createDirectories(any(Path.class)))
//          .thenThrow(new IOException("Permission denied"));
//
//      assertThatThrownBy(
//              () ->
//                  service.runVitruvForMetaModels(
//                      List.of(fs("a.ecore")), List.of(fs("a.genmodel")), fs("r.reactions")))
//          .isInstanceOf(VsumBuildingException.class)
//          .hasMessageContaining("Vitruv-CLI execution failed");
//    }
//
//    verify(vitruvCliService, times(0)).run(any(Path.class), anyList(), any(Path.class));
//  }
//
//  @Test
//  void runVitruvForMetaModels_throwsVsumBuildingException_whenWriteFails() {
//    FileStorage firstEcore = fs("first.ecore");
//    FileStorage firstGen = fs("first.genmodel");
//    FileStorage reaction = fs("reactions.reactions");
//
//    try (MockedStatic<Files> filesMock = org.mockito.Mockito.mockStatic(Files.class)) {
//      filesMock
//          .when(() -> Files.createDirectories(any(Path.class)))
//          .thenAnswer(inv -> inv.getArgument(0));
//
//      filesMock
//          .when(() -> Files.write(any(Path.class), any(byte[].class)))
//          .thenThrow(new IOException("disk full"));
//
//      assertThatThrownBy(
//              () ->
//                  service.runVitruvForMetaModels(List.of(firstEcore), List.of(firstGen),
// reaction))
//          .isInstanceOf(VsumBuildingException.class)
//          .hasMessageContaining("Vitruv-CLI execution failed");
//    }
//
//    verify(vitruvCliService, times(0)).run(any(Path.class), anyList(), any(Path.class));
//  }
// }
