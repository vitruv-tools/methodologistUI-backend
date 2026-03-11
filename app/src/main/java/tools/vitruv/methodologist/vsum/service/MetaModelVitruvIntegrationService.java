package tools.vitruv.methodologist.vsum.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import freemarker.template.TemplateException;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import tools.vitruv.methodologist.exception.VsumBuildingException;
import tools.vitruv.methodologist.general.FileEnumType;
import tools.vitruv.methodologist.general.model.FileStorage;
import tools.vitruv.methodologist.vitruvcli.VitruvCliService;
import tools.vitruv.methodologist.vsum.lowcode.reactions.template.dto.request.CompositeReactionsRequest;
import tools.vitruv.methodologist.vsum.lowcode.reactions.template.service.LowCodeReactionService;
import tools.vitruv.methodologist.vsum.model.FineGranularMetaModelRelation;
import tools.vitruv.methodologist.vsum.model.MetaModelRelation;
import tools.vitruv.methodologist.vsum.reaction.ReactionParserUtil;

/** Service that integrates VSUM-related files with Vitruv-CLI. */
@Service
@RequiredArgsConstructor
public class MetaModelVitruvIntegrationService {

  private final VitruvCliService vitruvCliService;
  private final LowCodeReactionService lowCodeReactionService;

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

