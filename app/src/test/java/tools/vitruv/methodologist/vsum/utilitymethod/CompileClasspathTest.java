package tools.vitruv.methodologist.vsum.utilitymethod;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import tools.vitruv.methodologist.general.model.FileStorage;

class CompileClasspathTest {

  private CompileClasspath classpath;

  @BeforeEach
  void setUp() {
    classpath =
        new CompileClasspath(new ClassPathResource("lsp/tools.vitruv.dsls.reactions.ide.jar"));
  }

  @AfterEach
  void tearDown() {
    classpath.cleanUp();
  }

  private static FileStorage ecore(String name) throws IOException {
    try (InputStream in =
        CompileClasspathTest.class.getResourceAsStream("/utilitymethod/" + name)) {
      return FileStorage.builder().filename(name).data(in.readAllBytes()).build();
    }
  }

  @Test
  void forVsum_compilesTheModelApiAndAddsTheServerJar() throws IOException {
    List<Path> paths = classpath.forVsum(List.of(ecore("simulink.ecore"))).paths();

    assertThat(paths).hasSize(2);
    assertThat(
            paths
                .get(0)
                .resolve("tools/vitruv/methodologisttemplate/model/simulink/SubSystem.class"))
        .exists();
    assertThat(paths.get(1)).isEqualTo(classpath.reactionsServerJar()).isRegularFile();
  }

  @Test
  void forVsum_reusesTheCompiledApiForTheSameMetamodelsInAnyOrder() throws IOException {
    FileStorage simulink = ecore("simulink.ecore");
    FileStorage library = ecore("library.ecore");

    Path first = classpath.forVsum(List.of(simulink, library)).paths().get(0);
    Path again = classpath.forVsum(List.of(library, simulink)).paths().get(0);
    Path other = classpath.forVsum(List.of(simulink)).paths().get(0);

    assertThat(again).isEqualTo(first);
    assertThat(other).isNotEqualTo(first);
  }

  @Test
  void forVsum_withoutMetamodels_onlyHasTheServerJar() {
    assertThat(classpath.forVsum(List.of()).paths())
        .containsExactly(classpath.reactionsServerJar());
  }

  @Test
  void reactionsServerJar_isExtractedOnce() {
    Path jar = classpath.reactionsServerJar();

    assertThat(classpath.reactionsServerJar()).isEqualTo(jar);
  }

  @Test
  void forVsum_failsClearly_whenTheMetamodelApiDoesNotCompile() throws IOException {
    // Without EMF on the classpath the generated API cannot compile.
    CompileClasspath withoutEmf = new CompileClasspath(new ByteArrayResource(new byte[0]));
    List<FileStorage> ecores = List.of(ecore("simulink.ecore"));
    try {
      assertThatThrownBy(() -> withoutEmf.forVsum(ecores))
          .isInstanceOf(UtilityMethodCompilationException.class)
          .hasMessageContaining("does not compile");
    } finally {
      withoutEmf.cleanUp();
    }
  }

  @Test
  void cleanUp_deletesTheExtractedFiles() {
    Path jar = classpath.reactionsServerJar();

    classpath.cleanUp();

    assertThat(Files.exists(jar)).isFalse();
  }
}
