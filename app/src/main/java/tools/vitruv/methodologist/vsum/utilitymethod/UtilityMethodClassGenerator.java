package tools.vitruv.methodologist.vsum.utilitymethod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import tools.vitruv.methodologist.vsum.utilitymethod.OclAst.Expr;
import tools.vitruv.methodologist.vsum.utilitymethod.OclAst.Pos;
import tools.vitruv.methodologist.vsum.utilitymethod.OclToJavaTranslator.UtilityMethodSignature;

/**
 * Generates the Java utility class that contains all utility methods of a VSUM as {@code public
 * static} methods, so that every reaction and routine can call them.
 */
public final class UtilityMethodClassGenerator {

  /** Package of the generated class, inside the consistency module of the VSUM project. */
  public static final String PACKAGE_NAME = "tools.vitruv.methodologisttemplate.consistency.utils";

  /** Simple name of the generated class. */
  public static final String CLASS_NAME = "Utility";

  /** Fully qualified name of the generated class, as imported by reactions. */
  public static final String QUALIFIED_CLASS_NAME = PACKAGE_NAME + "." + CLASS_NAME;

  /** File name of the generated class. */
  public static final String FILE_NAME = CLASS_NAME + ".java";

  /** Prefix reserved for the private runtime support methods of the generated class. */
  static final String RESERVED_PREFIX = "ocl";

  /**
   * A utility method that could not be translated.
   *
   * @param utilityMethodName the utility method
   * @param message what is wrong
   * @param line 1-based line in the OCL body, or {@code null} if the problem is not in the body
   * @param column 1-based column in the OCL body, or {@code null}
   */
  public record UtilityMethodError(
      String utilityMethodName, String message, Integer line, Integer column) {

    static UtilityMethodError of(String utilityMethodName, UtilityMethodTranslationException e) {
      Pos pos = e.pos();
      return new UtilityMethodError(
          utilityMethodName,
          e.getMessage(),
          pos == null ? null : pos.line(),
          pos == null ? null : pos.column());
    }
  }

