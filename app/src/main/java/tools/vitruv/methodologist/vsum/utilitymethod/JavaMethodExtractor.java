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
    private int depth;
    private boolean opened;
    private boolean blockComment;

    /** Whether the line contains the brace that closes the first block opened. */
    boolean closesBlock(String line) {
      int j = 0;
      while (j < line.length()) {
        if (blockComment) {
          j = skipComment(line, j);
          continue;
        }
        char c = line.charAt(j);
        if (c == '/' && next(line, j) == '/') {
          return false;
        }
        if (c == '/' && next(line, j) == '*') {
          blockComment = true;
          j += 2;
          continue;
        }
        if (c == '"' || c == '\'') {
          j = skipLiteral(line, j);
          continue;
        }
        if (count(c)) {
          return true;
        }
        j++;
      }
      return false;
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
