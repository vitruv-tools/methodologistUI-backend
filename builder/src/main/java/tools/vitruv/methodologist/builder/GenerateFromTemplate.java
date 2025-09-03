package tools.vitruv.methodologist.builder;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import tools.vitruv.methodologist.builder.configuration.MetamodelLocation;
import tools.vitruv.methodologist.builder.configuration.VitruvConfiguration;

/**
 * Utility for generating workflow configuration files from FreeMarker templates. It prepares
 * template data from metamodel locations and a Vitruv configuration, then renders the workflow
 * definition to a file on disk.
 */
public class GenerateFromTemplate {
  /**
   * Creates and configures a FreeMarker configuration instance. Uses UTF-8 encoding, loads
   * templates from the classpath under /templates, and throws exceptions on template errors.
   */
  private Configuration cfg() {
    Configuration c = new Configuration(Configuration.VERSION_2_3_31);
    c.setDefaultEncoding("UTF-8");
    c.setClassForTemplateLoading(this.getClass(), "/templates");
    c.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
    return c;
  }

  /**
   * Generates a workflow.mwe2 file from the generator.ftl template. Collects all metamodel
   * locations and configuration values, builds a data model for the template, and writes the
   * rendered file to the configured local output directory. Creates directories if they do not
   * exist. Returns the path to the generated workflow file, or throws an IOException if rendering
   * fails.
   */
  public Path generateMwe2(List<MetamodelLocation> models, VitruvConfiguration config)
      throws IOException {
    List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
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