  /**
   * The generated class.
   *
   * @param source the complete Java source; utility methods with errors are left out
   * @param methods the Java source of each translated utility method's method, by utility method
   *     name
   * @param errors the utility methods that could not be translated
   */
  public record Result(
      String source, Map<String, String> methods, List<UtilityMethodError> errors) {

    /** Whether at least one utility method could not be translated. */
    public boolean hasErrors() {
      return !errors.isEmpty();
    }

    /** The errors of one utility method. */
    public List<UtilityMethodError> errorsOf(String utilityMethodName) {
      return errors.stream().filter(e -> e.utilityMethodName().equals(utilityMethodName)).toList();
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
    if (!JavaNames.isIdentifier(name)) {
      return "The utility method name '"
          + name
          + "' is not a valid Java method name. Use letters, digits and '_' and start with a"
          + " letter.";
    }
    if (name.startsWith(RESERVED_PREFIX)) {
      return "Utility method names must not start with '"
          + RESERVED_PREFIX
          + "'; it is reserved for OCL.";
    }
    return null;
  }

  /**
   * Translates all utility methods and assembles the utility class.
   *
   * @param utility methods the utility methods of the VSUM
   * @param index the VSUM's metamodels
   * @return the class, the method of each utility method and the problems found
   */
  public static Result generate(
      List<UtilityMethodDefinition> utilityMethods, EcoreTypeIndex index) {
    List<UtilityMethodDefinition> sorted =
        utilityMethods.stream()
            .sorted(Comparator.comparing(UtilityMethodDefinition::name))
            .toList();
    List<UtilityMethodError> errors = new ArrayList<>();
    Map<String, UtilityMethodSignature> signatures = new LinkedHashMap<>();
    Map<String, OclTypeResolver> resolvers = new LinkedHashMap<>();
    for (UtilityMethodDefinition utilityMethod : sorted) {
      try {
        OclTypeResolver resolver = new OclTypeResolver(index, utilityMethod.imports());
        UtilityMethodSignature signature = signature(utilityMethod, resolver);
        if (signatures.putIfAbsent(utilityMethod.name(), signature) != null) {
          throw new UtilityMethodTranslationException(
              "Another utility method is already named '" + utilityMethod.name() + "'.");
        }
        resolvers.put(utilityMethod.name(), resolver);
      } catch (UtilityMethodTranslationException e) {
        errors.add(UtilityMethodError.of(utilityMethod.name(), e));
      }
    }

    JavaImports imports = new JavaImports(PACKAGE_NAME, CLASS_NAME);
    Set<String> runtime = new HashSet<>();
    Map<String, String> methods = new LinkedHashMap<>();
    for (UtilityMethodDefinition utilityMethod : sorted) {
      OclTypeResolver resolver = resolvers.get(utilityMethod.name());
      if (resolver == null) {
        continue;
      }
      JavaImports utilityMethodImports = imports.copy();
      Set<String> utilityMethodRuntime = new HashSet<>(runtime);
      try {
        OclToJavaTranslator translator =
            new OclToJavaTranslator(
                resolver, utilityMethodImports, utilityMethodRuntime, signatures, CLASS_NAME);
        Expr body = OclParser.parseExpression(utilityMethod.body());
        List<String> statements =
            translator.translateBody(signatures.get(utilityMethod.name()), body);
        methods.put(
            utilityMethod.name(),
            method(utilityMethod, signatures.get(utilityMethod.name()), statements, translator));
        imports = utilityMethodImports;
        runtime = utilityMethodRuntime;
      } catch (UtilityMethodTranslationException e) {
        errors.add(UtilityMethodError.of(utilityMethod.name(), e));
      }
    }

    String runtimeSource = UtilityMethodRuntime.render(runtime, imports);
    return new Result(classSource(imports, methods, runtimeSource), methods, errors);
  }

  private static UtilityMethodSignature signature(
      UtilityMethodDefinition utilityMethod, OclTypeResolver resolver) {
    String problem = nameProblem(utilityMethod.name());
    if (problem != null) {
      throw new UtilityMethodTranslationException(problem);
    }
    List<String> names = new ArrayList<>();
    List<OclType> types = new ArrayList<>();
    for (UtilityMethodDefinition.Parameter parameter : utilityMethod.parameters()) {
      String name = parameter.name() == null ? "" : parameter.name().trim();
      if (!JavaNames.isIdentifier(name) || name.equals("self") || name.equals("thisModule")) {
        throw new UtilityMethodTranslationException(
            "'" + name + "' is not a valid parameter name.");
      }
      if (names.contains(name)) {
        throw new UtilityMethodTranslationException(
            "The parameter '" + name + "' is declared twice.");
      }
      names.add(name);
      types.add(resolveSignatureType(resolver, parameter.type(), "Parameter '" + name + "'"));
    }
    OclType returnType = resolveSignatureType(resolver, utilityMethod.returnType(), "Return type");
    return new UtilityMethodSignature(utilityMethod.name(), names, types, returnType);
  }

  private static OclType resolveSignatureType(
      OclTypeResolver resolver, String type, String context) {
    if (type == null || type.isBlank()) {
      throw new UtilityMethodTranslationException(context + ": the type is missing.");
    }
    try {
      return resolver.resolve(OclParser.parseType(type));
    } catch (UtilityMethodTranslationException e) {
      throw new UtilityMethodTranslationException(
          context + " '" + type.trim() + "': " + e.getMessage());
    }
  }

  // ── source assembly ──────────────────────────────────────────────────────

  private static String method(
      UtilityMethodDefinition utilityMethod,
      UtilityMethodSignature signature,
      List<String> statements,
      OclToJavaTranslator translator) {
    StringBuilder builder = new StringBuilder();
    builder.append("  /**\n");
    String description =
        utilityMethod.description() == null || utilityMethod.description().isBlank()
            ? "Generated from the utility method {@code " + utilityMethod.name() + "}."
            : utilityMethod.description().strip();
    description
        .lines()
        .forEach(
            line ->
                builder
                    .append("   *")
                    .append(line.isBlank() ? "" : " " + escapeComment(line))
                    .append('\n'));
    builder.append("   */\n");
    utilityMethod
        .oclDefinition()
        .lines()
        .forEach(
            line ->
                builder
                    .append("  // ")
                    .append(escapeComment(line.replace("\t", "  ")))
                    .append('\n'));
    String parameters =
        IntStream.range(0, signature.parameterNames().size())
            .mapToObj(
                i ->
                    translator.javaType(signature.parameterTypes().get(i), false)
                        + " "
                        + signature.parameterNames().get(i))
            .collect(Collectors.joining(", "));
    builder
        .append("  public static ")
        .append(translator.javaType(signature.returnType(), false))
        .append(' ')
        .append(utilityMethod.name())
        .append('(')
        .append(parameters)
        .append(") {\n");
    for (String statement : statements) {
      JavaStatementFormatter.format(statement, 4)
          .forEach(line -> builder.append(line).append('\n'));
    }
    builder.append("  }\n");
    return builder.toString();
  }

  private static String classSource(
      JavaImports imports, Map<String, String> methods, String runtimeSource) {
    StringBuilder builder = new StringBuilder();
    builder.append("package ").append(PACKAGE_NAME).append(";\n\n");
    List<String> importList = imports.imports();
    if (!importList.isEmpty()) {
      importList.forEach(i -> builder.append("import ").append(i).append(";\n"));
      builder.append('\n');
    }
    builder
        .append("/**\n")
        .append(" * Utility methods generated by the Methodologist Workbench for this project.\n")
        .append(" *\n")
        .append(" * <p>Use them in reactions and routines after importing\n")
        .append(" * {@code ")
        .append(QUALIFIED_CLASS_NAME)
        .append("}.\n")
        .append(" *\n")
        .append(
            " * <p>Do not edit this file: it is regenerated from the utility method definitions on every build.\n")
        .append(" */\n")
        .append("public final class ")
        .append(CLASS_NAME)
        .append(" {\n\n")
        .append("  private ")
        .append(CLASS_NAME)
        .append("() {}\n");
    for (String method : methods.values()) {
      builder.append('\n').append(method);
    }
    builder.append(runtimeSource);
    builder.append("}\n");
    return builder.toString();
  }

  /** Keeps arbitrary text from ending a comment or forming a unicode escape. */
  private static String escapeComment(String text) {
    return text.replace("*/", "*&#47;").replace("\\u", "\\\\u");
  }
}