    runVitruvForMetaModels(firstEcore, firstGenModel, secondEcore, secondGenModel, reactionFile, List.of());
  }

  /**
   * Runs Vitruv-CLI for two metamodels (Ecore + GenModel) and a reaction file.
   *
   * @param firstEcore the first metamodel's Ecore file
   * @param firstGenModel the first metamodel's GenModel file
   * @param secondEcore the second metamodel's Ecore file
   * @param secondGenModel the second metamodel's GenModel file
   * @param compositeReactionFile the composite reaction file that defines the relation between the metamodels
   * @param additionalReactionFiles additional reaction files that should be used for the build
   */
  public void runVitruvForMetaModels(
      FileStorage firstEcore,
      FileStorage firstGenModel,
      FileStorage secondEcore,
      FileStorage secondGenModel,
      FileStorage compositeReactionFile,
      List<FileStorage> additionalReactionFiles) {

    try {
      Path workDir = Files.createTempDirectory("vitruv-job-");

      Path firstEcorePath = workDir.resolve(firstEcore.getFilename());
      Path firstGenModelPath = workDir.resolve(firstGenModel.getFilename());
      Path secondEcorePath = workDir.resolve(secondEcore.getFilename());
      Path secondGenModelPath = workDir.resolve(secondGenModel.getFilename());
      Path reactionPath = ensureReactionPathEndsWithProperExtension(workDir.resolve(compositeReactionFile.getFilename()));
      Map<FileStorage, Path> additionalReactionPaths = additionalReactionFiles.stream().map(arf -> new AbstractMap.SimpleEntry<>(arf, workDir.resolve(arf.getFilename()))).collect(Collectors.toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue));

      Files.write(firstEcorePath, firstEcore.getData());
      Files.write(firstGenModelPath, firstGenModel.getData());
      Files.write(secondEcorePath, secondEcore.getData());
      Files.write(secondGenModelPath, secondGenModel.getData());
      Files.write(reactionPath, compositeReactionFile.getData());
      for (Map.Entry<FileStorage, Path> entry : additionalReactionPaths.entrySet()) {
        FileStorage fs = entry.getKey();
        Path path = ensureReactionPathEndsWithProperExtension(entry.getValue());
        Files.write(path, fs.getData());
      }

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

  private Path ensureReactionPathEndsWithProperExtension(Path path) {
    if (!path.getFileName().toString().endsWith(".reactions")) {
      String filename = path.getFileName().toString();
      int dotIndex = filename.lastIndexOf('.');
      String nameWithoutExtension = (dotIndex == -1) ? filename : filename.substring(0, dotIndex);
      return path.resolveSibling(nameWithoutExtension + ".reactions");
    }
    return path;
  }

  public @NonNull BuildParameters getBuildParameters(MetaModelRelation relation) {
    ArrayList<FileStorage> additionalReactionFiles = new ArrayList<>(relation.getFineGranularMetaModelRelationSet().stream().map(FineGranularMetaModelRelation::getReactionFileStorage).filter(Objects::nonNull).toList());
    if (relation.getReactionFileStorage() != null) {
      additionalReactionFiles.add(relation.getReactionFileStorage());
    }
    FileStorage compositeReactionFile;
    if (additionalReactionFiles.size() == 1) {
      compositeReactionFile = additionalReactionFiles.get(0);
      additionalReactionFiles.remove(0);
    } else {
      //TODO: this is a quick and dirty way to get the required information, but there is simply no other way to get it without actually parsing the file.
      ReactionParserUtil.ReactionFileInfo reactionFileInfo = ReactionParserUtil.parse(new String(additionalReactionFiles.get(0).getData(), StandardCharsets.UTF_8));
      CompositeReactionsRequest compositeReactionsRequest = new CompositeReactionsRequest();
      compositeReactionsRequest.setRegenerate(true);
      compositeReactionsRequest.setModel1Uri(reactionFileInfo.modelUri1());
      compositeReactionsRequest.setModel2Uri(reactionFileInfo.modelUri2());
      compositeReactionsRequest.setModel1Alias(reactionFileInfo.modelAlias1());
      compositeReactionsRequest.setModel2Alias(reactionFileInfo.modelAlias2());
      compositeReactionsRequest.setReactionName("compositeReaction");
      var imports = additionalReactionFiles.stream().map(fileStorage -> {
        var importReactionFileInfo = ReactionParserUtil.parse(new String(fileStorage.getData(), StandardCharsets.UTF_8));
        if (!Objects.equals(reactionFileInfo.modelAlias1(), importReactionFileInfo.modelAlias1())) {
          throw new RuntimeException(
                  String.format(
                          "All reaction files must be between the same pair of model aliases. Found source model alias %s in reaction %s, but source model alias %s in reaction %s!",
                          reactionFileInfo.modelAlias1(),
                          reactionFileInfo.reactionName(),
                          importReactionFileInfo.modelAlias1(),
                          importReactionFileInfo.reactionName()
                  )
          );
        }
        if (!Objects.equals(reactionFileInfo.modelAlias2(), importReactionFileInfo.modelAlias2())) {
          throw new RuntimeException(
                  String.format(
                          "All reaction files must be between the same pair of model aliases. Found target model alias %s in reaction %s, but target model alias %s in reaction %s!",
                          reactionFileInfo.modelAlias2(),
                          reactionFileInfo.reactionName(),
                          importReactionFileInfo.modelAlias2(),
                          importReactionFileInfo.reactionName()
                  )
          );
        }
        if (!Objects.equals(reactionFileInfo.modelUri1(), importReactionFileInfo.modelUri1())) {
          throw new RuntimeException(
                  String.format(
                          "All reaction files must be between the same pair of model uris. Found source model uri %s in reaction %s, but source model uri %s in reaction %s!",
                          reactionFileInfo.modelUri1(),
                          reactionFileInfo.reactionName(),
                          importReactionFileInfo.modelUri1(),
                          importReactionFileInfo.reactionName()
                  )
          );
        }
        if (!Objects.equals(reactionFileInfo.modelUri2(), importReactionFileInfo.modelUri2())) {
          throw new RuntimeException(
                  String.format(
                          "All reaction files must be between the same pair of model uris. Found target model uri %s in reaction %s, but target model uri %s in reaction %s!",
                          reactionFileInfo.modelUri2(),
                          reactionFileInfo.reactionName(),
                          importReactionFileInfo.modelUri2(),
                          importReactionFileInfo.reactionName()
                  )
          );
        }
        return importReactionFileInfo.reactionName();
      }).toList();
      List<String> duplicates =
              imports.stream()
                      .filter(e -> Collections.frequency(imports, e) > 1)
                      .distinct()
                      .toList();
      if (!duplicates.isEmpty()) {
        throw new RuntimeException(String.format("Reaction names must be unique. Found duplicates: %s", duplicates));
      }
      compositeReactionsRequest.setImports(imports.toArray(new String[0]));

      try {
        var compositeReactionContent = lowCodeReactionService.applyTemplate(compositeReactionsRequest);
        compositeReactionFile = FileStorage
                .builder()
                .data(compositeReactionContent
                        .getBytes(StandardCharsets.UTF_8))
                .filename("compositeReaction.reactions")
                .type(FileEnumType.REACTION)
                .contentType("text/plain")
                .build();
      } catch (IOException | TemplateException e) {
        throw new RuntimeException(e);
      }
    }
    return new BuildParameters(additionalReactionFiles, compositeReactionFile);
  }

  public record BuildParameters(ArrayList<FileStorage> additionalReactionFiles, FileStorage compositeReactionFile) {
  }
}
