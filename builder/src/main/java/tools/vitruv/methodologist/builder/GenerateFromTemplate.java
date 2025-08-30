package tools.vitruv.methodologist.builder;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import tools.vitruv.methodologist.builder.configuration.MetamodelLocation;
import tools.vitruv.methodologist.builder.configuration.VitruvConfiguration;

public class GenerateFromTemplate {
  private Configuration getConfiguration() {
    Configuration cfg = new Configuration(Configuration.VERSION_2_3_31);
    cfg.setDefaultEncoding("UTF-8");
    cfg.setClassForTemplateLoading(this.getClass(), "/templates");
    cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
    cfg.setLogTemplateExceptions(false);
    cfg.setWrapUncheckedExceptions(true);
    return cfg;
  }

  /** Generate an .mwe2 file at {config.localPath}/workflow.mwe2 */
  public Path generateMwe2(List<MetamodelLocation> models, VitruvConfiguration config)
      throws IOException {
    Configuration cfg = getConfiguration();

    List<Map<String, Object>> items = new ArrayList<>();
    for (MetamodelLocation model : models) {
      items.add(
          Map.of(
              "targetDir", config.getLocalPath().toString().replaceAll("\\s", ""),
              "modelName", model.genmodel().getName(),
              "packageName",
                  (config.getPackageName() == null ? "generated.model" : config.getPackageName())
                      + ".model",
              "nsUri", model.genmodelUri()));
    }

    Map<String, Object> data = new HashMap<>();
    data.put("items", items);

    Template template = cfg.getTemplate("generator.ftl");

    Path outDir = config.getLocalPath();
    Files.createDirectories(outDir);
    Path mwe2 = outDir.resolve("workflow.mwe2");

    try (Writer w = new OutputStreamWriter(Files.newOutputStream(mwe2))) {
      template.process(data, w);
    } catch (Exception e) {
      throw new IOException("Failed to render mwe2: " + e.getMessage(), e);
    }
    return mwe2;
  }
}
