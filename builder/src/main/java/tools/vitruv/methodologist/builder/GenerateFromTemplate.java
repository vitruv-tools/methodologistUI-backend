package tools.vitruv.methodologist.builder;

import freemarker.template.*;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.*;
import java.util.*;
import tools.vitruv.methodologist.builder.configuration.MetamodelLocation;
import tools.vitruv.methodologist.builder.configuration.VitruvConfiguration;

public class GenerateFromTemplate {
  private Configuration cfg() {
    Configuration c = new Configuration(Configuration.VERSION_2_3_31);
    c.setDefaultEncoding("UTF-8");
    c.setClassForTemplateLoading(this.getClass(), "/templates");
    c.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
    return c;
  }

  public Path generateMwe2(List<MetamodelLocation> models, VitruvConfiguration config)
      throws IOException {
    var items = new ArrayList<Map<String, Object>>();
    for (MetamodelLocation m : models) {
      items.add(
          Map.of(
              "targetDir", config.getLocalPath().toString(),
              "packageName",
                  (config.getPackageName() == null ? "generated.model" : config.getPackageName())
                      + ".model",
              "nsUri", m.nsUri(),
              "genmodelPath", m.genmodel().getAbsolutePath()));
    }
    Map<String, Object> data = Map.of("items", items);

    Template t = cfg().getTemplate("generator.ftl");
    Path outDir = config.getLocalPath();
    Files.createDirectories(outDir);
    Path mwe2 = outDir.resolve("workflow.mwe2");

    try (Writer w = new OutputStreamWriter(Files.newOutputStream(mwe2))) {
      t.process(data, w);
    } catch (Exception e) {
      throw new IOException("Failed to render mwe2: " + e.getMessage(), e);
    }
    return mwe2;
  }
}
