package tools.vitruv.methodologist.vsum.reaction;
import java.util.regex.*;
import java.util.*;

public class ReactionParserUtil {
    private static final Pattern REACTION =
            Pattern.compile("reactions\\s*:\\s*(\\w+)");

    private static final Pattern MODEL1 =
            Pattern.compile("in\\s+reaction\\s+to\\s+changes\\s+in\\s+(\\w+)");

    private static final Pattern MODEL2 =
            Pattern.compile("execute\\s+actions\\s+in\\s+(\\w+)");

    private static final Pattern IMPORT =
            Pattern.compile("import\\s+\"([^\"]+)\"\\s+as\\s+(\\w+)");

    public record ReactionFileInfo(
            String reactionName,
            String modelAlias1,
            String modelAlias2,
            String modelUri1,
            String modelUri2
    ) {}

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

        return new ReactionFileInfo(
                reactionName,
                modelAlias1,
                modelAlias2,
                modelUri1,
                modelUri2
        );
    }

    private static String match(String text, Pattern regex) {
        Matcher m = regex.matcher(text);
        return m.find() ? m.group(1) : null;
    }
}
