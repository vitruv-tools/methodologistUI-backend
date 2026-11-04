package tools.vitruv.methodologist.vsum.utilitymethod;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import tools.vitruv.methodologist.general.model.FileStorage;
import tools.vitruv.methodologist.vsum.utilitymethod.UtilityMethodClassGenerator.Result;
import tools.vitruv.methodologist.vsum.utilitymethod.UtilityMethodClassGenerator.UtilityMethodError;
import tools.vitruv.methodologist.vsum.utilitymethod.UtilityMethodDefinition.Parameter;

/**
 * Compiles utility methods with the real Xtend compiler against the API of the simulink test
 * metamodel and the bundled reactions language server jar.
 */
class UtilityMethodCompilerTest {

  private static final String SIMULINK_IMPORT =
      "tools.vitruv.methodologisttemplate.model.simulink.*";

  private static CompileClasspath classpath;
  private static XtendUtilityCompiler xtendCompiler;
  private static UtilityMethodCompiler compiler;
  private static List<FileStorage> ecores;

  @BeforeAll
  static void setUp() throws IOException {
    classpath =
        new CompileClasspath(new ClassPathResource("lsp/tools.vitruv.dsls.reactions.ide.jar"));
    xtendCompiler = new XtendUtilityCompiler(new UtilityMethodCompilerProperties());
    compiler = new UtilityMethodCompiler(classpath, xtendCompiler);
    try (InputStream in =
        UtilityMethodCompilerTest.class.getResourceAsStream("/utilitymethod/simulink.ecore")) {
      ecores =
          List.of(
              FileStorage.builder()
                  .id(1L)
                  .filename("simulink.ecore")
                  .data(in.readAllBytes())
                  .build());
    }
  }

  @AfterAll
  static void tearDown() {
    xtendCompiler.shutdown();
    classpath.cleanUp();
  }

  private static UtilityMethodDefinition getAllSubSystems(String body) {
    return new UtilityMethodDefinition(
        "getAllSubSystems",
        "All sub-systems contained in the model.",
        List.of(new Parameter("model", "SimulinkModel")),
        "List<SubSystem>",
        List.of(SIMULINK_IMPORT),
        body);
  }

  @Test
  void compilesAnXtendBodyToAStaticJavaMethod() {
    Result result =
        compiler.compile(
            List.of(getAllSubSystems("model.eAllContents.filter(SubSystem).toList")), ecores);

    assertThat(result.hasErrors()).as(result.errors().toString()).isFalse();
    assertThat(result.source())
        .startsWith("package tools.vitruv.methodologisttemplate.consistency.utils;")
        .contains("public final class Utility")
        .contains("public static List<SubSystem> getAllSubSystems(final SimulinkModel model)");
    assertThat(result.methods().get("getAllSubSystems"))
        .startsWith("/**")
        .contains("All sub-systems contained in the model.")
        .contains("public static List<SubSystem> getAllSubSystems(")
        .endsWith("}\n");
    assertThat(result.xtendDefinitions().get("getAllSubSystems"))
        .isEqualTo(
            "def static List<SubSystem> getAllSubSystems(SimulinkModel model) {\n"
                + "  model.eAllContents.filter(SubSystem).toList\n}");
  }

  @Test
  void reportsATypeErrorAtItsPositionInTheBody() {
    Result result =
        compiler.compile(
            List.of(getAllSubSystems("val all = model.eAllContents\nall.filter(SubSystm).toList")),
            ecores);

    assertThat(result.source()).doesNotContain("getAllSubSystems");
    List<UtilityMethodError> errors = result.errorsOf("getAllSubSystems");
    assertThat(errors).isNotEmpty();
    UtilityMethodError error =
        errors.stream().filter(UtilityMethodError::isError).findFirst().orElseThrow();
    assertThat(error.message()).contains("SubSystm");
    assertThat(error.line()).isEqualTo(2);
    assertThat(error.column()).isEqualTo(12);
  }

  @Test
  void reportsAnUnknownParameterTypeInTheSignature() {
    UtilityMethodDefinition method =
        new UtilityMethodDefinition(
            "nameOf",
            null,
            List.of(new Parameter("block", "Blok")),
            "String",
            List.of(SIMULINK_IMPORT),
            "block.name");

    Result result = compiler.compile(List.of(method), ecores);

    UtilityMethodError error = result.errorsOf("nameOf").get(0);
    assertThat(error.isError()).isTrue();
    assertThat(error.message()).startsWith("In the method signature: ").contains("Blok");
    assertThat(error.line()).isNull();
  }

