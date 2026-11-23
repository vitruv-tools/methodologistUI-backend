package tools.vitruv.methodologist.vsum.utilitymethod;

import java.util.List;

/**
 * Cuts the declaration of one static method, with its Javadoc, out of the Java class the Xtend
 * compiler generated.
 */
final class JavaMethodExtractor {

  private JavaMethodExtractor() {}

  /**
   * Returns a method of the generated class.
   *
   * @param javaSource the generated class
   * @param methodName the method name
   * @return the method, unindented by one level, or {@code null} if the class has no such method
   */
  static String extract(String javaSource, String methodName) {
    List<String> lines = javaSource.lines().toList();
    int header = headerLine(lines, methodName);
    if (header < 0) {
      return null;
    }
    int start = javadocStart(lines, header);
    int end = endLine(lines, header);
    if (end < 0) {
      return null;
    }
    StringBuilder method = new StringBuilder();
    for (int i = start; i <= end; i++) {
      String line = lines.get(i);
      method.append(line.startsWith("  ") ? line.substring(2) : line).append('\n');
    }
    return method.toString();
  }

  private static int headerLine(List<String> lines, String methodName) {
    String call = " " + methodName + "(";
    for (int i = 0; i < lines.size(); i++) {
      String line = lines.get(i);
      if (line.startsWith("  public static ") && line.contains(call)) {
        return i;
      }
    }
    return -1;
  }

  private static int javadocStart(List<String> lines, int header) {
    int i = header - 1;
    while (i >= 0 && lines.get(i).strip().startsWith("@")) {
      i--;
    }
    if (i < 0 || !lines.get(i).strip().endsWith("*/")) {
      return header;
    }
    while (i >= 0 && !lines.get(i).strip().startsWith("/**")) {
      i--;
    }
    return i < 0 ? header : i;
  }

  /** The line holding the brace that closes the method, skipping strings and comments. */
  private static int endLine(List<String> lines, int header) {
    BraceScanner scanner = new BraceScanner();
    for (int i = header; i < lines.size(); i++) {
      if (scanner.closesBlock(lines.get(i))) {
        return i;
      }
    }
    return -1;
  }

  /** Counts braces line by line, ignoring those in string literals and comments. */
  private static final class BraceScanner {
    private static final int CLOSED = -1;

    private int depth;
    private boolean opened;
    private boolean blockComment;

    /** Whether the line contains the brace that closes the first block opened. */
    boolean closesBlock(String line) {
      int j = 0;
      while (j < line.length()) {
        j = advance(line, j);
        if (j == CLOSED) {
          return true;
        }
      }
      return false;
    }

    /**
     * Scans the token at {@code j}: a comment, a literal or a single character.
     *
     * @return the index after the token, the line length after a line comment, or {@link #CLOSED}
     *     if the token is the brace that closes the first block
     */
    private int advance(String line, int j) {
      if (blockComment) {
        return skipComment(line, j);
      }
      char c = line.charAt(j);
      if (c == '/' && next(line, j) == '/') {
        return line.length();
      }
      if (c == '/' && next(line, j) == '*') {
        blockComment = true;
        return j + 2;
      }
      if (c == '"' || c == '\'') {
        return skipLiteral(line, j);
      }
      return count(c) ? CLOSED : j + 1;
    }

    private boolean count(char c) {
      if (c == '{') {
        depth++;
        opened = true;
      } else if (c == '}') {
        depth--;
        return opened && depth == 0;
      }
      return false;
    }

    private int skipComment(String line, int start) {
      int end = line.indexOf("*/", start);
      if (end < 0) {
        return line.length();
      }
      blockComment = false;
      return end + 2;
    }

    /** The index after the string or character literal starting at {@code start}. */
    private static int skipLiteral(String line, int start) {
      char quote = line.charAt(start);
      int j = start + 1;
      while (j < line.length() && line.charAt(j) != quote) {
        j += line.charAt(j) == '\\' ? 2 : 1;
      }
      return j + 1;
    }

    private static char next(String line, int index) {
      return index + 1 < line.length() ? line.charAt(index + 1) : 0;
    }
  }
}
