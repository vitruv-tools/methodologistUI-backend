package tools.vitruv.methodologist.vsum.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.vitruv.methodologist.exception.NotFoundException;
import tools.vitruv.methodologist.general.model.FileStorage;
import tools.vitruv.methodologist.vsum.lowcode.reactions.template.service.LowCodeReactionService;
import tools.vitruv.methodologist.vsum.model.FineGranularMetaModelRelation;
import tools.vitruv.methodologist.vsum.model.MetaModel;
import tools.vitruv.methodologist.vsum.model.MetaModelRelation;
import tools.vitruv.methodologist.vsum.model.Vsum;

class VsumBuildInputCollectorTest {

  private final VsumBuildInputCollector collector =
      new VsumBuildInputCollector(new ReactionBuildCollector(new LowCodeReactionService(null)));

  @Test
  void collect_shouldDeduplicateMetamodels() {
    Vsum vsum = new Vsum();
    FileStorage e1 = fs(10L, "dup.ecore", new byte[] {1});
    FileStorage g1 = fs(11L, "dup.genmodel", new byte[] {2});
    FileStorage r1 = fs(12L, "a.reactions", new byte[] {3});
    MetaModel m1 = mm(e1, g1);
    MetaModel m2 = mm(e1, g1);
    vsum.setMetaModelRelations(
        Set.of(rel(m1, m2, r1), rel(m1, null, fs(13L, "b.reactions", new byte[] {4}))));

    VsumBuildInputs inputs = collector.collect(vsum);

    assertThat(inputs.ecores()).containsExactly(e1);
    assertThat(inputs.genmodels()).containsExactly(g1);
    assertThat(inputs.reactions()).hasSize(2);
  }

  @Test
  void collect_shouldThrowNotFound_whenNoMetaModelRelations() {
    Vsum vsum = new Vsum();
    vsum.setMetaModelRelations(null);

    assertThatThrownBy(() -> collector.collect(vsum)).isInstanceOf(NotFoundException.class);
  }

  @Test
  void collect_shouldThrowNotFound_whenNoReactions() {
    Vsum vsum = new Vsum();
    FileStorage e = fs(1L, "a.ecore", new byte[] {1});
    FileStorage g = fs(2L, "a.genmodel", new byte[] {2});
    vsum.setMetaModelRelations(Set.of(rel(mm(e, g), null, null)));

    assertThatThrownBy(() -> collector.collect(vsum)).isInstanceOf(NotFoundException.class);
  }

  @Test
  void collect_shouldThrowNotFound_whenNoMetamodelPair() {
    Vsum vsum = new Vsum();
    FileStorage r = fs(3L, "x.reactions", new byte[] {3});
    vsum.setMetaModelRelations(Set.of(rel(mm(null, null), null, r)));

    assertThatThrownBy(() -> collector.collect(vsum)).isInstanceOf(NotFoundException.class);
  }

  @Test
  void collect_shouldIncludeFineGranularReaction_whenCoarseReactionMissing() {
    Vsum vsum = new Vsum();
    FileStorage e = fs(1L, "a.ecore", new byte[] {1});
    FileStorage g = fs(2L, "a.genmodel", new byte[] {2});
    FileStorage fgReaction = fs(3L, "fg.reactions", new byte[] {3});
    MetaModelRelation relation = rel(mm(e, g), null, null);
    relation.getFineGranularMetaModelRelationSet().add(fg("Component", "Class", fgReaction));
    vsum.setMetaModelRelations(Set.of(relation));

    VsumBuildInputs inputs = collector.collect(vsum);

    assertThat(inputs.reactions()).containsExactly(fgReaction);
  }

