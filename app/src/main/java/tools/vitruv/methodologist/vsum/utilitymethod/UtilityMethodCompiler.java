package tools.vitruv.methodologist.vsum.utilitymethod;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.vitruv.methodologist.general.model.FileStorage;
import tools.vitruv.methodologist.vsum.utilitymethod.UtilityMethodClassGenerator.BodyRange;
import tools.vitruv.methodologist.vsum.utilitymethod.UtilityMethodClassGenerator.GeneratedXtend;
import tools.vitruv.methodologist.vsum.utilitymethod.UtilityMethodClassGenerator.Result;
import tools.vitruv.methodologist.vsum.utilitymethod.UtilityMethodClassGenerator.UtilityMethodError;
import tools.vitruv.methodologist.vsum.utilitymethod.XtendUtilityCompiler.XtendIssue;

/**
 * Compiles the utility methods of a VSUM into the Java utility class.
 *
 * <p>Utility methods that do not compile are left out of the class, so that the others stay usable:
 * after each compilation the failing utility methods are removed and the rest is compiled again.
 * Utility methods calling a removed one then fail as well.
 */
@Component
public class UtilityMethodCompiler {

  private static final String SIGNATURE_PREFIX = "In the method signature: ";

  private final CompileClasspath compileClasspath;
  private final XtendUtilityCompiler xtendCompiler;

  /**
   * Creates the compiler.
   *
   * @param compileClasspath the metamodel API and libraries the utility methods compile against
   * @param xtendCompiler the Xtend compiler
   */
  public UtilityMethodCompiler(
      CompileClasspath compileClasspath, XtendUtilityCompiler xtendCompiler) {
    this.compileClasspath = compileClasspath;
    this.xtendCompiler = xtendCompiler;
  }

  /**
   * Compiles the utility methods.
   *
   * @param utilityMethods the utility methods of the VSUM
   * @param ecores the VSUM's metamodel files
   * @return the Java class of the utility methods that compile and the problems of all of them
   * @throws UtilityMethodCompilationException if compiling fails for a reason other than a problem
   *     in the utility methods, for example a timeout
   */
  public Result compile(
      List<UtilityMethodDefinition> utilityMethods, Collection<FileStorage> ecores) {
    List<UtilityMethodError> errors = new ArrayList<>();
    Map<String, String> xtendDefinitions = new LinkedHashMap<>();
    List<UtilityMethodDefinition> candidates = new ArrayList<>();
    Set<String> names = new HashSet<>();
    for (UtilityMethodDefinition utilityMethod :
        utilityMethods.stream()
            .sorted(Comparator.comparing(UtilityMethodDefinition::name))
            .toList()) {
      xtendDefinitions.put(
          utilityMethod.name(), UtilityMethodClassGenerator.xtendDefinition(utilityMethod));
      List<String> problems = UtilityMethodClassGenerator.signatureProblems(utilityMethod);
      if (!names.add(utilityMethod.name().toLowerCase(Locale.ROOT))) {
        problems = new ArrayList<>(problems);
        problems.add("Another utility method is already named '" + utilityMethod.name() + "'.");
      }
      if (problems.isEmpty()) {
        candidates.add(utilityMethod);
      } else {
        problems.forEach(message -> errors.add(error(utilityMethod.name(), message, null, null)));
      }
    }

    CompileClasspath.Entries classpath = compileClasspath.forVsum(ecores);
    // Each round removes at least one utility method, plus a final round without failures.
    for (int round = 0; round <= utilityMethods.size(); round++) {
      GeneratedXtend generated = UtilityMethodClassGenerator.generate(candidates);
      XtendUtilityCompiler.Output output = xtendCompiler.compile(generated, classpath);
      List<UtilityMethodError> mapped = map(output.issues(), generated, candidates);
      Set<String> failed = new HashSet<>();
      mapped.stream()
          .filter(UtilityMethodError::isError)
          .forEach(error -> failed.add(error.utilityMethodName()));
      if (output.javaSource() != null && failed.isEmpty()) {
        errors.addAll(mapped);
        return new Result(
            output.javaSource(),
            javaMethods(output.javaSource(), candidates),
            xtendDefinitions,
            errors);
      }
      if (failed.isEmpty()) {
        throw new UtilityMethodCompilationException(
            "The utility methods cannot be compiled: " + summary(output.issues()));
      }
      mapped.stream()
          .filter(error -> failed.contains(error.utilityMethodName()))
          .forEach(errors::add);
      candidates.removeIf(utilityMethod -> failed.contains(utilityMethod.name()));
    }
    throw new UtilityMethodCompilationException("The utility methods cannot be compiled.");
  }

  /**
   * Assigns the compiler's problems to utility methods. Positions in a body become positions
   * relative to that body; errors in an import go to the utility methods declaring it; errors
   * anywhere else go to every utility method, because they keep the whole class from compiling.
   * Warnings outside the bodies are dropped.
   */
  private static List<UtilityMethodError> map(
      List<XtendIssue> issues, GeneratedXtend generated, List<UtilityMethodDefinition> candidates) {
    List<UtilityMethodError> errors = new ArrayList<>();
    for (XtendIssue issue : issues) {
      if (issue.utilityMethodName() != null) {
        errors.add(withSeverity(issue.utilityMethodName(), issue.message(), null, null, issue));
        continue;
      }
      Integer line = issue.line();
      BodyRange body =
          line == null
              ? null
              : generated.bodies().stream()
                  .filter(range -> range.containsBodyLine(line) || range.headerLine() == line)
                  .findFirst()
                  .orElse(null);
      if (body != null && body.containsBodyLine(line)) {
        errors.add(
            withSeverity(
                body.utilityMethodName(),
                issue.message(),
                line - body.firstBodyLine() + 1,
                issue.column(),
                issue));
      } else if (body != null) {
        errors.add(
            withSeverity(
                body.utilityMethodName(), SIGNATURE_PREFIX + issue.message(), null, null, issue));
      } else if (line != null && generated.importLines().containsKey(line) && issue.isError()) {
        // Warnings on imports, such as deprecated wildcard imports, are not worth reporting.
        generated
            .importLines()
            .get(line)
            .forEach(name -> errors.add(withSeverity(name, issue.message(), null, null, issue)));
      } else if (issue.isError()) {
        // Unused imports and the like are not worth reporting, real errors are.
        candidates.forEach(
            utilityMethod ->
                errors.add(withSeverity(utilityMethod.name(), issue.message(), null, null, issue)));
      }
    }
    return errors;
  }

  private static UtilityMethodError withSeverity(
      String utilityMethodName, String message, Integer line, Integer column, XtendIssue issue) {
    return new UtilityMethodError(utilityMethodName, message, line, column, issue.severity());
  }

  private static UtilityMethodError error(
      String utilityMethodName, String message, Integer line, Integer column) {
    return new UtilityMethodError(
        utilityMethodName, message, line, column, UtilityMethodClassGenerator.ERROR);
  }

  private static String summary(List<XtendIssue> issues) {
    return issues.stream()
        .map(XtendIssue::message)
        .limit(3)
        .reduce((a, b) -> a + "; " + b)
        .orElse("the Xtend compiler generated no Java");
  }

  private static Map<String, String> javaMethods(
      String javaSource, List<UtilityMethodDefinition> utilityMethods) {
    Map<String, String> methods = new LinkedHashMap<>();
    for (UtilityMethodDefinition utilityMethod : utilityMethods) {
      String method = JavaMethodExtractor.extract(javaSource, utilityMethod.name());
      if (method != null) {
        methods.put(utilityMethod.name(), method);
      }
    }
    return methods;
  }
}
