package tools.vitruv.methodologist.vsum.utilitymethod;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.vitruv.methodologist.vsum.utilitymethod.UtilityMethodClassGenerator.UtilityMethodError;
import tools.vitruv.methodologist.vsum.utilitymethod.UtilityMethodDefinition.Parameter;

class UtilityMethodClassGeneratorTest {

  private static final String MODEL = "tools.vitruv.methodologisttemplate.model.";

  /** Stand-ins for the interfaces EMF generates for simulink.ecore and library.ecore. */
  private static final Map<String, String> MODEL_STUBS =
      Map.of(
          MODEL + "simulink.SimulinkModel",
          "public interface SimulinkModel { java.util.List<Block> getContains();"
              + " String getName(); }",
          MODEL + "simulink.Block",
          "public interface Block { String getName(); int getPriority(); boolean isEnabled();"
              + " BlockKind getKind(); }",
          MODEL + "simulink.SubSystem",
          "public interface SubSystem extends Block { java.util.List<Block> getSubBlocks(); }",
          MODEL + "simulink.Gain",
          "public interface Gain extends Block { double getFactor(); }",
          MODEL + "simulink.BlockKind",
          "public enum BlockKind { CONTINUOUS, DISCRETE_TIME }",
          MODEL + "library.Block",
          "public interface Block { "
              + MODEL
              + "simulink.SimulinkModel getModel(); long getPages(); }",
          MODEL + "library.shelf.Shelf",
          "public interface Shelf extends "
              + MODEL
              + "library.Block { java.util.List<Book> getBooks(); }",
          MODEL + "library.shelf.Book",
          "public interface Book extends " + MODEL + "library.Block {}");

  /**
   * Builds a model with a cycle top -> inner -> deep -> top and runs some utility methods on it.
   */
  private static final String FIXTURE =
      """
      package fixture;

      import java.util.*;
      import tools.vitruv.methodologisttemplate.consistency.utils.Utility;
      import tools.vitruv.methodologisttemplate.model.simulink.*;

      public class Fixture {
        static final class Sub implements SubSystem {
          final String name; final List<Block> subBlocks = new ArrayList<>(); final int priority;
          Sub(String name, int priority) { this.name = name; this.priority = priority; }
          public String getName() { return name; }
          public List<Block> getSubBlocks() { return subBlocks; }
          public int getPriority() { return priority; }
          public boolean isEnabled() { return true; }
          public BlockKind getKind() { return BlockKind.DISCRETE_TIME; }
          public String toString() { return name; }
        }
        static final class G implements Gain {
          final String name; final double factor;
          G(String name, double factor) { this.name = name; this.factor = factor; }
          public String getName() { return name; }
          public double getFactor() { return factor; }
          public int getPriority() { return 1; }
          public boolean isEnabled() { return false; }
          public BlockKind getKind() { return BlockKind.CONTINUOUS; }
          public String toString() { return name; }
        }

        public static List<String> run() {
          Sub top = new Sub("top", 3);
          Sub inner = new Sub("inner", 2);
          Sub deep = new Sub("deep", 5);
          top.subBlocks.addAll(List.of(inner, new G("g1", 2)));
          inner.subBlocks.addAll(List.of(deep, new G("g2", 7.5)));
          deep.subBlocks.add(top);
          List<Block> contents = List.of(top, new G("g0", 3));
          SimulinkModel model = new SimulinkModel() {
            public List<Block> getContains() { return contents; }
            public String getName() { return "model"; }
          };
          return List.of(
              String.valueOf(Utility.getAllSubSystems(model)),
              String.valueOf(Utility.countEnabled(model)),
              String.valueOf(Utility.upperNames(model)),
              String.valueOf(Utility.maxGain(List.of(new G("a", 1), new G("b", 4.5), top))),
              String.valueOf(Utility.oddNumbers()),
              String.valueOf(Utility.sortedByPriority(List.of(deep, top, inner))),
              String.valueOf(Utility.shout("hello")),
              String.valueOf(Utility.describe(top)));
        }
      }
      """;

  private static EcoreTypeIndex index;

  @TempDir Path tempDir;

  @BeforeAll
  static void loadMetamodels() throws IOException {
    index = EcoreTypeIndex.of(List.of(ecore("simulink.ecore"), ecore("library.ecore")));
  }

  @Test
  void generate_translatesTheSubSystemClosureExample_andTheCodeRuns() throws Exception {
    UtilityMethodClassGenerator.Result result =
        UtilityMethodClassGenerator.generate(utilityMethods(), index);

    assertThat(result.errors()).isEmpty();
    assertThat(result.methods().get("getAllSubSystems"))
        .contains("public static List<SubSystem> getAllSubSystems(SimulinkModel model) {")
        .contains("Utility.<SubSystem>oclClosure(")
        .contains(".filter(SubSystem.class::isInstance)");
    assertThat(result.source())
        .startsWith("package tools.vitruv.methodologisttemplate.consistency.utils;")
        .contains("import tools.vitruv.methodologisttemplate.model.simulink.Block;")
        .contains(
            "pagesOf(tools.vitruv.methodologisttemplate.model.library.Block book, Block block)");

    List<String> output = compileAndRun(result.source());

    assertThat(output)
        .containsExactly(
            "[top, inner, deep]",
            "6",
            "[TOP, G0]",
            "4.5",
            "[1, 3, 5, 7]",
            "[inner, top, deep]",
            "HEL!",
            "top (3)");
  }

