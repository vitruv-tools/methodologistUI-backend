package tools.vitruv.methodologist.vsum.utilitymethod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Generates the Xtend utility class that contains all utility methods of a VSUM as {@code def
 * static} methods. The Xtend compiler turns it into the Java class that every reaction and routine
 * can call.
 *
 * <p>Every body is copied verbatim onto lines of its own, so that a position the Xtend compiler
 * reports in the class maps back to the body by a line offset only (see {@link BodyRange}).
 */
public final class UtilityMethodClassGenerator {

  /** Package of the generated class, inside the consistency module of the VSUM project. */
  public static final String PACKAGE_NAME = "tools.vitruv.methodologisttemplate.consistency.utils";

  /** Simple name of the generated class. */
  public static final String CLASS_NAME = "Utility";

  /** Fully qualified name of the generated class, as imported by reactions. */
  public static final String QUALIFIED_CLASS_NAME = PACKAGE_NAME + "." + CLASS_NAME;

  /** File name of the generated Java class. */
  public static final String FILE_NAME = CLASS_NAME + ".java";

  /** File name of the generated Xtend class. */
  public static final String XTEND_FILE_NAME = CLASS_NAME + ".xtend";

  /** Severity of a problem that keeps a utility method from compiling. */
  public static final String ERROR = "ERROR";

  /** Severity of a problem that is shown but does not keep a utility method from compiling. */
  public static final String WARNING = "WARNING";

  /** Maximum length of a utility method body. */
  public static final int MAX_BODY_LENGTH = 20_000;

  /** Maximum number of imports of a utility method. */
  public static final int MAX_IMPORTS = 50;

  /** Maximum length of an import. */
  public static final int MAX_IMPORT_LENGTH = 255;

  /** Words of Xtend that are not Java keywords but cannot name a method or parameter. */
  private static final Set<String> XTEND_KEYWORDS =
      Set.of(
          "def",
          "val",
          "var",
          "extension",
          "dispatch",
          "typeof",
          "it",
          "create",
          "override",
          "as",
          "annotation",
          "self");

  /**
   * Collection types every utility method can use by their simple name, as Xtend only imports
   * {@code java.lang} implicitly. An import of the same simple name replaces them.
   */
  static final List<String> DEFAULT_IMPORTS =
      List.of("java.util.Collection", "java.util.List", "java.util.Map", "java.util.Set");

  private static final String INDENT = "  ";

  /**
   * A problem of a utility method.
   *
   * @param utilityMethodName the utility method, or {@code null} if the problem concerns all of
   *     them
   * @param message what is wrong
   * @param line 1-based line in the utility method's body, or {@code null} if the problem is not in
   *     the body
   * @param column 1-based column in the body, or {@code null}
   * @param severity {@link #ERROR} or {@link #WARNING}
   */
  public record UtilityMethodError(
      String utilityMethodName, String message, Integer line, Integer column, String severity) {

    /** Whether the problem keeps the utility method from compiling. */
    public boolean isError() {
      return ERROR.equals(severity);
    }
  }

  /**
   * The compiled utility class.
   *
   * @param source the complete Java source; utility methods with errors are left out
   * @param methods the Java source of each compiled utility method, by utility method name
   * @param xtendDefinitions the Xtend definition of each utility method, by utility method name
   * @param errors the problems found; utility methods with a problem of severity {@link #ERROR} are
   *     left out of {@code source}
   */
  public record Result(
      String source,
      Map<String, String> methods,
      Map<String, String> xtendDefinitions,
      List<UtilityMethodError> errors) {

    /** Whether at least one utility method does not compile. */
    public boolean hasErrors() {
      return errors.stream().anyMatch(UtilityMethodError::isError);
    }

    /** The problems of one utility method, including warnings. */
    public List<UtilityMethodError> errorsOf(String utilityMethodName) {
      return errors.stream().filter(e -> utilityMethodName.equals(e.utilityMethodName())).toList();
    }

    /** The names of the utility methods that do not compile. */
    public Set<String> failedUtilityMethods() {
      return errors.stream()
          .filter(UtilityMethodError::isError)
          .map(UtilityMethodError::utilityMethodName)
          .collect(Collectors.toCollection(TreeSet::new));
    }
  }

  /**
   * Where a utility method is in the generated Xtend source.
   *
   * @param utilityMethodName the utility method
   * @param headerLine the 1-based line of its {@code def static} header
   * @param firstBodyLine the 1-based line its body starts on
   * @param lastBodyLine the 1-based line its body ends on
   */
  public record BodyRange(
      String utilityMethodName, int headerLine, int firstBodyLine, int lastBodyLine) {

    /** Whether the 1-based line of the Xtend source lies in the body. */
    public boolean containsBodyLine(int line) {
      return line >= firstBodyLine && line <= lastBodyLine;
    }
  }

