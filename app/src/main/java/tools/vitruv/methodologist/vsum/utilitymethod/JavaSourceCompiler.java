package tools.vitruv.methodologist.vsum.utilitymethod;

import java.io.File;
import java.io.IOException;
import java.net.URI;
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
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/**
 * Compiles in-memory Java sources with the JDK's compiler, for the class files that the Xtend
 * compiler and the reactions editor's language server resolve Java types from.
 */
public final class JavaSourceCompiler {

  /** Class file version of the language server and of VSUM builds. */
  public static final String RELEASE = "17";

  /**
   * The outcome of a compilation.
   *
   * @param compiled whether all sources compiled
   * @param errors the first error messages if they did not
   */
  public record Outcome(boolean compiled, List<String> errors) {}

  private JavaSourceCompiler() {}

  /** Whether the running JVM provides a Java compiler, i.e. whether it is a JDK. */
  public static boolean isAvailable() {
    return ToolProvider.getSystemJavaCompiler() != null;
  }

  /**
   * Compiles the sources into {@code outputDir}.
   *
   * @param sources the sources, keyed by the qualified name of their type
   * @param classpath the classpath to compile against
   * @param outputDir the directory the class files are written to; created if missing
   * @return whether the sources compiled, and why not
   * @throws IOException if the output directory cannot be created
   * @throws IllegalStateException if the JVM has no Java compiler
   */
  public static Outcome compile(Map<String, String> sources, List<Path> classpath, Path outputDir)
      throws IOException {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    if (compiler == null) {
      throw new IllegalStateException("No Java compiler available; the backend must run on a JDK.");
    }
    Files.createDirectories(outputDir);
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    List<String> options =
        List.of(
            "-proc:none",
            "-nowarn",
            "--release",
            RELEASE,
            "-classpath",
            classpath.stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator)),
            "-d",
            outputDir.toString());
    List<JavaFileObject> units = new ArrayList<>();
    sources.forEach((name, source) -> units.add(new SourceUnit(name, source)));
    boolean compiled;
    try (StandardJavaFileManager fileManager =
        compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
      compiled = compiler.getTask(null, fileManager, diagnostics, options, null, units).call();
    }
    List<String> errors =
        diagnostics.getDiagnostics().stream()
            .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
            .map(d -> d.getMessage(null))
            .limit(5)
            .toList();
    return new Outcome(compiled, errors);
  }

  /** An in-memory source file. */
  private static final class SourceUnit extends SimpleJavaFileObject {
    private final String source;

    SourceUnit(String qualifiedName, String source) {
      super(
          URI.create("string:///" + qualifiedName.replace('.', '/') + Kind.SOURCE.extension),
          Kind.SOURCE);
      this.source = source;
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
      return source;
    }
  }
}