  @Test
  void generate_reportsUnknownFeatureWithPositionAndKeepsOtherUtilityMethods() {
    List<UtilityMethodDefinition> utilityMethods = new ArrayList<>(utilityMethods());
    utilityMethods.add(utilityMethod("broken", "Integer", "model.contians->size()", model()));

    UtilityMethodClassGenerator.Result result =
        UtilityMethodClassGenerator.generate(utilityMethods, index);

    assertThat(result.errors())
        .singleElement()
        .satisfies(
            error -> {
              assertThat(error.utilityMethodName()).isEqualTo("broken");
              assertThat(error.message()).contains("no feature 'contians'").contains("contains");
              assertThat(error.line()).isEqualTo(1);
              assertThat(error.column()).isEqualTo(7);
            });
    assertThat(result.methods()).containsKey("getAllSubSystems").doesNotContainKey("broken");
    assertThat(result.source()).doesNotContain("broken(");
  }

  @Test
  void generate_rejectsReturnTypeMismatch() {
    UtilityMethodError error =
        singleError(
            utilityMethod("names", "Sequence(simulink::SubSystem)", "model.contains", model()));

    assertThat(error.message())
        .contains("OrderedSet(simulink::Block)")
        .contains("does not conform to the return type Sequence(simulink::SubSystem)");
  }

  @Test
  void generate_rejectsAmbiguousSimpleTypeName_unlessImported() {
    UtilityMethodDefinition ambiguous =
        utilityMethod("first", "Block", "model.contains->first()", model());
    assertThat(singleError(ambiguous).message())
        .contains("Ambiguous type 'Block'")
        .contains("library::Block")
        .contains("simulink::Block");

    UtilityMethodDefinition imported =
        new UtilityMethodDefinition(
            "first",
            null,
            List.of(new Parameter("model", "SimulinkModel")),
            "Block",
            List.of("simulink::*"),
            "model.contains->first()");
    assertThat(UtilityMethodClassGenerator.generate(List.of(imported), index).errors()).isEmpty();
  }

  @Test
  void generate_reportsParseErrorPosition() {
    UtilityMethodError error =
        singleError(utilityMethod("broken", "Integer", "model.contains\n  ->size(", model()));

    assertThat(error.message()).contains("end of the expression");
    assertThat(error.line()).isEqualTo(2);
  }

  @Test
  void generate_rejectsInvalidSignatures() {
    assertThat(singleError(utilityMethod("oclSize", "Integer", "1", List.of())).message())
        .contains("reserved");
    assertThat(singleError(utilityMethod("my utilityMethod", "Integer", "1", List.of())).message())
        .contains("not a valid Java method name");
    assertThat(
            singleError(
                    utilityMethod(
                        "twice",
                        "Integer",
                        "1",
                        List.of(new Parameter("a", "Integer"), new Parameter("a", "Integer"))))
                .message())
        .contains("declared twice");
    assertThat(
            singleError(utilityMethod("unknownType", "simulink::Nope", "null", List.of()))
                .message())
        .contains("Return type")
        .contains("has no type 'Nope'");
  }

  @Test
  void generate_explainsUnsupportedAllInstances() {
    UtilityMethodError error =
        singleError(
            utilityMethod("all", "Integer", "simulink::Block.allInstances()->size()", List.of()));

    assertThat(error.message()).contains("allInstances() is not supported");
  }

  @Test
  void generate_resolvesSubpackagesAndCrossFileReferences() {
    UtilityMethodDefinition pages =
        utilityMethod(
            "totalPages",
            "Integer",
            "shelf.books->collect(b | b.pages)->sum() + shelf.model.contains->size()",
            List.of(new Parameter("shelf", "library::shelf::Shelf")));

    UtilityMethodClassGenerator.Result result =
        UtilityMethodClassGenerator.generate(List.of(pages), index);

    assertThat(result.errors()).isEmpty();
    assertThat(result.methods().get("totalPages"))
        .contains("Math.toIntExact(b.getPages())")
        .contains("shelf.getModel().getContains().size()");
  }

  // ── fixtures ─────────────────────────────────────────────────────────────

