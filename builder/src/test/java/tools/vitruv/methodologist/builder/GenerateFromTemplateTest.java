package tools.vitruv.methodologist.builder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.vitruv.methodologist.builder.configuration.MetamodelLocation;
import tools.vitruv.methodologist.builder.configuration.VitruvConfiguration;

class GenerateFromTemplateTest {

  @Test
  void generateMwe2_createsFileAndWritesContent() throws IOException {
    Path temporaryDirectory = Files.createTempDirectory("gen-template-ok");
    File ecoreFile = File.createTempFile("model", ".ecore");
    File genmodelFile = File.createTempFile("model", ".genmodel");

    MetamodelLocation metamodelLocation =
        new MetamodelLocation(ecoreFile, genmodelFile, "http://example/test");

    VitruvConfiguration vitruvConfiguration = new VitruvConfiguration();
    vitruvConfiguration.setLocalPath(temporaryDirectory);
    vitruvConfiguration.setPackageName("com.example");

    GenerateFromTemplate generateFromTemplate = new GenerateFromTemplate();
    Path generatedPath =
        generateFromTemplate.generateMwe2(List.of(metamodelLocation), vitruvConfiguration);

    assertTrue(Files.exists(generatedPath));
    assertEquals("workflow.mwe2", generatedPath.getFileName().toString());

    String content = Files.readString(generatedPath, StandardCharsets.UTF_8);
    assertTrue(content.contains("Generated workflow"));
    assertTrue(content.contains("module GeneratedWorkflow"));
    assertTrue(content.contains("http://example/test"));
    assertTrue(content.contains(genmodelFile.getAbsolutePath()));
    assertTrue(content.contains(temporaryDirectory.toString()));
  }
}
