package tools.vitruv.methodologist.vsum.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import tools.vitruv.methodologist.exception.VsumBuildingException;
import tools.vitruv.methodologist.general.model.FileStorage;
import tools.vitruv.methodologist.vsum.lowcode.reactions.template.service.LowCodeReactionService;
import tools.vitruv.methodologist.vsum.model.FineGranularMetaModelRelation;
import tools.vitruv.methodologist.vsum.model.MetaModelRelation;

/** Unit tests for {@link ReactionBuildCollector}. */
class ReactionBuildCollectorTest {
  private final ReactionBuildCollector collector =
      new ReactionBuildCollector(new LowCodeReactionService(null));

  @Test
  void getBuildParameters_emptyPair_returnsNoFiles() {
    MetaModelRelation relation = new MetaModelRelation();

    ReactionBuildCollector.BuildParameters params = collector.getBuildParameters(relation);

    assertThat(params.additionalReactionFiles()).isEmpty();
    assertThat(params.compositeReactionFile()).isNull();
    assertThat(collector.collectForRelation(relation)).isEmpty();
  }

  @Test
  void getBuildParameters_singleFineGranularFile_returnsThatFileAsComposite() {
    FileStorage reaction = reactionFile(1L, "createRoot", "createRoot.reactions");
    MetaModelRelation relation = new MetaModelRelation();
    relation.getFineGranularMetaModelRelationSet().add(fg("Component", "Class", reaction));

    ReactionBuildCollector.BuildParameters params = collector.getBuildParameters(relation);

    assertThat(params.additionalReactionFiles()).isEmpty();
    assertThat(params.compositeReactionFile()).isSameAs(reaction);
    assertThat(collector.collectForRelation(relation)).containsExactly(reaction);
  }

  @Test
  void getBuildParameters_singleCoarseFile_returnsThatFileAsComposite() {
    FileStorage reaction = reactionFile(2L, "coarse", "coarse.reactions");
    MetaModelRelation relation = new MetaModelRelation();
    relation.setReactionFileStorage(reaction);

    assertThat(collector.collectForRelation(relation)).containsExactly(reaction);
  }

  @Test
  void getBuildParameters_multipleFiles_wrapsWithCompositeImports() {
    FileStorage first = reactionFile(3L, "firstReaction", "first.reactions");
    FileStorage second = reactionFile(4L, "secondReaction", "second.reactions");
    MetaModelRelation relation = new MetaModelRelation();
    relation.setId(9L);
    relation.getFineGranularMetaModelRelationSet().add(fg("Component", "Class", first));
    relation.getFineGranularMetaModelRelationSet().add(fg("Interface", "Type", second));

    ReactionBuildCollector.BuildParameters params = collector.getBuildParameters(relation);

    assertThat(params.additionalReactionFiles()).containsExactlyInAnyOrder(first, second);
    FileStorage composite = params.compositeReactionFile();
    assertThat(composite.getFilename()).isEqualTo("compositeReaction9.reactions");
    String content = new String(composite.getData(), StandardCharsets.UTF_8);
    assertThat(content).contains("import \"http://pcm\" as pcm");
    assertThat(content).contains("import \"http://uml\" as uml");
    assertThat(content).contains("reactions: compositeReaction9");
    assertThat(content).contains("import firstReaction");
    assertThat(content).contains("import secondReaction");
  }

  @Test
  void getBuildParameters_duplicateFileIds_areDeduplicated() {
    FileStorage shared = reactionFile(5L, "sharedReaction", "shared.reactions");
    MetaModelRelation relation = new MetaModelRelation();
    relation.getFineGranularMetaModelRelationSet().add(fg("A", "B", shared));
    relation.getFineGranularMetaModelRelationSet().add(fg("C", "D", shared));
    relation.setReactionFileStorage(shared);

    assertThat(collector.collectForRelation(relation)).containsExactly(shared);
  }

  @Test
  void getBuildParameters_aliasMismatch_throwsVsumBuildingException() {
    FileStorage first = reactionFile(6L, "firstReaction", "first.reactions");
    FileStorage second =
        storage(
            7L,
            "second.reactions",
            """
            import "http://pcm" as pcm
            import "http://uml" as other

            reactions: secondReaction
            in reaction to changes in pcm
            execute actions in other
            """);
    MetaModelRelation relation = new MetaModelRelation();
    relation.getFineGranularMetaModelRelationSet().add(fg("A", "B", first));
    relation.getFineGranularMetaModelRelationSet().add(fg("C", "D", second));

    assertThatThrownBy(() -> collector.getBuildParameters(relation))
        .isInstanceOf(VsumBuildingException.class)
        .hasMessageContaining("same pair of models");
  }

  @Test
  void getBuildParameters_duplicateReactionNames_throwsVsumBuildingException() {
    FileStorage first = reactionFile(8L, "sameName", "first.reactions");
    FileStorage second = reactionFile(9L, "sameName", "second.reactions");
    MetaModelRelation relation = new MetaModelRelation();
    relation.getFineGranularMetaModelRelationSet().add(fg("A", "B", first));
    relation.getFineGranularMetaModelRelationSet().add(fg("C", "D", second));

    assertThatThrownBy(() -> collector.getBuildParameters(relation))
        .isInstanceOf(VsumBuildingException.class)
        .hasMessageContaining("Reaction names must be unique");
  }

  private static FineGranularMetaModelRelation fg(
      String source, String target, FileStorage reaction) {
    return FineGranularMetaModelRelation.builder()
        .sourceId(source)
        .targetId(target)
        .reactionFileStorage(reaction)
        .build();
  }

  private static FileStorage reactionFile(Long id, String reactionName, String filename) {
    return storage(
        id,
        filename,
        """
        import "http://pcm" as pcm
        import "http://uml" as uml

        reactions: %s
        in reaction to changes in pcm
        execute actions in uml
        """
            .formatted(reactionName));
  }

  private static FileStorage storage(Long id, String filename, String content) {
    FileStorage file = new FileStorage();
    file.setId(id);
    file.setFilename(filename);
    file.setData(content.getBytes(StandardCharsets.UTF_8));
    return file;
  }
}
