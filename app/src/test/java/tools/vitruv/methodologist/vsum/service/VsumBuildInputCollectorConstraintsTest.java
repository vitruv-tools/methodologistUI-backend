package tools.vitruv.methodologist.vsum.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.vitruv.methodologist.general.FileEnumType;
import tools.vitruv.methodologist.general.model.FileStorage;
import tools.vitruv.methodologist.vsum.lowcode.reactions.template.service.LowCodeReactionService;
import tools.vitruv.methodologist.vsum.model.ConstraintRuleSet;
import tools.vitruv.methodologist.vsum.model.MetaModel;
import tools.vitruv.methodologist.vsum.model.MetaModelRelation;
import tools.vitruv.methodologist.vsum.model.Vsum;
import tools.vitruv.methodologist.vsum.model.repository.ConstraintRuleSetRepository;

/** Tests that the constraint rule sets of a VSUM become build inputs. */
class VsumBuildInputCollectorConstraintsTest {
  private static final long VSUM_ID = 7L;

  private final ConstraintRuleSetRepository ruleSetRepository =
      mock(ConstraintRuleSetRepository.class);
  private final VsumBuildInputCollector collector =
      new VsumBuildInputCollector(
          new ReactionBuildCollector(new LowCodeReactionService(null)), ruleSetRepository);

  private Vsum vsum;

  @BeforeEach
  void setUp() {
    vsum = new Vsum();
    vsum.setId(VSUM_ID);
    MetaModel metaModel = new MetaModel();
    metaModel.setEcoreFile(fs(1L, "a.ecore", "ecore"));
    metaModel.setGenModelFile(fs(2L, "a.genmodel", "genmodel"));
    MetaModelRelation relation = new MetaModelRelation();
    relation.setSource(metaModel);
    relation.setReactionFileStorage(fs(3L, "a.reactions", "reactions"));
    vsum.setMetaModelRelations(Set.of(relation));
  }

  @Test
  void eachRuleSetBecomesAnOclFileNamedLikeTheRuleSet() {
    when(ruleSetRepository.findByVsumId(VSUM_ID))
        .thenReturn(
            List.of(
                ruleSet(2L, "Preise & Regeln", "context m::Item inv a: true"),
                ruleSet(1L, "Komponenten", "context m::Component inv b: true")));

    VsumBuildInputs inputs = collector.collect(vsum);

    assertThat(inputs.constraints())
        .extracting(FileStorage::getFilename)
        .containsExactly("Komponenten.ocl", "Preise & Regeln.ocl");
    assertThat(new String(inputs.constraints().get(1).getData(), StandardCharsets.UTF_8))
        .isEqualTo("context m::Item inv a: true");
    assertThat(inputs.constraints()).allMatch(file -> file.getType() == FileEnumType.OCL);
  }

  @Test
  void skipsRuleSetsWithoutConstraints() {
    ConstraintRuleSet withoutFile = ruleSet(3L, "Leer", "x");
    withoutFile.setOclFile(null);
    when(ruleSetRepository.findByVsumId(VSUM_ID))
        .thenReturn(List.of(ruleSet(1L, "Blank", "  \n"), withoutFile));

    assertThat(collector.collect(vsum).constraints()).isEmpty();
  }

  @Test
  void fingerprintChangesWithRuleSetContentAndName() {
    when(ruleSetRepository.findByVsumId(VSUM_ID))
        .thenReturn(List.of(ruleSet(1L, "Regeln", "context m::A inv a: true")));
    String original = collector.collect(vsum).fingerprint();

    when(ruleSetRepository.findByVsumId(VSUM_ID))
        .thenReturn(List.of(ruleSet(1L, "Regeln", "context m::A inv a: false")));
    String changedContent = collector.collect(vsum).fingerprint();

    when(ruleSetRepository.findByVsumId(VSUM_ID))
        .thenReturn(List.of(ruleSet(1L, "Neue Regeln", "context m::A inv a: true")));
    String renamed = collector.collect(vsum).fingerprint();

    when(ruleSetRepository.findByVsumId(VSUM_ID)).thenReturn(List.of());
    String withoutRuleSets = collector.collect(vsum).fingerprint();

    assertThat(List.of(original, changedContent, renamed, withoutRuleSets)).doesNotHaveDuplicates();
  }

  @Test
  void ruleSetsWithEqualNamesAreOrderedById() {
    when(ruleSetRepository.findByVsumId(VSUM_ID))
        .thenReturn(
            List.of(
                ruleSet(9L, "Regeln", "context m::B inv b: true"),
                ruleSet(4L, "Regeln", "context m::A inv a: true")));

    List<FileStorage> constraints = collector.collect(vsum).constraints();

    assertThat(constraints)
        .extracting(FileStorage::getFilename)
        .containsExactly("Regeln.ocl", "Regeln.ocl");
    assertThat(new String(constraints.get(0).getData(), StandardCharsets.UTF_8))
        .isEqualTo("context m::A inv a: true");
    assertThat(constraints).allMatch(file -> file.getId() == null);
  }

  @Test
  void reportsTheNamesOfTheRuleSetsWithConstraints() {
    ConstraintRuleSet unnamed = ruleSet(4L, "x", "context m::C inv c: true");
    unnamed.setName(" ");
    when(ruleSetRepository.findByVsumId(VSUM_ID))
        .thenReturn(
            List.of(
                ruleSet(1L, "Preise", "context m::A inv a: true"),
                ruleSet(2L, "Leer", " "),
                ruleSet(3L, "Komponenten", "context m::B inv b: true"),
                unnamed));

    assertThat(collector.collectConstraintRuleSetNames(vsum))
        .containsExactly("(unnamed)", "Komponenten", "Preise");
  }

  @Test
  void buildsWithoutRuleSets() {
    when(ruleSetRepository.findByVsumId(VSUM_ID)).thenReturn(List.of());

    assertThat(collector.collect(vsum).constraints()).isEmpty();
  }

  private static ConstraintRuleSet ruleSet(Long id, String name, String content) {
    return ConstraintRuleSet.builder()
        .id(id)
        .name(name)
        .oclFile(fs(100L + id, name.replace(' ', '_') + ".ocl", content))
        .build();
  }

  private static FileStorage fs(Long id, String filename, String content) {
    FileStorage file = new FileStorage();
    file.setId(id);
    file.setFilename(filename);
    file.setData(content.getBytes(StandardCharsets.UTF_8));
    return file;
  }
}
