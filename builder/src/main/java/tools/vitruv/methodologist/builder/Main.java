package tools.vitruv.methodologist.builder;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
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
      String pairs = a.get("--pairs");
      if (pairs == null) {
        String ecore = a.get("--ecore");
        String gen = a.get("--genmodel");
        if (ecore == null || gen == null)
          throw new IllegalArgumentException(
              "Provide either --pairs \"ecore,gen;ecore2,gen2\" or both --ecore and --genmodel");
        pairs = ecore + "," + gen;
      }

      // Configure
      VitruvConfiguration cfg = new VitruvConfiguration();
      cfg.setLocalPath(Paths.get(out));
      cfg.setMetaModelLocations(pairs);

      if (cfg.getMetaModelLocations().isEmpty()) {
        throw new IllegalStateException(
            "No valid metamodels found (ecore/genmodel load failed or empty).");
      }

      cfg.getMetaModelLocations()
          .forEach(
              loc -> {
                if (!loc.metamodel().isFile() || loc.metamodel().length() == 0L) {
                  throw new IllegalArgumentException("Ecore is missing/empty: " + loc.metamodel());
                }
                if (!loc.genmodel().isFile() || loc.genmodel().length() == 0L) {
                  throw new IllegalArgumentException(
                      "GenModel is missing/empty: " + loc.genmodel());
                }
              });

      // Generate MWE2
      GenerateFromTemplate gen = new GenerateFromTemplate();
      Path mwe2 = gen.generateMwe2(cfg.getMetaModelLocations(), cfg);

      nsUris = cfg.getMetaModelLocations().stream().map(MetamodelLocation::genmodelUri).toList();

      result.put("success", true);
      result.put("errors", 0);
      result.put("warnings", 0);
      result.put("report", "Generated " + mwe2.getFileName());
      result.put("nsUris", nsUris);

    } catch (Exception e) {
      // mark failure
      result.put("success", false);
      result.put("errors", 1);
      result.put("warnings", 0);
      result.put("report", "Build failed: " + e.getMessage());
      result.put("nsUris", nsUris);
    }

    // Always write result.json
    Path outDir = Paths.get(out);
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
