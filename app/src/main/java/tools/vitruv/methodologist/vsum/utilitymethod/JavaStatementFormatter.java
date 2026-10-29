package tools.vitruv.methodologist.vsum.utilitymethod;

import java.util.ArrayList;
import java.util.List;

/**
 * Wraps long generated Java statements so that the methodologist can read them, roughly following
 * google-java-format: method chains break before each call, argument lists break after each comma,
 * and lambda bodies and conditionals are indented below their start.
 */
final class JavaStatementFormatter {

  private static final int MAX_WIDTH = 100;
  private static final int CONTINUATION = 4;

  private JavaStatementFormatter() {}

  /**
   * Formats one statement.
   *
   * @param statement the statement on a single line, ending with {@code ;}
   * @param indent the indentation of the statement
   * @return the indented lines
   */
  static List<String> format(String statement, int indent) {
    List<String> lines = new ArrayList<>();
    String body =
        statement.endsWith(";") ? statement.substring(0, statement.length() - 1) : statement;
    List<String> formatted = formatExpression(body, indent, 1);
    for (int i = 0; i < formatted.size(); i++) {
      String line = formatted.get(i);
      lines.add(i == formatted.size() - 1 ? line + ";" : line);
    }
    return lines;
  }

  /**
   * Formats {@code expr}, starting at column {@code indent}; {@code trailing} characters must fit
   * after the last line.
   */
  private static List<String> formatExpression(String expr, int indent, int trailing) {
    String pad = " ".repeat(indent);
    if (indent + expr.length() + trailing <= MAX_WIDTH) {
      return List.of(pad + expr);
    }
    List<String> lines = splitLambda(expr, indent, trailing);
    if (lines == null) {
      lines = splitOperators(expr, indent, trailing);
    }
    if (lines == null) {
      lines = splitChain(expr, indent, trailing);
    }
    if (lines == null) {
      lines = splitArguments(expr, indent, trailing);
    }
    return lines == null ? List.of(pad + expr) : lines;
  }

  /** {@code x -> body}: keeps the parameter on the first line and formats the body. */
  private static List<String> splitLambda(String expr, int indent, int trailing) {
    int arrow = topLevelIndexOf(expr, " -> ");
    if (arrow < 0 || !isLambdaHead(expr.substring(0, arrow))) {
      return null;
    }
    String head = expr.substring(0, arrow + 4);
    List<String> body =
        formatExpression(expr.substring(arrow + 4), indent + CONTINUATION, trailing);
    List<String> lines = new ArrayList<>();
    if (body.size() == 1) {
      lines.add(" ".repeat(indent) + head + body.get(0).strip());
      return lines.get(0).length() + trailing <= MAX_WIDTH
          ? lines
          : withBodyBelow(head, body, indent);
    }
    return withBodyBelow(head, body, indent);
  }

  private static List<String> withBodyBelow(String head, List<String> body, int indent) {
    List<String> lines = new ArrayList<>();
    lines.add(" ".repeat(indent) + head.stripTrailing());
    lines.addAll(body);
    return lines;
  }

  private static boolean isLambdaHead(String head) {
    return head.matches("[\\w$]+") || (head.startsWith("(") && head.endsWith(")"));
  }

  /** Breaks before top-level {@code ? :}, {@code ||}, {@code &&} and {@code +} operators. */
  private static List<String> splitOperators(String expr, int indent, int trailing) {
    for (String[] group : new String[][] {{" ? ", " : "}, {" || "}, {" && "}, {" + ", " - "}}) {
      List<Integer> positions = new ArrayList<>();
      for (String op : group) {
        positions.addAll(topLevelIndexesOf(expr, op));
      }
      if (positions.isEmpty()) {
        continue;
      }
      positions.sort(null);
      List<String> parts = new ArrayList<>();
      int start = 0;
      for (int position : positions) {
        parts.add(expr.substring(start, position));
        start = position + 1;
      }
      parts.add(expr.substring(start));
      List<String> lines = new ArrayList<>();
      for (int i = 0; i < parts.size(); i++) {
        int partIndent = i == 0 ? indent : indent + CONTINUATION;
        lines.addAll(
            formatExpression(parts.get(i), partIndent, i == parts.size() - 1 ? trailing : 0));
      }
      return lines;
    }
    return null;
  }

