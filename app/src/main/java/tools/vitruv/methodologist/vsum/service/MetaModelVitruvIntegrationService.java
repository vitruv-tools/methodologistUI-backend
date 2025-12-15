package tools.vitruv.methodologist.vsum.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import tools.vitruv.methodologist.exception.VsumBuildingException;
import tools.vitruv.methodologist.general.model.FileStorage;
import tools.vitruv.methodologist.vitruvcli.VitruvCliProperties;
import tools.vitruv.methodologist.vitruvcli.VitruvCliService;

/** Service that integrates VSUM-related files with Vitruv-CLI. */
@Service
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MetaModelVitruvIntegrationService {

  VitruvCliService vitruvCliService;
  VitruvCliProperties properties;

  /**
   * Runs Vitruv-CLI for an arbitrary number of metamodels (each consisting of Ecore + GenModel) and
   * one or more reaction files.
   *
   * <p>For each reaction file, Vitruv-CLI is invoked once with the full list of metamodels. The CLI
   * option {@code --metamodel} expects a semicolon-separated list of {@code ecore,genmodel} pairs.
   *
   * @param ecoreFiles list of Ecore files
   * @param genModelFiles list of GenModel files (must have the same size and corresponding order as
   *     {@code ecoreFiles})
   * @param reactionFiles list of reaction files that define the relations between the metamodels
   */
  public void runVitruvForMetaModels(
      List<FileStorage> ecoreFiles,
      List<FileStorage> genModelFiles,
      List<FileStorage> reactionFiles) {

    if (ecoreFiles == null || genModelFiles == null || ecoreFiles.isEmpty()) {
      throw new VsumBuildingException(
          "At least one metamodel (Ecore/GenModel pair) must be provided");
    }
    if (ecoreFiles.size() != genModelFiles.size()) {
      throw new VsumBuildingException("Number of Ecore files must match number of GenModel files");
    }
    if (reactionFiles == null || reactionFiles.isEmpty()) {
      throw new VsumBuildingException("At least one reaction file must be provided");
    }

    for (FileStorage reactionFile : reactionFiles) {
      runVitruvForMetaModels(ecoreFiles, genModelFiles, reactionFile);
    }
  }

  /**
   * Runs Vitruv-CLI for an arbitrary number of metamodels (each consisting of Ecore + GenModel) and
   * a single reaction file.
   *
   * @param ecoreFiles list of Ecore files
   * @param genModelFiles list of GenModel files (must have the same size and corresponding order as
   *     {@code ecoreFiles})
   * @param reactionFile the reaction file that defines the relations between the metamodels
   */
  public void runVitruvForMetaModels(
      List<FileStorage> ecoreFiles, List<FileStorage> genModelFiles, FileStorage reactionFile) {

    if (ecoreFiles == null || genModelFiles == null || ecoreFiles.isEmpty()) {
      throw new VsumBuildingException(
          "At least one metamodel (Ecore/GenModel pair) must be provided");
    }
    if (ecoreFiles.size() != genModelFiles.size()) {
      throw new VsumBuildingException("Number of Ecore files must match number of GenModel files");
    }
    if (reactionFile == null) {
      throw new VsumBuildingException("Reaction file must not be null");
    }

    try {
      Path jobDir = Path.of(properties.getWorkingDir(), "job-" + System.currentTimeMillis());
      Files.createDirectories(jobDir);

      List<VitruvCliService.MetamodelInput> metamodels =
          createMetamodelInputs(jobDir, ecoreFiles, genModelFiles);

      Path reactionPath = jobDir.resolve(reactionFile.getFilename());
      Files.write(reactionPath, reactionFile.getData());

      VitruvCliService.VitruvCliResult result =
          vitruvCliService.run(jobDir, metamodels, reactionPath);

      if (!result.isSuccess()) {
        String msg = result.getStderr().isBlank() ? result.getStdout() : result.getStderr();
        throw new VsumBuildingException("Vitruv-CLI Error: " + msg);
      }

    } catch (IOException e) {
      throw new VsumBuildingException("Vitruv-CLI execution failed: " + e.getMessage());
    }
  }

  /**
   * Convenience overload for the common case of exactly two metamodels and one reaction file.
   *
   * @param firstEcore the first metamodel's Ecore file
   * @param firstGenModel the first metamodel's GenModel file
   * @param secondEcore the second metamodel's Ecore file
   * @param secondGenModel the second metamodel's GenModel file
   * @param reactionFile the reaction file that defines the relation between the metamodels
   */
  public void runVitruvForMetaModels(
      FileStorage firstEcore,
      FileStorage firstGenModel,
      FileStorage secondEcore,
      FileStorage secondGenModel,
      FileStorage reactionFile) {

    runVitruvForMetaModels(
        List.of(firstEcore, secondEcore), List.of(firstGenModel, secondGenModel), reactionFile);
  }

  /**
   * Writes all metamodel files into the given job directory and returns the corresponding {@link
   * VitruvCliService.MetamodelInput} list for the CLI.
   *
   * @param jobDir the job directory where all files are written
   * @param ecoreFiles list of Ecore files
   * @param genModelFiles list of GenModel files
   * @return list of MetamodelInput instances describing the written files
   * @throws IOException if writing any of the files fails
   */
  private List<VitruvCliService.MetamodelInput> createMetamodelInputs(
      Path jobDir, List<FileStorage> ecoreFiles, List<FileStorage> genModelFiles)
      throws IOException {

    List<VitruvCliService.MetamodelInput> inputs = new ArrayList<>(ecoreFiles.size());

    for (int i = 0; i < ecoreFiles.size(); i++) {
      FileStorage ecore = ecoreFiles.get(i);
      FileStorage gen = genModelFiles.get(i);

      Path ecorePath = jobDir.resolve(ecore.getFilename());
      Path genPath = jobDir.resolve(gen.getFilename());

      Files.write(ecorePath, ecore.getData());
      Files.write(genPath, gen.getData());

      inputs.add(
          VitruvCliService.MetamodelInput.builder()
              .ecorePath(ecorePath)
              .genmodelPath(genPath)
              .build());
    }

    return inputs;
  }
}
