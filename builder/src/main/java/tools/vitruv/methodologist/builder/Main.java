package tools.vitruv.methodologist.builder;

import static java.nio.file.Files.createDirectories;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import tools.vitruv.methodologist.builder.configuration.MetamodelLocation;
import tools.vitruv.methodologist.builder.configuration.VitruvConfiguration;

/**
 * Entry point for the metamodel builder CLI tool. Supports command-line arguments for providing
 * metamodel definitions and generates a workflow file (MWE2) from the given Ecore and GenModel
 * files. Produces a result.json file containing success, error, and diagnostic information.
 */
public class Main {
  /**
   * Runs the CLI application. Parses arguments, validates input metamodel files, generates workflow
   * configuration, and writes a result.json file with execution details. Exits with code 0 on
   * success or 1 on failure.
   */
  public static void main(String[] args) throws Exception {
    Map<String, String> a = parseArgs(args);
    String out = a.getOrDefault("--out", System.getProperty("java.io.tmpdir") + "/mm-out");

    Map<String, Object> result = new HashMap<>();
    List<String> nsUris = List.of();

    try {
      String pairs = a.get("--pairs");
      if (pairs == null) {
        String ecore = expandPath(a.get("--ecore"));
        String gen = expandPath(a.get("--genmodel"));
        if (ecore == null || gen == null) {
          throw new IllegalArgumentException(
              "Provide either --pairs \"ecore,gen;ecore2,gen2\" or both --ecore and --genmodel");
        }
        pairs = ecore + "," + gen;
      } else {
        StringBuilder sb = new StringBuilder();
        for (String pair : pairs.split(";")) {
          if (pair.isBlank()) {
            continue;
          }
          String[] pg = pair.split(",");
          if (pg.length != 2) {
            throw new IllegalArgumentException("Bad pair: " + pair);
          }
          sb.append(expandPath(pg[0].trim()))
              .append(",")
              .append(expandPath(pg[1].trim()))
              .append(";");
        }
        pairs = sb.toString().replaceAll(";+?$", "");
      }

      VitruvConfiguration cfg = new VitruvConfiguration();
      cfg.setLocalPath(Paths.get(out));
      cfg.setMetaModelLocations(pairs); // loads + basic validation

      if (cfg.getMetaModelLocations().isEmpty()) {
        throw new IllegalStateException(
            "No valid metamodels found (ecore/genmodel load failed or empty).");
      }

      cfg.getMetaModelLocations()
          .forEach(
              loc -> {
                if (!loc.ecore().isFile() || loc.ecore().length() == 0L) {
                  throw new IllegalArgumentException("Ecore is missing/empty: " + loc.ecore());
                }
                if (!loc.genmodel().isFile() || loc.genmodel().length() == 0L) {
                  throw new IllegalArgumentException(
                      "GenModel is missing/empty: " + loc.genmodel());
                }
              });

      nsUris = cfg.getMetaModelLocations().stream().map(MetamodelLocation::nsUri).toList();

      result.put("success", true);
      result.put("errors", 0);
      result.put("warnings", 0);

      Path mwe2 = new GenerateFromTemplate().generateMwe2(cfg.getMetaModelLocations(), cfg);
      result.put("report", "Generated " + mwe2.getFileName());
      result.put("nsUris", nsUris);
      System.out.println(result);

      writeResult(out, result);
      System.exit(0);

    } catch (Exception e) {
      result.put("success", false);
      result.put("errors", 1);
      result.put("warnings", 0);
      result.put("report", "Build failed: " + e.getMessage());
      result.put("nsUris", nsUris);
      System.out.println(result);
      writeResult(out, result);
      System.exit(1);
    }
  }

  /**
   * Expands a path string, replacing '~' with the user's home directory if present. Returns null if
   * the input path is null.
   */
  private static String expandPath(String path) {
    if (path == null) {
      return null;
    }
    if (path.startsWith("~")) {
      return System.getProperty("user.home") + path.substring(1);
    }
    return path;
  }

  /**
   * Writes the result of execution to result.json in the specified output directory. Creates the
   * directory if it does not exist.
   */
  private static void writeResult(String out, Map<String, Object> result) throws Exception {
    Path outDir = Paths.get(out);
    createDirectories(outDir);
    new ObjectMapper()
        .writerWithDefaultPrettyPrinter()
        .writeValue(outDir.resolve("result.json").toFile(), result);
  }

  /**
   * Parses command-line arguments into a map. Assumes arguments are provided as key-value pairs
   * like "--ecore file.ecore".
   */
  private static Map<String, String> parseArgs(String[] args) {
    Map<String, String> parsedArgs = new HashMap<>();
    for (int i = 0; i < args.length - 1; i += 2) {
      parsedArgs.put(args[i], args[i + 1]);
    }
    return parsedArgs;
  }
}
