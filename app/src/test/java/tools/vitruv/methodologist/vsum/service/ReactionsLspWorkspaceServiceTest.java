package tools.vitruv.methodologist.vsum.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.vitruv.methodologist.general.model.FileStorage;
import tools.vitruv.methodologist.vsum.model.MetaModel;
import tools.vitruv.methodologist.vsum.utilitymethod.EcoreTypeIndex;
import tools.vitruv.methodologist.vsum.utilitymethod.EmfApiStubs;
import tools.vitruv.methodologist.vsum.utilitymethod.UtilityMethodClassGenerator;
import tools.vitruv.methodologist.vsum.utilitymethod.UtilityMethodDefinition;
import tools.vitruv.methodologist.vsum.utilitymethod.UtilityMethodDefinition.Parameter;

class ReactionsLspWorkspaceServiceTest {

  private static final long VSUM_ID = 7L;
  private static final String MODEL_CLASSES = "tools/vitruv/methodologisttemplate/model/simulink/";
  private static final String UTILITY_METHOD_CLASS =
      UtilityMethodClassGenerator.QUALIFIED_CLASS_NAME.replace('.', '/') + ".class";

  @TempDir Path tempDir;

  private final MetaModelService metaModelService = mock(MetaModelService.class);
  private final UtilityMethodService utilityMethodService = mock(UtilityMethodService.class);
  private final ReactionsLspWorkspaceService service =
      new ReactionsLspWorkspaceService(metaModelService, utilityMethodService);

  private Path sessionDir;
  private Path emf;
  private byte[] simulink;

  @BeforeEach
  void setUp() throws IOException {
    sessionDir = Files.createDirectory(tempDir.resolve("session"));
    emf = EmfApiStubs.compile(tempDir.resolve("emf"));
    try (InputStream in = getClass().getResourceAsStream("/utilitymethod/simulink.ecore")) {
      simulink = in.readAllBytes();
    }
    MetaModel metaModel = new MetaModel();
    metaModel.setEcoreFile(
        FileStorage.builder().id(1L).filename("simulink.ecore").data(simulink).build());
    when(metaModelService.findAccessibleByProject(VSUM_ID)).thenReturn(List.of(metaModel));
  }

  @Test
  void prepare_compilesModelApiAndUtilityMethods_andNamesTheInstanceClasses() throws IOException {
    when(utilityMethodService.generateEditorSource(eq(VSUM_ID), anyCollection()))
        .thenReturn(Optional.of(utilityMethodSource()));

    ReactionsLspWorkspaceService.Workspace workspace = service.prepare(sessionDir, VSUM_ID, emf);

    Path classes = sessionDir.resolve(ReactionsLspWorkspaceService.CLASSES_DIR);
    assertThat(workspace.projectDir())
        .isEqualTo(sessionDir.resolve(ReactionsLspWorkspaceService.PROJECT_DIR));
    assertThat(workspace.classpath()).containsExactly(classes);
    assertThat(classes.resolve(MODEL_CLASSES + "Gain.class")).exists();
    assertThat(classes.resolve(MODEL_CLASSES + "SimulinkFactory.class")).exists();
    assertThat(classes.resolve(UTILITY_METHOD_CLASS)).exists();
    assertThat(writtenEcore(workspace))
        .contains("instanceClassName=\"tools.vitruv.methodologisttemplate.model.simulink.Gain\"");
  }

  @Test
  void prepare_writesThePlainMetamodels_whenTheModelApiDoesNotCompile() throws IOException {
    Path withoutEmf = Files.createDirectory(tempDir.resolve("no-emf"));

    ReactionsLspWorkspaceService.Workspace workspace =
        service.prepare(sessionDir, VSUM_ID, withoutEmf);

    assertThat(workspace.classpath()).isEmpty();
    assertThat(workspace.projectDir().resolve("model/simulink.ecore")).hasBinaryContent(simulink);
  }

  @Test
  void prepare_keepsTheModelApi_whenTheUtilityMethodsDoNotCompile() throws IOException {
    when(utilityMethodService.generateEditorSource(eq(VSUM_ID), anyCollection()))
        .thenReturn(Optional.of("package broken; class"));

    ReactionsLspWorkspaceService.Workspace workspace = service.prepare(sessionDir, VSUM_ID, emf);

    Path classes = sessionDir.resolve(ReactionsLspWorkspaceService.CLASSES_DIR);
    assertThat(workspace.classpath()).containsExactly(classes);
    assertThat(classes.resolve(MODEL_CLASSES + "Gain.class")).exists();
    assertThat(classes.resolve(UTILITY_METHOD_CLASS)).doesNotExist();
  }

  @Test
  void prepare_createsAnEmptyWorkspace_withoutVsum() throws IOException {
    when(metaModelService.findAccessibleByProject(null)).thenReturn(List.of());

    ReactionsLspWorkspaceService.Workspace workspace = service.prepare(sessionDir, null, emf);

    assertThat(workspace.classpath()).isEmpty();
    assertThat(workspace.projectDir().resolve(ReactionsLspWorkspaceService.MODEL_DIR))
        .isEmptyDirectory();
    verifyNoInteractions(utilityMethodService);
  }

  @Test
  void launchCommand_runsTheJar_withTheClassesOnTheClasspath_ifAny() throws IOException {
    Path jar = jar("tools.example.Launcher");
    Path classes = tempDir.resolve("classes");

    assertThat(
            new ReactionsLspWorkspaceService.Workspace(tempDir, List.of())
                .launchCommand("java", jar))
        .containsExactly("java", "-jar", jar.toString());
    assertThat(
            new ReactionsLspWorkspaceService.Workspace(tempDir, List.of(classes))
                .launchCommand("java", jar))
        .containsExactly(
            "java", "-cp", jar + File.pathSeparator + classes, "tools.example.Launcher");
  }

  @Test
  void launchCommand_fails_whenTheJarNamesNoMainClass() throws IOException {
    Path jar = jar(null);
    ReactionsLspWorkspaceService.Workspace workspace =
        new ReactionsLspWorkspaceService.Workspace(tempDir, List.of(tempDir.resolve("classes")));

    assertThatThrownBy(() -> workspace.launchCommand("java", jar))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("no main class");
  }

  // ── fixtures ─────────────────────────────────────────────────────────────

  private String utilityMethodSource() {
    EcoreTypeIndex index =
        EcoreTypeIndex.of(List.of(new EcoreTypeIndex.EcoreFile("simulink.ecore", simulink)));
    UtilityMethodDefinition first =
        new UtilityMethodDefinition(
            "first",
            null,
            List.of(new Parameter("model", "SimulinkModel")),
            "Block",
            List.of("simulink::*"),
            "model.contains->first()");
    return UtilityMethodClassGenerator.generate(List.of(first), index).source();
  }

  private static String writtenEcore(ReactionsLspWorkspaceService.Workspace workspace)
      throws IOException {
    return Files.readString(
        workspace.projectDir().resolve("model/simulink.ecore"), StandardCharsets.UTF_8);
  }

  private Path jar(String mainClass) throws IOException {
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    if (mainClass != null) {
      manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, mainClass);
    }
    Path jar = Files.createTempFile(tempDir, "server", ".jar");
    try (OutputStream out = Files.newOutputStream(jar);
        JarOutputStream ignored = new JarOutputStream(out, manifest)) {
      // A manifest is all the launch command reads.
    }
    return jar;
  }
}
