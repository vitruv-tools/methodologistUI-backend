package tools.vitruv.methodologist.vsum.helper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import tools.vitruv.methodologist.vsum.helper.OclAst.Expr;
import tools.vitruv.methodologist.vsum.helper.OclAst.Pos;
import tools.vitruv.methodologist.vsum.helper.OclToJavaTranslator.HelperSignature;

/**
 * Generates the Java utility class that contains all helpers of a VSUM as {@code public static}
 * methods, so that every reaction and routine can call them.
 */
public final class HelperClassGenerator {

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
   * A helper that could not be translated.
   *
   * @param helperName the helper
   * @param message what is wrong
   * @param line 1-based line in the OCL body, or {@code null} if the problem is not in the body
   * @param column 1-based column in the OCL body, or {@code null}
   */
  public record HelperError(String helperName, String message, Integer line, Integer column) {

    static HelperError of(String helperName, HelperTranslationException e) {
      Pos pos = e.pos();
      return new HelperError(
          helperName,
          e.getMessage(),
          pos == null ? null : pos.line(),
          pos == null ? null : pos.column());
    }
  }

  /**
   * The generated class.
   *
   * @param source the complete Java source; helpers with errors are left out
   * @param methods the Java source of each translated helper's method, by helper name
   * @param errors the helpers that could not be translated
   */
  public record Result(String source, Map<String, String> methods, List<HelperError> errors) {

    /** Whether at least one helper could not be translated. */
    public boolean hasErrors() {
      return !errors.isEmpty();
    }

    /** The errors of one helper. */
    public List<HelperError> errorsOf(String helperName) {
      return errors.stream().filter(e -> e.helperName().equals(helperName)).toList();
    }
  }

  private HelperClassGenerator() {}

  /**
   * Checks a helper name.
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
   * Translates all helpers and assembles the utility class.
   *
   * @param helpers the helpers of the VSUM
   * @param index the VSUM's metamodels
   * @return the class, the method of each helper and the problems found
   */
  public static Result generate(List<HelperDefinition> helpers, EcoreTypeIndex index) {
    List<HelperDefinition> sorted =
        helpers.stream().sorted(Comparator.comparing(HelperDefinition::name)).toList();
    List<HelperError> errors = new ArrayList<>();
    Map<String, HelperSignature> signatures = new LinkedHashMap<>();
    Map<String, OclTypeResolver> resolvers = new LinkedHashMap<>();
    for (HelperDefinition helper : sorted) {
      try {
        OclTypeResolver resolver = new OclTypeResolver(index, helper.imports());
        HelperSignature signature = signature(helper, resolver);
        if (signatures.putIfAbsent(helper.name(), signature) != null) {
          throw new HelperTranslationException(
              "Another utility method is already named '" + helper.name() + "'.");
        }
        resolvers.put(helper.name(), resolver);
      } catch (HelperTranslationException e) {
        errors.add(HelperError.of(helper.name(), e));
      }
    }

    JavaImports imports = new JavaImports(PACKAGE_NAME, CLASS_NAME);
    Set<String> runtime = new HashSet<>();
    Map<String, String> methods = new LinkedHashMap<>();
    for (HelperDefinition helper : sorted) {
      OclTypeResolver resolver = resolvers.get(helper.name());
      if (resolver == null) {
        continue;
      }
      JavaImports helperImports = imports.copy();
      Set<String> helperRuntime = new HashSet<>(runtime);
      try {
        OclToJavaTranslator translator =
            new OclToJavaTranslator(resolver, helperImports, helperRuntime, signatures, CLASS_NAME);
        Expr body = OclParser.parseExpression(helper.body());
        List<String> statements = translator.translateBody(signatures.get(helper.name()), body);
        methods.put(
            helper.name(), method(helper, signatures.get(helper.name()), statements, translator));
        imports = helperImports;
        runtime = helperRuntime;
      } catch (HelperTranslationException e) {
        errors.add(HelperError.of(helper.name(), e));
      }
    }

    String runtimeSource = HelperRuntime.render(runtime, imports);
    return new Result(classSource(imports, methods, runtimeSource), methods, errors);
  }

  private static HelperSignature signature(HelperDefinition helper, OclTypeResolver resolver) {
    String problem = nameProblem(helper.name());
    if (problem != null) {
      throw new HelperTranslationException(problem);
    }
    List<String> names = new ArrayList<>();
    List<OclType> types = new ArrayList<>();
    for (HelperDefinition.Parameter parameter : helper.parameters()) {
      String name = parameter.name() == null ? "" : parameter.name().trim();
      if (!JavaNames.isIdentifier(name) || name.equals("self") || name.equals("thisModule")) {
        throw new HelperTranslationException("'" + name + "' is not a valid parameter name.");
      }
      if (names.contains(name)) {
        throw new HelperTranslationException("The parameter '" + name + "' is declared twice.");
      }
      names.add(name);
      types.add(resolveSignatureType(resolver, parameter.type(), "Parameter '" + name + "'"));
    }
    OclType returnType = resolveSignatureType(resolver, helper.returnType(), "Return type");
    return new HelperSignature(helper.name(), names, types, returnType);
  }

  private static OclType resolveSignatureType(
      OclTypeResolver resolver, String type, String context) {
    if (type == null || type.isBlank()) {
      throw new HelperTranslationException(context + ": the type is missing.");
    }
    try {
      return resolver.resolve(OclParser.parseType(type));
    } catch (HelperTranslationException e) {
      throw new HelperTranslationException(context + " '" + type.trim() + "': " + e.getMessage());
    }
  }

  // ── source assembly ──────────────────────────────────────────────────────

  private static String method(
      HelperDefinition helper,
      HelperSignature signature,
      List<String> statements,
      OclToJavaTranslator translator) {
    StringBuilder builder = new StringBuilder();
    builder.append("  /**\n");
    String description =
        helper.description() == null || helper.description().isBlank()
            ? "Generated from the utility method {@code " + helper.name() + "}."
            : helper.description().strip();
    description
        .lines()
        .forEach(
            line ->
                builder
                    .append("   *")
                    .append(line.isBlank() ? "" : " " + escapeComment(line))
                    .append('\n'));
    builder.append("   */\n");
    helper
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
        .append(helper.name())
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
