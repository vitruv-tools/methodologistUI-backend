package tools.vitruv.methodologist.vsum.utilitymethod;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Java naming rules shared by the utility method code generator, mirroring EMF's code generator.
 */
public final class JavaNames {

  private static final Set<String> KEYWORDS =
      Set.of(
          "abstract",
          "assert",
          "boolean",
          "break",
          "byte",
          "case",
          "catch",
          "char",
          "class",
          "const",
          "continue",
          "default",
          "do",
          "double",
          "else",
          "enum",
          "extends",
          "final",
          "finally",
          "float",
          "for",
          "goto",
          "if",
          "implements",
          "import",
          "instanceof",
          "int",
          "interface",
          "long",
          "native",
          "new",
          "package",
          "private",
          "protected",
          "public",
          "return",
          "short",
          "static",
          "strictfp",
          "super",
          "switch",
          "synchronized",
          "this",
          "throw",
          "throws",
          "transient",
          "try",
          "void",
          "volatile",
          "while",
          "true",
          "false",
          "null",
          "var",
          "yield",
          "record",
          "sealed",
          "permits",
          "_");

  private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");

  private JavaNames() {}

  /** Whether {@code name} is a reserved word that cannot be used as an identifier. */
  public static boolean isKeyword(String name) {
    return KEYWORDS.contains(name);
  }

  /** Whether {@code name} is a plain ASCII Java identifier that is not a keyword. */
  public static boolean isIdentifier(String name) {
    return name != null && IDENTIFIER.matcher(name).matches() && !isKeyword(name);
  }

  /** Appends {@code _} to reserved words, as EMF does for generated names. */
  public static String safeName(String name) {
    return isKeyword(name) ? name + "_" : name;
  }

  /** Upper-cases the first character, as EMF does for accessor names. */
  public static String capName(String name) {
    if (name.isEmpty()) {
      return name;
    }
    return Character.toUpperCase(name.charAt(0)) + name.substring(1);
  }

  /**
   * The constant name EMF generates for an enum literal: words of the camel-case name joined by
   * {@code _} and upper-cased, e.g. {@code redLight} becomes {@code RED_LIGHT}.
   */
  public static String enumLiteralName(String literal) {
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < literal.length(); i++) {
      char current = literal.charAt(i);
      if (i > 0 && Character.isUpperCase(current) && builder.charAt(builder.length() - 1) != '_') {
        char previous = literal.charAt(i - 1);
        boolean nextIsLower =
            i + 1 < literal.length() && Character.isLowerCase(literal.charAt(i + 1));
        if (Character.isLowerCase(previous)
            || Character.isDigit(previous)
            || (Character.isUpperCase(previous) && nextIsLower)) {
          builder.append('_');
        }
      }
      builder.append(current);
    }
    return builder.toString().toUpperCase(Locale.ROOT);
  }

  /** A Java string literal with the given content. */
  public static String stringLiteral(String value) {
    StringBuilder builder = new StringBuilder("\"");
    for (char c : value.toCharArray()) {
      switch (c) {
        case '"' -> builder.append("\\\"");
        case '\\' -> builder.append("\\\\");
        case '\n' -> builder.append("\\n");
        case '\r' -> builder.append("\\r");
        case '\t' -> builder.append("\\t");
        default -> {
          if (c < 0x20) {
            builder.append(String.format("\\u%04x", (int) c));
          } else {
            builder.append(c);
          }
        }
      }
    }
    return builder.append('"').toString();
  }
}
