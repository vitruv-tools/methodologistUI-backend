package tools.vitruv.methodologist.vsum.utilitymethod;

import com.google.inject.Injector;
import jakarta.annotation.PreDestroy;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtend.core.XtendStandaloneSetup;
import org.eclipse.xtend.core.xtend.XtendFile;
import org.eclipse.xtext.common.types.access.ClasspathTypeProviderFactory;
import org.eclipse.xtext.diagnostics.Severity;
import org.eclipse.xtext.generator.GeneratorContext;
import org.eclipse.xtext.generator.GeneratorDelegate;
import org.eclipse.xtext.generator.IFileSystemAccess;
import org.eclipse.xtext.generator.InMemoryFileSystemAccess;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.resource.XtextResourceSet;
import org.eclipse.xtext.util.CancelIndicator;
import org.eclipse.xtext.validation.CheckMode;
import org.eclipse.xtext.validation.IResourceValidator;
import org.eclipse.xtext.validation.Issue;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Compiles the generated {@code Utility.xtend} to Java with the Xtend compiler.
 *
 * <p>Nothing the methodologist wrote is executed: the source is checked by {@link
 * XtendSourceStructureCheck} before Java is generated, so it cannot declare members or annotations
 * of its own, and the types it can use come only from the given classpath. Compilations run on a
 * bounded pool and are cancelled after {@link UtilityMethodCompilerProperties#getTimeoutSeconds()}.
 */
@Component
@EnableConfigurationProperties(UtilityMethodCompilerProperties.class)
public class XtendUtilityCompiler {

  /** Message of the problem reported when a compilation is cancelled. */
  static final String TIMEOUT_MESSAGE =
      "Compiling the utility methods took too long. Simplify the method or try again.";

  private static final String JAVA_FILE =
      UtilityMethodClassGenerator.QUALIFIED_CLASS_NAME.replace('.', '/') + ".java";

  private final UtilityMethodCompilerProperties properties;
  private final ExecutorService executor;
  private volatile Injector injector;

  /**
   * A problem the Xtend compiler or the structure check found.
   *
   * @param severity {@code ERROR} or {@code WARNING}
   * @param message what is wrong
   * @param line 1-based line in the Xtend source, or {@code null}
   * @param column 1-based column in the Xtend source, or {@code null}
   * @param utilityMethodName the utility method the problem belongs to, if the check knows it
   *     without a position
   */
  public record XtendIssue(
      String severity, String message, Integer line, Integer column, String utilityMethodName) {

    /** Whether the problem keeps the source from compiling. */
    public boolean isError() {
      return UtilityMethodClassGenerator.ERROR.equals(severity);
    }
  }

  /**
   * The outcome of a compilation.
   *
   * @param javaSource the generated {@code Utility.java}, or {@code null} if there are errors
   * @param issues the problems found
   */
  public record Output(String javaSource, List<XtendIssue> issues) {}

  /**
   * Creates the compiler.
   *
   * @param properties pool size and timeout
   */
  public XtendUtilityCompiler(UtilityMethodCompilerProperties properties) {
    this.properties = properties;
    AtomicInteger threadNumber = new AtomicInteger();
    this.executor =
        Executors.newFixedThreadPool(
            Math.max(1, properties.getThreads()),
            runnable -> {
              Thread thread =
                  new Thread(runnable, "utility-method-compiler-" + threadNumber.incrementAndGet());
              thread.setDaemon(true);
              return thread;
            });
  }

  /**
   * Compiles the generated Xtend source.
   *
   * @param generated the source and the position of every utility method's body in it
   * @param classpath the types the source can use
   * @return the Java source, or the problems that keep it from compiling
   * @throws UtilityMethodCompilationException if the compilation takes too long or fails for a
   *     reason other than a problem in the source
   */
  public Output compile(
      UtilityMethodClassGenerator.GeneratedXtend generated, CompileClasspath.Entries classpath) {
    AtomicBoolean cancelled = new AtomicBoolean();
    Future<Output> future = executor.submit(() -> doCompile(generated, classpath, cancelled::get));
    try {
      return future.get(properties.getTimeoutSeconds(), TimeUnit.SECONDS);
    } catch (TimeoutException e) {
      cancelled.set(true);
      future.cancel(true);
      throw new UtilityMethodCompilationException(TIMEOUT_MESSAGE, e);
    } catch (InterruptedException e) {
      cancelled.set(true);
      future.cancel(true);
      Thread.currentThread().interrupt();
      throw new UtilityMethodCompilationException("Compiling the utility methods was interrupted.");
    } catch (ExecutionException e) {
      throw new UtilityMethodCompilationException(
          "The utility methods cannot be compiled: " + e.getCause().getMessage(), e.getCause());
    }
  }

  /** Stops the compiler threads. */
  @PreDestroy
  public void shutdown() {
    executor.shutdownNow();
  }

  private Output doCompile(
      UtilityMethodClassGenerator.GeneratedXtend generated,
      CompileClasspath.Entries classpath,
      CancelIndicator cancelIndicator)
      throws IOException {
    Injector xtend = injector();
    try (URLClassLoader loader = classLoader(classpath)) {
      XtextResourceSet resourceSet = xtend.getInstance(XtextResourceSet.class);
      resourceSet.setClasspathURIContext(loader);
      resourceSet.addLoadOption(XtextResource.OPTION_RESOLVE_ALL, Boolean.TRUE);
      xtend.getInstance(ClasspathTypeProviderFactory.class).createTypeProvider(resourceSet);

      Resource resource =
          resourceSet.createResource(
              URI.createURI("synthetic:/" + UtilityMethodClassGenerator.XTEND_FILE_NAME));
      resource.load(
          new ByteArrayInputStream(generated.source().getBytes(StandardCharsets.UTF_8)), Map.of());

      if (!resource.getErrors().isEmpty() || resource.getContents().isEmpty()) {
        // Syntax errors: report them before checking a structure that may not have been parsed.
        return new Output(null, validate(resource, cancelIndicator));
      }
      List<XtendIssue> structureProblems =
          XtendSourceStructureCheck.check((XtendFile) resource.getContents().get(0), generated);
      if (!structureProblems.isEmpty()) {
        return new Output(null, structureProblems);
      }
      List<XtendIssue> issues = validate(resource, cancelIndicator);
      if (issues.stream().anyMatch(XtendIssue::isError)) {
        return new Output(null, issues);
      }

      InMemoryFileSystemAccess fileSystem = new InMemoryFileSystemAccess();
      GeneratorContext context = new GeneratorContext();
      context.setCancelIndicator(cancelIndicator);
      xtend.getInstance(GeneratorDelegate.class).generate(resource, fileSystem, context);
      CharSequence java =
          fileSystem.getTextFiles().get(IFileSystemAccess.DEFAULT_OUTPUT + JAVA_FILE);
      if (java == null) {
        throw new IllegalStateException("The Xtend compiler generated no " + JAVA_FILE);
      }
      return new Output(java.toString(), issues);
    }
  }

  private static List<XtendIssue> validate(Resource resource, CancelIndicator cancelIndicator) {
    IResourceValidator validator =
        ((XtextResource) resource).getResourceServiceProvider().getResourceValidator();
    List<Issue> issues = validator.validate(resource, CheckMode.ALL, cancelIndicator);
    return issues.stream()
        .filter(issue -> issue.getSeverity() != Severity.INFO)
        .filter(issue -> issue.getSeverity() != Severity.IGNORE)
        .map(
            issue ->
                new XtendIssue(
                    issue.getSeverity() == Severity.ERROR
                        ? UtilityMethodClassGenerator.ERROR
                        : UtilityMethodClassGenerator.WARNING,
                    issue.getMessage(),
                    issue.getLineNumber(),
                    issue.getColumn(),
                    null))
        .toList();
  }

  private Injector injector() {
    Injector result = injector;
    if (result == null) {
      synchronized (this) {
        result = injector;
        if (result == null) {
          result = new XtendStandaloneSetup().createInjectorAndDoEMFRegistration();
          injector = result;
        }
      }
    }
    return result;
  }

  /** Loads only the given entries; JDK types come from the platform class loader. */
  private static URLClassLoader classLoader(CompileClasspath.Entries classpath) {
    List<URL> urls = new ArrayList<>();
    for (Path path : classpath.paths()) {
      try {
        urls.add(path.toUri().toURL());
      } catch (MalformedURLException e) {
        throw new IllegalArgumentException("Invalid classpath entry " + path, e);
      }
    }
    return new URLClassLoader(urls.toArray(URL[]::new), ClassLoader.getPlatformClassLoader());
  }
}