  private static List<UtilityMethodDefinition> utilityMethods() {
    return List.of(
        new UtilityMethodDefinition(
            "getAllSubSystems",
            "Returns all subsystems of a model, including nested ones.",
            model(),
            "Sequence(simulink::SubSystem)",
            List.of("simulink::SubSystem"),
            "model.contains->select(b | b.oclIsKindOf(SubSystem))\n"
                + "  ->closure(s | s.subBlocks->selectByKind(SubSystem))\n"
                + "  ->asSequence()"),
        utilityMethod(
            "countEnabled",
            "Integer",
            "let all = getAllSubSystems(model) in\n"
                + "all.subBlocks->select(enabled)->size()\n"
                + "  + all->select(s | s.kind = simulink::BlockKind::discreteTime)->size()",
            model()),
        utilityMethod(
            "upperNames",
            "Set(String)",
            "model.contains->collect(b | b.name.toUpper())->asSet()",
            model()),
        utilityMethod(
            "maxGain",
            "Real",
            "blocks->selectByKind(simulink::Gain)"
                + "->iterate(g; acc : Real = 0 | if g.factor > acc then g.factor else acc endif)",
            List.of(new Parameter("blocks", "Sequence(simulink::Block)"))),
        utilityMethod(
            "oddNumbers",
            "Sequence(Integer)",
            "Sequence{1..5}->select(i | i mod 2 = 1)->including(7)",
            List.of()),
        utilityMethod(
            "sortedByPriority",
            "Sequence(simulink::Block)",
            "blocks->sortedBy(b | b.priority)",
            List.of(new Parameter("blocks", "Sequence(simulink::Block)"))),
        utilityMethod(
            "shout",
            "String",
            "if s.size() > 2 then s.toUpper().substring(1, 3).concat('!') else s endif",
            List.of(new Parameter("s", "String"))),
        utilityMethod(
            "describe",
            "String",
            "if block.oclIsUndefined() then 'none'"
                + " else block.name + ' (' + block.priority.toString() + ')' endif",
            List.of(new Parameter("block", "simulink::Block"))),
        utilityMethod(
            "allBlocks",
            "Sequence(simulink::Block)",
            "getAllSubSystems(model)->union(model.contains->asSequence())",
            model()),
        utilityMethod(
            "namesAreUnique",
            "Boolean",
            "model.contains->isUnique(b | b.name)"
                + " and model.contains->forAll(a, b | a = b or a.name <> b.name)"
                + " and model.contains->exists(b | b.oclIsTypeOf(simulink::Gain)) = false",
            model()),
        utilityMethod(
            "firstSubBlockName",
            "String",
            "let first = sub.subBlocks->first() in"
                + " if sub.subBlocks->isEmpty() then '' else first.name endif",
            List.of(new Parameter("sub", "simulink::SubSystem"))),
        utilityMethod(
            "highPriority",
            "Integer",
            "model.contains->select(b | let p = b.priority in p > 1 and p <= 10)->size()"
                + " + model.contains->reverse()->size()",
            model()),
        utilityMethod("kinds", "Bag(simulink::BlockKind)", "model.contains.kind", model()),
        utilityMethod(
            "pagesOf",
            "Integer",
            "book.pages + block.priority",
            List.of(
                new Parameter("book", "library::Block"),
                new Parameter("block", "simulink::Block"))),
        utilityMethod(
            "average",
            "Real",
            "gains->collect(g | g.factor)->avg() / gains->size()",
            List.of(new Parameter("gains", "Set(simulink::Gain)"))));
  }

  private static List<Parameter> model() {
    return List.of(new Parameter("model", "simulink::SimulinkModel"));
  }

  private static UtilityMethodDefinition utilityMethod(
      String name, String returnType, String body, List<Parameter> parameters) {
    return new UtilityMethodDefinition(name, null, parameters, returnType, List.of(), body);
  }

  private static UtilityMethodError singleError(UtilityMethodDefinition utilityMethod) {
    List<UtilityMethodError> errors =
        UtilityMethodClassGenerator.generate(List.of(utilityMethod), index).errors();
    assertThat(errors).hasSize(1);
    return errors.get(0);
  }

  private static EcoreTypeIndex.EcoreFile ecore(String name) throws IOException {
    try (InputStream in =
        UtilityMethodClassGeneratorTest.class.getResourceAsStream("/utilitymethod/" + name)) {
      return new EcoreTypeIndex.EcoreFile(name, in.readAllBytes());
    }
  }

  /** Compiles the generated class with the model stubs and the fixture, then runs it. */
  @SuppressWarnings("unchecked") // Fixture.run() returns List<String>
  private List<String> compileAndRun(String generatedSource) throws Exception {
    Map<String, String> sources = new HashMap<>(MODEL_STUBS);
    sources.put(UtilityMethodClassGenerator.QUALIFIED_CLASS_NAME, generatedSource);
    sources.put("fixture.Fixture", FIXTURE);
    Path emf = EmfApiStubs.compile(tempDir.resolve("emf"));
    Path classes = tempDir.resolve("classes");

    List<String> errors =
        EmfApiStubs.compile(sources, List.of(emf), classes, "-Xlint:all", "-Werror");

    assertThat(errors).as("generated code compiles:\n%s", generatedSource).isEmpty();
    try (URLClassLoader loader =
        new URLClassLoader(
            new URL[] {classes.toUri().toURL(), emf.toUri().toURL()},
            getClass().getClassLoader())) {
      return (List<String>) loader.loadClass("fixture.Fixture").getMethod("run").invoke(null);
    }
  }
}
