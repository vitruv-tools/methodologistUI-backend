package tools.vitruv.methodologist.builder;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;

public class Main {
  public static void main(String[] args) throws Exception {
    Map<String, String> argMap = parseArgs(args);
    String out = argMap.getOrDefault("--out", System.getProperty("java.io.tmpdir") + "/mm-out");

    // simple: always succeed
    Map<String, Object> result = new HashMap<>();
    result.put("success", true);
    result.put("errors", 0);
    result.put("warnings", 0);
    result.put("report", "Validation OK");
    result.put("nsUris", List.of("http://example.org/dummy"));

    Path outDir = Path.of(out);
    Files.createDirectories(outDir);
        new ObjectMapper()
            .writerWithDefaultPrettyPrinter()
            .writeValue(outDir.resolve("result.json").toFile(), result);
  }

  private static Map<String, String> parseArgs(String[] args) {
    Map<String, String> map = new HashMap<>();
    for (int i = 0; i < args.length - 1; i += 2) map.put(args[i], args[i + 1]);
    return map;
  }
}