  @Test
  void utilityMethodsCallEachOtherBySimpleName() {
    UtilityMethodDefinition count =
        new UtilityMethodDefinition(
            "countSubSystems",
            null,
            List.of(new Parameter("model", "SimulinkModel")),
            "int",
            List.of(SIMULINK_IMPORT),
            "getAllSubSystems(model).size");

    Result result =
        compiler.compile(
            List.of(count, getAllSubSystems("model.eAllContents.filter(SubSystem).toList")),
            ecores);

    assertThat(result.hasErrors()).as(result.errors().toString()).isFalse();
    assertThat(result.methods()).containsKeys("countSubSystems", "getAllSubSystems");
  }

  @Test
  void leavesOutUtilityMethodsThatCallAFailingOne() {
    UtilityMethodDefinition count =
        new UtilityMethodDefinition(
            "countSubSystems",
            null,
            List.of(new Parameter("model", "SimulinkModel")),
            "int",
            List.of(SIMULINK_IMPORT),
            "getAllSubSystems(model).size");
    UtilityMethodDefinition ok =
        new UtilityMethodDefinition("answer", null, List.of(), "int", List.of(), "42");

    Result result =
        compiler.compile(List.of(count, getAllSubSystems("model.unknownFeature"), ok), ecores);

    assertThat(result.failedUtilityMethods())
        .containsExactly("countSubSystems", "getAllSubSystems");
    assertThat(result.methods()).containsOnlyKeys("answer");
    assertThat(result.source()).contains("public static int answer()");
  }

  @Test
  void rejectsABodyThatEndsItsMethod() {
    UtilityMethodDefinition escaping =
        new UtilityMethodDefinition(
            "escape", null, List.of(), "int", List.of(), "1 }\n  def static void injected() {");

    Result result = compiler.compile(List.of(escaping), ecores);

    assertThat(result.errorsOf("escape"))
        .extracting(UtilityMethodError::message)
        .contains(XtendSourceStructureCheck.NOT_A_SINGLE_BODY);
    assertThat(result.source()).doesNotContain("injected");
  }

  @Test
  void rejectsAnnotationsSmuggledIntoTheBody() {
    UtilityMethodDefinition annotated =
        new UtilityMethodDefinition(
            "annotated",
            null,
            List.of(),
            "int",
            List.of(),
            "1 }\n  @org.eclipse.xtend.lib.annotations.Data static class X { int a\n");

    Result result = compiler.compile(List.of(annotated), ecores);

    assertThat(result.failedUtilityMethods()).containsExactly("annotated");
    assertThat(result.source()).doesNotContain("class X");
  }

  @Test
  void signatureProblemsAreReportedWithoutCompiling() {
    UtilityMethodDefinition bad =
        new UtilityMethodDefinition("def", null, List.of(), "int", List.of("java..util"), "1");

    Result result = compiler.compile(List.of(bad), ecores);

    assertThat(result.errorsOf("def"))
        .extracting(UtilityMethodError::message)
        .anyMatch(message -> message.contains("keyword"))
        .anyMatch(message -> message.contains("not a Java import"));
  }

  @Test
  void generatedJavaCompilesAgainstTheModelApiAndTheServerJar(@TempDir Path out)
      throws IOException {
    Result result =
        compiler.compile(
            List.of(getAllSubSystems("model.eAllContents.filter(SubSystem).toList")), ecores);
    CompileClasspath.Entries entries = classpath.forVsum(ecores);

    JavaSourceCompiler.Outcome outcome =
        JavaSourceCompiler.compile(
            Map.of(UtilityMethodClassGenerator.QUALIFIED_CLASS_NAME, result.source()),
            entries.paths(),
            out);

    assertThat(outcome.compiled()).as(outcome.errors().toString()).isTrue();
    assertThat(out.resolve("tools/vitruv/methodologisttemplate/consistency/utils/Utility.class"))
        .exists();
  }

  @Test
  void timesOutWithAClearMessage() {
    UtilityMethodCompilerProperties properties = new UtilityMethodCompilerProperties();
    properties.setTimeoutSeconds(0);
    XtendUtilityCompiler impatient = new XtendUtilityCompiler(properties);
    try {
      UtilityMethodCompiler timingOut = new UtilityMethodCompiler(classpath, impatient);
      List<UtilityMethodDefinition> methods =
          List.of(getAllSubSystems("model.eAllContents.toList.filter(SubSystem).toList"));
      assertThatThrownBy(() -> timingOut.compile(methods, ecores))
          .isInstanceOf(UtilityMethodCompilationException.class)
          .hasMessage(XtendUtilityCompiler.TIMEOUT_MESSAGE);
    } finally {
      impatient.shutdown();
    }
  }
}