  /** Breaks a method chain before each top-level {@code .call(...)} that follows a call. */
  private static List<String> splitChain(String expr, int indent, int trailing) {
    List<Integer> dots = new ArrayList<>();
    Depth depth = new Depth();
    for (int i = 0; i < expr.length(); i++) {
      char c = expr.charAt(i);
      if (c == '"') {
        i = skipString(expr, i);
      } else if (!depth.track(expr, i)
          && c == '.'
          && i > 0
          && depth.isTop()
          && expr.charAt(i - 1) == ')') {
        dots.add(i);
      }
    }
    if (dots.isEmpty()) {
      return null;
    }
    List<String> lines = new ArrayList<>();
    lines.addAll(formatExpression(expr.substring(0, dots.get(0)), indent, 0));
    for (int i = 0; i < dots.size(); i++) {
      int end = i + 1 < dots.size() ? dots.get(i + 1) : expr.length();
      lines.addAll(
          formatExpression(
              expr.substring(dots.get(i), end),
              indent + CONTINUATION,
              i == dots.size() - 1 ? trailing : 0));
    }
    return lines;
  }

  /** Breaks the arguments of a trailing call, one per line. */
  private static List<String> splitArguments(String expr, int indent, int trailing) {
    if (!expr.endsWith(")")) {
      return null;
    }
    int open = matchingOpen(expr, expr.length() - 1);
    if (open <= 0) {
      return null;
    }
    String inner = expr.substring(open + 1, expr.length() - 1);
    List<String> args = splitTopLevel(inner);
    if (args.isEmpty() || (args.size() == 1 && args.get(0).length() < 20)) {
      return null;
    }
    List<String> lines = new ArrayList<>();
    lines.add(" ".repeat(indent) + expr.substring(0, open + 1));
    for (int i = 0; i < args.size(); i++) {
      boolean last = i == args.size() - 1;
      List<String> arg =
          formatExpression(args.get(i).strip(), indent + CONTINUATION, last ? trailing + 1 : 1);
      List<String> withSuffix = new ArrayList<>(arg);
      int lastIndex = withSuffix.size() - 1;
      withSuffix.set(lastIndex, withSuffix.get(lastIndex) + (last ? ")" : ","));
      lines.addAll(withSuffix);
    }
    return lines;
  }

  // ── scanning ─────────────────────────────────────────────────────────────

  private static List<String> splitTopLevel(String text) {
    List<String> parts = new ArrayList<>();
    Depth depth = new Depth();
    int start = 0;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c == '"') {
        i = skipString(text, i);
      } else if (!depth.track(text, i) && c == ',' && depth.isTop()) {
        parts.add(text.substring(start, i));
        start = i + 1;
      }
    }
    parts.add(text.substring(start));
    return parts;
  }

  private static int topLevelIndexOf(String text, String needle) {
    List<Integer> all = topLevelIndexesOf(text, needle);
    return all.isEmpty() ? -1 : all.get(0);
  }

  private static List<Integer> topLevelIndexesOf(String text, String needle) {
    List<Integer> result = new ArrayList<>();
    int depth = 0;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c == '"') {
        i = skipString(text, i);
      } else if (c == '(') {
        depth++;
      } else if (c == ')') {
        depth--;
      } else if (depth == 0 && text.startsWith(needle, i)) {
        result.add(i);
        i += needle.length() - 1;
      }
    }
    return result;
  }

  private static int matchingOpen(String text, int close) {
    int depth = 0;
    for (int i = close; i >= 0; i--) {
      char c = text.charAt(i);
      if (c == ')') {
        depth++;
      } else if (c == '(') {
        depth--;
        if (depth == 0) {
          return i;
        }
      } else if (c == '"') {
        i = text.lastIndexOf('"', i - 1);
        if (i < 0) {
          return -1;
        }
      }
    }
    return -1;
  }

  /**
   * Nesting depth in parentheses and type arguments. Generated comparisons always have spaces
   * around {@code <} and {@code >}, type arguments never do.
   */
  private static final class Depth {
    private int parentheses;
    private int angles;

    /** Updates the depth for the character at {@code index}; returns whether it was a bracket. */
    boolean track(String text, int index) {
      char c = text.charAt(index);
      char previous = index > 0 ? text.charAt(index - 1) : ' ';
      if (c == '(') {
        parentheses++;
      } else if (c == ')') {
        parentheses--;
      } else if (c == '<' && previous != ' ' && previous != '<') {
        angles++;
      } else if (c == '>' && angles > 0 && previous != ' ' && previous != '-') {
        angles--;
      } else {
        return false;
      }
      return true;
    }

    boolean isTop() {
      return parentheses == 0 && angles == 0;
    }
  }

  private static int skipString(String text, int start) {
    for (int i = start + 1; i < text.length(); i++) {
      if (text.charAt(i) == '\\') {
        i++;
      } else if (text.charAt(i) == '"') {
        return i;
      }
    }
    return text.length() - 1;
  }
}
