package tools.vitruv.methodologist.vsum.utilitymethod;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/**
 * The part of the EMF API that generated model code and the utility method class use, compiled for
 * tests. The backend does not depend on EMF, and the reactions language server jar that provides it
 * at runtime is not part of the repository.
 */
public final class EmfApiStubs {

  private static final Map<String, String> STUBS =
      Map.ofEntries(
          Map.entry(
              "org.eclipse.emf.ecore.EObject", "public interface EObject { EClass eClass(); }"),
          Map.entry("org.eclipse.emf.ecore.EModelElement", "public interface EModelElement {}"),
          Map.entry(
              "org.eclipse.emf.ecore.EClassifier",
              "public interface EClassifier extends EModelElement { String getName(); }"),
          Map.entry(
              "org.eclipse.emf.ecore.EClass",
              "public interface EClass extends EClassifier { EPackage getEPackage(); }"),
          Map.entry(
              "org.eclipse.emf.ecore.EDataType",
              "public interface EDataType extends EClassifier {}"),
          Map.entry("org.eclipse.emf.ecore.EEnum", "public interface EEnum extends EDataType {}"),
          Map.entry(
              "org.eclipse.emf.ecore.EStructuralFeature",
              "public interface EStructuralFeature extends EModelElement {}"),
          Map.entry(
              "org.eclipse.emf.ecore.EAttribute",
              "public interface EAttribute extends EStructuralFeature {}"),
          Map.entry(
              "org.eclipse.emf.ecore.EReference",
              "public interface EReference extends EStructuralFeature {}"),
          Map.entry(
              "org.eclipse.emf.ecore.EPackage",
              "public interface EPackage extends EModelElement { String getNsURI(); }"),
          Map.entry(
              "org.eclipse.emf.ecore.EFactory",
              "public interface EFactory extends EModelElement {}"),
          Map.entry(
              "org.eclipse.emf.common.util.EList",
              "public interface EList<E> extends java.util.List<E> {}"),
          Map.entry(
              "org.eclipse.emf.common.util.Enumerator",
              "public interface Enumerator {"
                  + " int getValue(); String getName(); String getLiteral(); }"));

  private EmfApiStubs() {}

  /**
   * Compiles the stubs.
   *
   * @param outputDir the directory to compile into
   * @return {@code outputDir}, to put on a classpath
   */
  public static Path compile(Path outputDir) throws IOException {
    List<String> errors = compile(STUBS, List.of(), outputDir);
    assertThat(errors).as("EMF stubs compile").isEmpty();
    return outputDir;
  }

  /**
   * Compiles sources keyed by the qualified name of their type.
   *
   * @param options additional javac options, e.g. {@code -Werror}
   * @return the compiler errors, empty if the sources compiled
   */
  public static List<String> compile(
      Map<String, String> sources, List<Path> classpath, Path out, String... options)
      throws IOException {
    Path sourceDir = Files.createTempDirectory(out.toAbsolutePath().getParent(), "sources");
    List<Path> files = new ArrayList<>();
    for (Map.Entry<String, String> source : sources.entrySet()) {
      String name = source.getKey();
      Path file = sourceDir.resolve(name.replace('.', '/') + ".java");
      Files.createDirectories(file.getParent());
      String content = source.getValue();
      if (!content.startsWith("package ")) {
        content = "package " + name.substring(0, name.lastIndexOf('.')) + ";\n" + content;
      }
      files.add(Files.writeString(file, content));
    }
    Files.createDirectories(out);
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    try (StandardJavaFileManager fileManager =
        compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
      List<String> javacOptions = new ArrayList<>(List.of("-proc:none", "-d", out.toString()));
      javacOptions.addAll(List.of(options));
      if (!classpath.isEmpty()) {
        javacOptions.add("-classpath");
        javacOptions.add(
            classpath.stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator)));
      }
      compiler
          .getTask(
              null,
              fileManager,
              diagnostics,
              javacOptions,
              null,
              fileManager.getJavaFileObjectsFromPaths(files))
          .call();
    }
    return diagnostics.getDiagnostics().stream()
        .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
        .map(Object::toString)
        .toList();
  }
}
