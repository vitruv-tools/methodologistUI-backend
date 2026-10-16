package tools.vitruv.methodologist.vsum.service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import tools.vitruv.methodologist.exception.VsumBuildingException;
import tools.vitruv.methodologist.general.FileEnumType;
import tools.vitruv.methodologist.general.model.FileStorage;
import tools.vitruv.methodologist.vsum.lowcode.reactions.template.dto.request.CompositeReactionsRequest;
import tools.vitruv.methodologist.vsum.lowcode.reactions.template.service.LowCodeReactionService;
import tools.vitruv.methodologist.vsum.model.FineGranularMetaModelRelation;
import tools.vitruv.methodologist.vsum.model.MetaModelRelation;
import tools.vitruv.methodologist.vsum.reaction.ReactionParserUtil;

/**
 * Collects reaction files for a VSUM JAR build. A pair with one file is sent as-is. A pair with
 * several files is wrapped in a generated composite that imports each reaction.
 */
@Service
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ReactionBuildCollector {
  LowCodeReactionService lowCodeReactionService;

  /**
   * Build inputs for one coarse relation: import files plus an optional composite wrapper.
   *
   * @param additionalReactionFiles reaction files to send as imports (empty when the pair has one
   *     file)
   * @param compositeReactionFile the file setup-service should treat as the pair's reaction; the
   *     single file when there is only one, or a generated composite when there are several
   */
  public record BuildParameters(
      ArrayList<FileStorage> additionalReactionFiles, FileStorage compositeReactionFile) {}

  /**
   * Collects reaction files for {@code relation} in setup-service order: composite (if any), then
   * imported files.
   *
   * @param relation the coarse meta-model relation
   * @return reaction files to include in the JAR build for this pair
   */
  public List<FileStorage> collectForRelation(MetaModelRelation relation) {
    BuildParameters result = getBuildParameters(relation);
    List<FileStorage> files = new ArrayList<>();
    if (result.compositeReactionFile() != null) {
      files.add(result.compositeReactionFile());
    }
    files.addAll(result.additionalReactionFiles());
    return files;
  }

  /**
   * Resolves coarse and fine-granular reaction files for {@code relation}. One file is returned as
   * the composite with no extra imports. Several files are validated to share the same model pair
   * and wrapped with {@code composite_reactions.ftl}.
   *
   * @param relation the coarse meta-model relation
   * @return build parameters for this pair
   */
  public BuildParameters getBuildParameters(MetaModelRelation relation) {
    ArrayList<FileStorage> additionalReactionFiles = collectUniqueReactionFiles(relation);
    if (additionalReactionFiles.isEmpty()) {
      return new BuildParameters(additionalReactionFiles, null);
    }
    if (additionalReactionFiles.size() == 1) {
      FileStorage single = additionalReactionFiles.remove(0);
      return new BuildParameters(additionalReactionFiles, single);
    }
    return wrapWithComposite(relation, additionalReactionFiles);
  }

  private ArrayList<FileStorage> collectUniqueReactionFiles(MetaModelRelation relation) {
    LinkedHashMap<String, FileStorage> unique = new LinkedHashMap<>();
    Set<FineGranularMetaModelRelation> fineGranularSet =
        relation.getFineGranularMetaModelRelationSet();
    if (fineGranularSet != null) {
      for (FineGranularMetaModelRelation fineGranular : fineGranularSet) {
        putUnique(unique, fineGranular.getReactionFileStorage());
      }
    }
    putUnique(unique, relation.getReactionFileStorage());
    return new ArrayList<>(unique.values());
  }

  private static void putUnique(LinkedHashMap<String, FileStorage> unique, FileStorage file) {
    if (file == null) {
      return;
    }
    unique.putIfAbsent(fileKey(file), file);
  }

  private static String fileKey(FileStorage file) {
    if (file.getId() != null) {
      return "id:" + file.getId();
    }
    return "name:" + (file.getFilename() == null ? "" : file.getFilename());
  }

  private BuildParameters wrapWithComposite(
      MetaModelRelation relation, ArrayList<FileStorage> additionalReactionFiles) {
    ReactionParserUtil.ReactionFileInfo reactionFileInfo =
        parseOrThrow(additionalReactionFiles.get(0));
    List<String> imports =
        additionalReactionFiles.stream()
            .map(fileStorage -> parseAndValidate(reactionFileInfo, fileStorage))
            .toList();
    List<String> duplicates =
        imports.stream()
            .filter(name -> Collections.frequency(imports, name) > 1)
            .distinct()
            .toList();
    if (!duplicates.isEmpty()) {
      throw new VsumBuildingException(
          String.format("Reaction names must be unique. Found duplicates: %s", duplicates));
    }

    String reactionName = compositeReactionName(relation);
    CompositeReactionsRequest compositeReactionsRequest = new CompositeReactionsRequest();
    compositeReactionsRequest.setRegenerate(true);
    compositeReactionsRequest.setModel1Uri(reactionFileInfo.modelUri1());
    compositeReactionsRequest.setModel2Uri(reactionFileInfo.modelUri2());
    compositeReactionsRequest.setModel1Alias(reactionFileInfo.modelAlias1());
    compositeReactionsRequest.setModel2Alias(reactionFileInfo.modelAlias2());
    compositeReactionsRequest.setReactionName(reactionName);
    compositeReactionsRequest.setImports(imports.toArray(new String[0]));

    String compositeReactionContent =
        lowCodeReactionService.applyTemplate(compositeReactionsRequest);
    FileStorage compositeReactionFile =
        FileStorage.builder()
            .data(compositeReactionContent.getBytes(StandardCharsets.UTF_8))
            .filename(reactionName + ".reactions")
            .type(FileEnumType.REACTION)
            .contentType("text/plain")
            .build();
    return new BuildParameters(additionalReactionFiles, compositeReactionFile);
  }

  private static String compositeReactionName(MetaModelRelation relation) {
    if (relation.getId() == null) {
      return "compositeReaction";
    }
    return "compositeReaction" + relation.getId();
  }

  private static String parseAndValidate(
      ReactionParserUtil.ReactionFileInfo expected, FileStorage fileStorage) {
    ReactionParserUtil.ReactionFileInfo actual = parseOrThrow(fileStorage);
    requireSame(
        expected.modelAlias1(),
        actual.modelAlias1(),
        expected.reactionName(),
        actual.reactionName(),
        "source model alias");
    requireSame(
        expected.modelAlias2(),
        actual.modelAlias2(),
        expected.reactionName(),
        actual.reactionName(),
        "target model alias");
    requireSame(
        expected.modelUri1(),
        actual.modelUri1(),
        expected.reactionName(),
        actual.reactionName(),
        "source model uri");
    requireSame(
        expected.modelUri2(),
        actual.modelUri2(),
        expected.reactionName(),
        actual.reactionName(),
        "target model uri");
    return actual.reactionName();
  }

  private static void requireSame(
      String expected,
      String actual,
      String expectedReactionName,
      String actualReactionName,
      String fieldLabel) {
    if (Objects.equals(expected, actual)) {
      return;
    }
    throw new VsumBuildingException(
        String.format(
            "All reaction files must be between the same pair of models. Found %s %s in"
                + " reaction %s, but %s %s in reaction %s!",
            fieldLabel, expected, expectedReactionName, fieldLabel, actual, actualReactionName));
  }

  private static ReactionParserUtil.ReactionFileInfo parseOrThrow(FileStorage fileStorage) {
    byte[] data = fileStorage.getData();
    if (data == null || data.length == 0) {
      throw new VsumBuildingException("Reaction file is empty: " + fileStorage.getFilename());
    }
    ReactionParserUtil.ReactionFileInfo info =
        ReactionParserUtil.parse(new String(data, StandardCharsets.UTF_8));
    if (info.reactionName() == null
        || info.modelAlias1() == null
        || info.modelAlias2() == null
        || info.modelUri1() == null
        || info.modelUri2() == null) {
      throw new VsumBuildingException(
          "Could not parse reaction file: " + fileStorage.getFilename());
    }
    return info;
  }
}
