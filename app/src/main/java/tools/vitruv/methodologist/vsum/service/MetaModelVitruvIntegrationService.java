package tools.vitruv.methodologist.vsum.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.vitruv.methodologist.exception.VsumBuildingException;
import tools.vitruv.methodologist.general.model.FileStorage;
import tools.vitruv.methodologist.vitruvcli.VitruvCliService;

/** Service that integrates VSUM-related files with Vitruv-CLI. */
@Service
@RequiredArgsConstructor
public class MetaModelVitruvIntegrationService {

  private final VitruvCliService vitruvCliService;

  /**
   * Runs Vitruv-CLI for two metamodels (Ecore + GenModel) and a reaction file.
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

    try {
      Path workDir = Files.createTempDirectory("vitruv-job-");

      Path firstEcorePath = workDir.resolve(firstEcore.getFilename());
      Path firstGenModelPath = workDir.resolve(firstGenModel.getFilename());
      Path secondEcorePath = workDir.resolve(secondEcore.getFilename());
      Path secondGenModelPath = workDir.resolve(secondGenModel.getFilename());
      Path reactionPath = workDir.resolve(reactionFile.getFilename());

      Files.write(firstEcorePath, firstEcore.getData());
      Files.write(firstGenModelPath, firstGenModel.getData());
      Files.write(secondEcorePath, secondEcore.getData());
      Files.write(secondGenModelPath, secondGenModel.getData());
      Files.write(reactionPath, reactionFile.getData());

      List<VitruvCliService.MetamodelInput> metamodels =
          List.of(
              VitruvCliService.MetamodelInput.builder()
                  .ecorePath(firstEcorePath)
                  .genmodelPath(firstGenModelPath)
                  .build(),
              VitruvCliService.MetamodelInput.builder()
                  .ecorePath(secondEcorePath)
                  .genmodelPath(secondGenModelPath)
                  .build());

      VitruvCliService.VitruvCliResult result =
          vitruvCliService.run(workDir, metamodels, reactionPath);

      if (!result.isSuccess()) {
        String message = result.getStderr().isBlank() ? result.getStdout() : result.getStderr();
        throw new VsumBuildingException(String.format("Vitruv-CLI Error: %s", message));
      }
    } catch (IOException e) {
      throw new VsumBuildingException(
          String.format("Vitruv-CLI execution failed: %s", e.getMessage()));
    }
  }
}
