package tools.vitruv.methodologist.vsum.helper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.vitruv.methodologist.vsum.helper.HelperClassGenerator.HelperError;
import tools.vitruv.methodologist.vsum.helper.HelperDefinition.Parameter;

class HelperClassGeneratorTest {

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

  /** Builds a model with a cycle top -> inner -> deep -> top and runs some helpers on it. */
  private static final String FIXTURE =
      """
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
    HelperClassGenerator.Result result = HelperClassGenerator.generate(helpers(), index);

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
  void generate_reportsUnknownFeatureWithPositionAndKeepsOtherHelpers() {
    List<HelperDefinition> helpers = new ArrayList<>(helpers());
    helpers.add(helper("broken", "Integer", "model.contians->size()", model()));

    HelperClassGenerator.Result result = HelperClassGenerator.generate(helpers, index);

    assertThat(result.errors())
        .singleElement()
        .satisfies(
            error -> {
              assertThat(error.helperName()).isEqualTo("broken");
              assertThat(error.message()).contains("no feature 'contians'").contains("contains");
              assertThat(error.line()).isEqualTo(1);
              assertThat(error.column()).isEqualTo(7);
            });
    assertThat(result.methods()).containsKey("getAllSubSystems").doesNotContainKey("broken");
    assertThat(result.source()).doesNotContain("broken(");
  }

  @Test
  void generate_rejectsReturnTypeMismatch() {
    HelperError error =
        singleError(helper("names", "Sequence(simulink::SubSystem)", "model.contains", model()));

    assertThat(error.message())
        .contains("OrderedSet(simulink::Block)")
        .contains("does not conform to the return type Sequence(simulink::SubSystem)");
  }

  @Test
  void generate_rejectsAmbiguousSimpleTypeName_unlessImported() {
    HelperDefinition ambiguous = helper("first", "Block", "model.contains->first()", model());
    assertThat(singleError(ambiguous).message())
        .contains("Ambiguous type 'Block'")
        .contains("library::Block")
        .contains("simulink::Block");

    HelperDefinition imported =
        new HelperDefinition(
            "first",
            null,
            List.of(new Parameter("model", "SimulinkModel")),
            "Block",
            List.of("simulink::*"),
            "model.contains->first()");
    assertThat(HelperClassGenerator.generate(List.of(imported), index).errors()).isEmpty();
  }

  @Test
  void generate_reportsParseErrorPosition() {
    HelperError error =
        singleError(helper("broken", "Integer", "model.contains\n  ->size(", model()));

    assertThat(error.message()).contains("end of the expression");
    assertThat(error.line()).isEqualTo(2);
  }

  @Test
  void generate_rejectsInvalidSignatures() {
    assertThat(singleError(helper("oclSize", "Integer", "1", List.of())).message())
        .contains("reserved");
    assertThat(singleError(helper("my helper", "Integer", "1", List.of())).message())
        .contains("not a valid Java method name");
    assertThat(
            singleError(
                    helper(
                        "twice",
                        "Integer",
                        "1",
                        List.of(new Parameter("a", "Integer"), new Parameter("a", "Integer"))))
                .message())
        .contains("declared twice");
    assertThat(singleError(helper("unknownType", "simulink::Nope", "null", List.of())).message())
        .contains("Return type")
        .contains("has no type 'Nope'");
  }

  @Test
  void generate_explainsUnsupportedAllInstances() {
    HelperError error =
        singleError(helper("all", "Integer", "simulink::Block.allInstances()->size()", List.of()));

    assertThat(error.message()).contains("allInstances() is not supported");
  }

  @Test
  void generate_resolvesSubpackagesAndCrossFileReferences() {
    HelperDefinition pages =
        helper(
            "totalPages",
            "Integer",
            "shelf.books->collect(b | b.pages)->sum() + shelf.model.contains->size()",
            List.of(new Parameter("shelf", "library::shelf::Shelf")));

    HelperClassGenerator.Result result = HelperClassGenerator.generate(List.of(pages), index);

    assertThat(result.errors()).isEmpty();
    assertThat(result.methods().get("totalPages"))
        .contains("Math.toIntExact(b.getPages())")
        .contains("shelf.getModel().getContains().size()");
  }

  // ── fixtures ─────────────────────────────────────────────────────────────

  private static List<HelperDefinition> helpers() {
    return List.of(
        new HelperDefinition(
            "getAllSubSystems",
            "Returns all subsystems of a model, including nested ones.",
            model(),
            "Sequence(simulink::SubSystem)",
            List.of("simulink::SubSystem"),
            "model.contains->select(b | b.oclIsKindOf(SubSystem))\n"
                + "  ->closure(s | s.subBlocks->selectByKind(SubSystem))\n"
                + "  ->asSequence()"),
        helper(
            "countEnabled",
            "Integer",
            "let all = getAllSubSystems(model) in\n"
                + "all.subBlocks->select(enabled)->size()\n"
                + "  + all->select(s | s.kind = simulink::BlockKind::discreteTime)->size()",
            model()),
        helper(
            "upperNames",
            "Set(String)",
            "model.contains->collect(b | b.name.toUpper())->asSet()",
            model()),
        helper(
            "maxGain",
            "Real",
            "blocks->selectByKind(simulink::Gain)"
                + "->iterate(g; acc : Real = 0 | if g.factor > acc then g.factor else acc endif)",
            List.of(new Parameter("blocks", "Sequence(simulink::Block)"))),
        helper(
            "oddNumbers",
            "Sequence(Integer)",
            "Sequence{1..5}->select(i | i mod 2 = 1)->including(7)",
            List.of()),
        helper(
            "sortedByPriority",
            "Sequence(simulink::Block)",
            "blocks->sortedBy(b | b.priority)",
            List.of(new Parameter("blocks", "Sequence(simulink::Block)"))),
        helper(
            "shout",
            "String",
            "if s.size() > 2 then s.toUpper().substring(1, 3).concat('!') else s endif",
            List.of(new Parameter("s", "String"))),
        helper(
            "describe",
            "String",
            "if block.oclIsUndefined() then 'none'"
                + " else block.name + ' (' + block.priority.toString() + ')' endif",
            List.of(new Parameter("block", "simulink::Block"))),
        helper(
            "allBlocks",
            "Sequence(simulink::Block)",
            "getAllSubSystems(model)->union(model.contains->asSequence())",
            model()),
        helper(
            "namesAreUnique",
            "Boolean",
            "model.contains->isUnique(b | b.name)"
                + " and model.contains->forAll(a, b | a = b or a.name <> b.name)"
                + " and model.contains->exists(b | b.oclIsTypeOf(simulink::Gain)) = false",
            model()),
        helper(
            "firstSubBlockName",
            "String",
            "let first = sub.subBlocks->first() in"
                + " if sub.subBlocks->isEmpty() then '' else first.name endif",
            List.of(new Parameter("sub", "simulink::SubSystem"))),
        helper(
            "highPriority",
            "Integer",
            "model.contains->select(b | let p = b.priority in p > 1 and p <= 10)->size()"
                + " + model.contains->reverse()->size()",
            model()),
        helper("kinds", "Bag(simulink::BlockKind)", "model.contains.kind", model()),
        helper(
            "pagesOf",
            "Integer",
            "book.pages + block.priority",
            List.of(
                new Parameter("book", "library::Block"),
                new Parameter("block", "simulink::Block"))),
        helper(
            "average",
            "Real",
            "gains->collect(g | g.factor)->avg() / gains->size()",
            List.of(new Parameter("gains", "Set(simulink::Gain)"))));
  }

  private static List<Parameter> model() {
    return List.of(new Parameter("model", "simulink::SimulinkModel"));
  }

  private static HelperDefinition helper(
      String name, String returnType, String body, List<Parameter> parameters) {
    return new HelperDefinition(name, null, parameters, returnType, List.of(), body);
  }

  private static HelperError singleError(HelperDefinition helper) {
    List<HelperError> errors = HelperClassGenerator.generate(List.of(helper), index).errors();
    assertThat(errors).hasSize(1);
    return errors.get(0);
  }

  private static EcoreTypeIndex.EcoreFile ecore(String name) throws IOException {
    try (InputStream in = HelperClassGeneratorTest.class.getResourceAsStream("/helper/" + name)) {
      return new EcoreTypeIndex.EcoreFile(name, in.readAllBytes());
    }
  }

  /**
   * Compiles the generated class with the model stubs and the fixture, then runs {@code
   * Fixture.run()}. The EObject stub stands in for EMF, which the backend does not depend on.
   */
  @SuppressWarnings("unchecked")
  private List<String> compileAndRun(String generatedSource) throws Exception {
    Path sources = tempDir.resolve("src");
    Path classes = tempDir.resolve("classes");
    Files.createDirectories(classes);
    List<Path> files = new ArrayList<>();
    for (Map.Entry<String, String> stub : MODEL_STUBS.entrySet()) {
      files.add(writeSource(sources, stub.getKey(), stub.getValue()));
    }
    files.add(
        writeSource(
            sources,
            "org.eclipse.emf.ecore.EObject",
            "public interface EObject { EClass eClass(); }"));
    files.add(
        writeSource(
            sources,
            "org.eclipse.emf.ecore.EClass",
            "public interface EClass { String getName(); EPackage getEPackage(); }"));
    files.add(
        writeSource(
            sources,
            "org.eclipse.emf.ecore.EPackage",
            "public interface EPackage { String getNsURI(); }"));
    files.add(writeFile(sources.resolve(HelperClassGenerator.FILE_NAME), generatedSource));
    files.add(writeFile(sources.resolve("Fixture.java"), FIXTURE));

    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    try (StandardJavaFileManager fileManager =
        compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
      boolean compiled =
          compiler
              .getTask(
                  null,
                  fileManager,
                  diagnostics,
                  List.of("-d", classes.toString(), "-Xlint:all", "-Werror"),
                  null,
                  fileManager.getJavaFileObjectsFromPaths(files))
              .call();
      assertThat(compiled)
          .as("generated code compiles:\n%s\n%s", diagnostics.getDiagnostics(), generatedSource)
          .isTrue();
    }
    try (URLClassLoader loader =
        new URLClassLoader(new URL[] {classes.toUri().toURL()}, getClass().getClassLoader())) {
      Method run = loader.loadClass("Fixture").getMethod("run");
      return (List<String>) run.invoke(null);
    }
  }

  private static Path writeSource(Path root, String qualifiedName, String body) throws IOException {
    int dot = qualifiedName.lastIndexOf('.');
    String pkg = qualifiedName.substring(0, dot);
    Path file = root.resolve(qualifiedName.replace('.', '/') + ".java");
    return writeFile(file, "package " + pkg + ";\n" + body + "\n");
  }

  private static Path writeFile(Path file, String content) throws IOException {
    Files.createDirectories(file.getParent());
    return Files.writeString(file, content);
  }
}
