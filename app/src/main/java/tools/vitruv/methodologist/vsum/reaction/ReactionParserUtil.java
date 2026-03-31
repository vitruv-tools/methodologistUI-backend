package tools.vitruv.methodologist.vsum.reaction;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Utility for parsing reaction files. */
public class ReactionParserUtil {
  private static final Pattern REACTION = Pattern.compile("reactions\\s*:\\s*(\\w+)");

  private static final Pattern MODEL1 =
      Pattern.compile("in\\s+reaction\\s+to\\s+changes\\s+in\\s+(\\w+)");

  private static final Pattern MODEL2 = Pattern.compile("execute\\s+actions\\s+in\\s+(\\w+)");

  private static final Pattern IMPORT = Pattern.compile("import\\s+\"([^\"]+)\"\\s+as\\s+(\\w+)");

  /**
   * Record representing information extracted from a reaction file.
   *
   * @param reactionName the name of the reaction
   * @param modelAlias1 the alias of the first model
   * @param modelAlias2 the alias of the second model
   * @param modelUri1 the URI of the first model
   * @param modelUri2 the URI of the second model
   */
  public record ReactionFileInfo(
      String reactionName,
      String modelAlias1,
      String modelAlias2,
      String modelUri1,
      String modelUri2) {}

  /**
   * Parses the content of a reaction file and returns a {@link ReactionFileInfo}.
   *
   * @param content the content of the reaction file
   * @return the extracted reaction file information
   */
  public static ReactionFileInfo parse(String content) {

    String reactionName = match(content, REACTION);
    String modelAlias1 = match(content, MODEL1);
    String modelAlias2 = match(content, MODEL2);

    Matcher matcher = IMPORT.matcher(content);

    Map<String, String> imports = new HashMap<>();

    while (matcher.find()) {
      imports.put(matcher.group(2), matcher.group(1));
    }

    String modelUri1 = imports.get(modelAlias1);
    String modelUri2 = imports.get(modelAlias2);

    return new ReactionFileInfo(reactionName, modelAlias1, modelAlias2, modelUri1, modelUri2);
  }

  /**
   * Matches a pattern against the given text and returns the first capturing group.
   *
   * @param text the text to match against
   * @param regex the pattern to use
   * @return the first capturing group, or null if no match is found
   */
  private static String match(String text, Pattern regex) {
    Matcher m = regex.matcher(text);
    return m.find() ? m.group(1) : null;
  }
}