  /**
   * The generated Xtend source.
   *
   * @param source the Xtend source of the utility class
   * @param bodies where each utility method is, in source order
   * @param importLines the utility methods that declare the import on a 1-based line
   */
  public record GeneratedXtend(
      String source, List<BodyRange> bodies, Map<Integer, List<String>> importLines) {

    /** Copies the collections. */
    public GeneratedXtend {
      bodies = List.copyOf(bodies);
      importLines = Map.copyOf(importLines);
    }
  }

  private UtilityMethodClassGenerator() {}

  /**
   * Checks a utility method name.
   *
   * @param name the name
   * @return a description of the problem, or {@code null} if the name is valid
   */
  public static String nameProblem(String name) {
    if (name == null || name.isBlank()) {
      return "The utility method name must not be empty.";
    }
    if (isReserved(name)) {
      return "'" + name + "' is a keyword of Xtend or Java and cannot name a utility method.";
    }
    if (!JavaNames.isIdentifier(name)) {
      return "The utility method name '"
          + name
          + "' is not a valid Java method name. Use letters, digits and '_' and start with a"
          + " letter.";
    }
    return null;
  }

  /**
   * Checks the parts of a utility method that are not Xtend code: name, parameters, types, imports
   * and the size of the body.
   *
   * @param utilityMethod the utility method
   * @return the problems found, empty if the utility method can be compiled
   */
  public static List<String> signatureProblems(UtilityMethodDefinition utilityMethod) {
    List<String> problems = new ArrayList<>();
    String nameProblem = nameProblem(utilityMethod.name());
    if (nameProblem != null) {
      problems.add(nameProblem);
    }
    Set<String> parameterNames = new HashSet<>();
    for (UtilityMethodDefinition.Parameter parameter : utilityMethod.parameters()) {
      String name = parameter.name() == null ? "" : parameter.name().trim();
      if (isReserved(name) || !JavaNames.isIdentifier(name)) {
        problems.add("'" + name + "' is not a valid parameter name.");
      } else if (!parameterNames.add(name)) {
        problems.add("The parameter '" + name + "' is declared twice.");
      }
      if (isBlank(parameter.type())) {
        problems.add("Parameter '" + name + "': the type is missing.");
      } else if (!isTypeReference(parameter.type())) {
        problems.add("Parameter '" + name + "': '" + parameter.type() + "' is not a type.");
      }
    }
    if (isBlank(utilityMethod.returnType())) {
      problems.add("Return type: the type is missing.");
    } else if (!isTypeReference(utilityMethod.returnType())) {
      problems.add("Return type: '" + utilityMethod.returnType() + "' is not a type.");
    }
    if (utilityMethod.imports().size() > MAX_IMPORTS) {
      problems.add("A utility method can have at most " + MAX_IMPORTS + " imports.");
    }
    for (String entry : utilityMethod.imports()) {
      if (!isImport(entry)) {
        problems.add(
            "'" + entry + "' is not a Java import, e.g. 'java.util.List' or 'my.model.*'.");
      }
    }
    if (isBlank(utilityMethod.body())) {
      problems.add("The body must not be empty.");
    } else if (utilityMethod.body().length() > MAX_BODY_LENGTH) {
      problems.add("The body must not be longer than " + MAX_BODY_LENGTH + " characters.");
    }
    return problems;
  }

  /**
   * Generates the Xtend utility class.
   *
   * @param utilityMethods the utility methods, which must have no {@link #signatureProblems}
   * @return the source and where each utility method is in it
   */
  public static GeneratedXtend generate(List<UtilityMethodDefinition> utilityMethods) {
    List<UtilityMethodDefinition> sorted =
        utilityMethods.stream()
            .sorted(Comparator.comparing(UtilityMethodDefinition::name))
            .toList();
    Map<String, List<String>> importsByEntry = new TreeMap<>();
    for (UtilityMethodDefinition utilityMethod : sorted) {
      for (String entry : utilityMethod.imports()) {
        importsByEntry
            .computeIfAbsent(entry.trim(), k -> new ArrayList<>())
            .add(utilityMethod.name());
      }
    }

    SourceWriter out = new SourceWriter();
    out.line("package " + PACKAGE_NAME);
    out.line("");
    for (String entry : DEFAULT_IMPORTS) {
      if (importsByEntry.keySet().stream().noneMatch(i -> sameSimpleName(i, entry))) {
        out.line("import " + entry);
      }
    }
    Map<Integer, List<String>> importLines = new TreeMap<>();
    for (Map.Entry<String, List<String>> entry : importsByEntry.entrySet()) {
      importLines.put(out.nextLine(), List.copyOf(entry.getValue()));
      out.line("import " + entry.getKey());
    }
    out.line("");
    out.line("/**");
    out.line(" * Utility methods generated by the Methodologist Workbench for this project.");
    out.line(" *");
    out.line(" * <p>Use them in reactions and routines after importing");
    out.line(" * {@code " + QUALIFIED_CLASS_NAME + "}.");
    out.line(" *");
    out.line(" * <p>Do not edit this file: it is regenerated from the utility method");
    out.line(" * definitions on every build.");
    out.line(" */");
    out.line("final class " + CLASS_NAME + " {");

    List<BodyRange> bodies = new ArrayList<>();
    for (UtilityMethodDefinition utilityMethod : sorted) {
      out.line("");
      javadoc(utilityMethod).forEach(out::line);
      int headerLine = out.nextLine();
      out.line(INDENT + header(utilityMethod) + " {");
      int firstBodyLine = out.nextLine();
      List<String> bodyLines = utilityMethod.body().strip().lines().toList();
      bodyLines.forEach(out::line);
      bodies.add(
          new BodyRange(
              utilityMethod.name(),
              headerLine,
              firstBodyLine,
              firstBodyLine + Math.max(bodyLines.size(), 1) - 1));
      out.line(INDENT + "}");
    }
    out.line("}");
    return new GeneratedXtend(out.toString(), bodies, importLines);
  }

