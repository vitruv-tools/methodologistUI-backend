package tools.vitruv.methodologist.builder;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import tools.vitruv.methodologist.builder.configuration.MetamodelLocation;
import tools.vitruv.methodologist.builder.configuration.VitruvConfiguration;

public class Main {
  public static void main(String[] args) throws Exception {
    Map<String, String> a = parseArgs(args);
    String out = a.getOrDefault("--out", System.getProperty("java.io.tmpdir") + "/mm-out");

    Map<String, Object> result = new HashMap<>();
    List<String> nsUris = List.of();

    try {
      // Accept either --pairs "e1,g1;e2,g2" OR single --ecore + --genmodel
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
        // expand ~ for each path inside --pairs
        StringBuilder sb = new StringBuilder();
        for (String pair : pairs.split(";")) {
          if (pair.isBlank()) continue;
          String[] pg = pair.split(",");
          if (pg.length != 2) throw new IllegalArgumentException("Bad pair: " + pair);
          sb.append(expandPath(pg[0].trim()))
              .append(",")
              .append(expandPath(pg[1].trim()))
              .append(";");
        }
        // remove trailing ';' if present
        pairs = sb.toString().replaceAll(";+?$", "");
      }

      // Configure and load
      VitruvConfiguration cfg = new VitruvConfiguration();
      cfg.setLocalPath(Paths.get(out));
      cfg.setMetaModelLocations(pairs); // loads + basic validation

      // Must have at least one valid pair
      if (cfg.getMetaModelLocations().isEmpty()) {
        throw new IllegalStateException(
            "No valid metamodels found (ecore/genmodel load failed or empty).");
      }

      // Extra cheap file sanity
      cfg.getMetaModelLocations()
          .forEach(
              loc -> {
                if (!loc.ecore().isFile() || loc.ecore().length() == 0L)
                  throw new IllegalArgumentException("Ecore is missing/empty: " + loc.ecore());
                if (!loc.genmodel().isFile() || loc.genmodel().length() == 0L)
                  throw new IllegalArgumentException(
                      "GenModel is missing/empty: " + loc.genmodel());
              });

      // Generate workflow.mwe2
      Path mwe2 = new GenerateFromTemplate().generateMwe2(cfg.getMetaModelLocations(), cfg);

      // Collect nsURIs
      nsUris = cfg.getMetaModelLocations().stream().map(MetamodelLocation::nsUri).toList();

      // Success
      result.put("success", true);
      result.put("errors", 0);
      result.put("warnings", 0);
      result.put("report", "Generated " + mwe2.getFileName());
      result.put("nsUris", nsUris);
      System.out.println(result);

      writeResult(out, result);
      System.exit(0);

    } catch (Exception e) {
      // Failure
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

  private static String expandPath(String p) {
    if (p == null) return null;
    if (p.startsWith("~")) return System.getProperty("user.home") + p.substring(1);
    return p;
  }

  private static void writeResult(String out, Map<String, Object> result) throws Exception {
    var outDir = Paths.get(out);
    java.nio.file.Files.createDirectories(outDir);
    new ObjectMapper()
        .writerWithDefaultPrettyPrinter()
        .writeValue(outDir.resolve("result.json").toFile(), result);
  }

  private static Map<String, String> parseArgs(String[] args) {
    Map<String, String> m = new HashMap<>();
    for (int i = 0; i < args.length - 1; i += 2) m.put(args[i], args[i + 1]);
    return m;
  }
}