  @Test
  void collect_shouldSendCompositeAndImports_whenPairHasMultipleReactions() {
    FileStorage e = fs(1L, "a.ecore", new byte[] {1});
    FileStorage g = fs(2L, "a.genmodel", new byte[] {2});
    FileStorage first = fs(3L, "first.reactions", reactionBytes("firstReaction"));
    FileStorage second = fs(4L, "second.reactions", reactionBytes("secondReaction"));
    MetaModelRelation relation = rel(mm(e, g), null, null);
    relation.setId(5L);
    relation.getFineGranularMetaModelRelationSet().add(fg("Component", "Class", first));
    relation.getFineGranularMetaModelRelationSet().add(fg("Interface", "Type", second));
    Vsum vsum = new Vsum();
    vsum.setMetaModelRelations(Set.of(relation));

    List<FileStorage> sent = collector.collect(vsum).reactions();

    assertThat(sent).hasSize(3);
    assertThat(sent.get(0).getFilename()).isEqualTo("compositeReaction5.reactions");
    String composite = new String(sent.get(0).getData(), StandardCharsets.UTF_8);
    assertThat(composite)
        .contains("reactions: compositeReaction5")
        .contains("import firstReaction")
        .contains("import secondReaction");
    assertThat(sent.subList(1, sent.size())).containsExactlyInAnyOrder(first, second);
  }

  @Test
  void fingerprint_isStable_andIndependentOfRelationOrder() {
    FileStorage e1 = fs(1L, "a.ecore", new byte[] {1});
    FileStorage g1 = fs(2L, "a.genmodel", new byte[] {2});
    FileStorage e2 = fs(3L, "b.ecore", new byte[] {4});
    FileStorage g2 = fs(4L, "b.genmodel", new byte[] {5});
    FileStorage r1 = fs(5L, "x.reactions", new byte[] {3});
    FileStorage r2 = fs(6L, "y.reactions", new byte[] {6});
    MetaModelRelation ab = rel(mm(e1, g1), mm(e2, g2), r1);
    MetaModelRelation ba = rel(mm(e2, g2), mm(e1, g1), r2);

    // Vsum#getMetaModelRelations is a Set; simulate the two iteration orders explicitly.
    Vsum first = new Vsum();
    first.setMetaModelRelations(orderedSet(ab, ba));
    Vsum second = new Vsum();
    second.setMetaModelRelations(orderedSet(ba, ab));

    String fingerprint = collector.collect(first).fingerprint();

    assertThat(fingerprint).matches("[0-9a-f]{64}");
    assertThat(collector.collect(second).fingerprint()).isEqualTo(fingerprint);
  }

  @Test
  void fingerprint_changesWhenAnyInputByteChanges() {
    FileStorage e = fs(1L, "a.ecore", new byte[] {1});
    FileStorage g = fs(2L, "a.genmodel", new byte[] {2});
    Vsum vsum = new Vsum();
    vsum.setMetaModelRelations(Set.of(rel(mm(e, g), null, fs(3L, "x.reactions", new byte[] {3}))));
    String before = collector.collect(vsum).fingerprint();

    Vsum changed = new Vsum();
    changed.setMetaModelRelations(
        Set.of(rel(mm(e, g), null, fs(3L, "x.reactions", new byte[] {3, 0}))));

    assertThat(collector.collect(changed).fingerprint()).isNotEqualTo(before);
  }

  private static Set<MetaModelRelation> orderedSet(MetaModelRelation... relations) {
    return new LinkedHashSet<>(List.of(relations));
  }

  private FileStorage fs(Long id, String filename, byte[] data) {
    FileStorage f = new FileStorage();
    f.setId(id);
    f.setFilename(filename);
    f.setData(data);
    return f;
  }

  private MetaModel mm(FileStorage ecore, FileStorage gen) {
    MetaModel m = new MetaModel();
    m.setEcoreFile(ecore);
    m.setGenModelFile(gen);
    return m;
  }

  private MetaModelRelation rel(MetaModel source, MetaModel target, FileStorage reaction) {
    MetaModelRelation r = new MetaModelRelation();
    r.setSource(source);
    r.setTarget(target);
    r.setReactionFileStorage(reaction);
    return r;
  }

  private FineGranularMetaModelRelation fg(String source, String target, FileStorage reaction) {
    return FineGranularMetaModelRelation.builder()
        .sourceId(source)
        .targetId(target)
        .reactionFileStorage(reaction)
        .build();
  }

  private byte[] reactionBytes(String reactionName) {
    return """
        import "http://pcm" as pcm
        import "http://uml" as uml

        reactions: %s
        in reaction to changes in pcm
        execute actions in uml
        """
        .formatted(reactionName)
        .getBytes(StandardCharsets.UTF_8);
  }
}