  /**
   * The utility method as it appears in the Xtend class, as shown to the methodologist.
   *
   * @param utilityMethod the utility method
   * @return its {@code def static} declaration with the body
   */
  public static String xtendDefinition(UtilityMethodDefinition utilityMethod) {
    String body = utilityMethod.body() == null ? "" : utilityMethod.body().strip();
    String indentedBody = body.lines().map(line -> INDENT + line).collect(Collectors.joining("\n"));
    return header(utilityMethod) + " {\n" + indentedBody + "\n}";
  }

  private static String header(UtilityMethodDefinition utilityMethod) {
    String parameters =
        utilityMethod.parameters().stream()
            .map(p -> p.type().trim() + " " + p.name().trim())
            .collect(Collectors.joining(", "));
    return "def static "
        + utilityMethod.returnType().trim()
        + " "
        + utilityMethod.name()
        + "("
        + parameters
        + ")";
  }

  private static List<String> javadoc(UtilityMethodDefinition utilityMethod) {
    String description =
        isBlank(utilityMethod.description())
            ? "Generated from the utility method {@code " + utilityMethod.name() + "}."
            : utilityMethod.description().strip();
    List<String> lines = new ArrayList<>();
    lines.add(INDENT + "/**");
    description
        .lines()
        .forEach(line -> lines.add(INDENT + " *" + (line.isBlank() ? "" : " " + escape(line))));
    lines.add(INDENT + " */");
    return lines;
  }

  /** Keeps arbitrary text from ending a comment or forming a unicode escape. */
  private static String escape(String text) {
    return text.replace("*/", "*&#47;").replace("\\u", "\\\\u");
  }

  private static boolean sameSimpleName(String anImport, String qualifiedName) {
    String simpleName = qualifiedName.substring(qualifiedName.lastIndexOf('.'));
    return anImport.equals(qualifiedName) || anImport.endsWith(simpleName);
  }

  private static boolean isReserved(String name) {
    return JavaNames.isKeyword(name) || XTEND_KEYWORDS.contains(name.toLowerCase(Locale.ROOT));
  }

  private static boolean isBlank(String text) {
    return text == null || text.isBlank();
  }

  /**
   * Whether the text is a Java import without the keyword and semicolon: optionally {@code static},
   * then a qualified name, optionally ending in {@code .*}.
   */
  static boolean isImport(String entry) {
    if (entry == null || entry.isBlank() || entry.length() > MAX_IMPORT_LENGTH) {
      return false;
    }
    String name = entry.strip();
    if (name.startsWith("static ")) {
      name = name.substring("static ".length()).strip();
    }
    if (name.endsWith(".*")) {
      name = name.substring(0, name.length() - 2);
    }
    String[] segments = name.split("\\.", -1);
    if (segments.length < 2) {
      return false;
    }
    for (String segment : segments) {
      if (!JavaNames.isIdentifier(segment)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Whether the text can be a type reference. The Xtend compiler checks the type; this only keeps
   * text out that could end the method header, such as braces, parentheses or line breaks.
   */
  private static boolean isTypeReference(String type) {
    for (char c : type.strip().toCharArray()) {
      boolean allowed =
          Character.isLetterOrDigit(c)
              || c == '_'
              || c == '$'
              || c == '.'
              || c == '<'
              || c == '>'
              || c == ','
              || c == '?'
              || c == '['
              || c == ']'
              || c == ' ';
      if (!allowed) {
        return false;
      }
    }
    return true;
  }

  /** Collects source lines and knows the number of the next one. */
  private static final class SourceWriter {
    private final StringBuilder builder = new StringBuilder();
    private int lines;

    void line(String line) {
      builder.append(line).append('\n');
      lines++;
    }

    int nextLine() {
      return lines + 1;
    }

    @Override
    public String toString() {
      return builder.toString();
    }
  }
}
