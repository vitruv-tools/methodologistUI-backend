package tools.vitruv.methodologist.vsum.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.vitruv.methodologist.general.model.FileStorage;
import tools.vitruv.methodologist.vsum.lowcode.reactions.template.service.LowCodeReactionService;
import tools.vitruv.methodologist.vsum.model.ConstraintRuleSet;
import tools.vitruv.methodologist.vsum.model.MetaModel;
import tools.vitruv.methodologist.vsum.model.MetaModelRelation;
import tools.vitruv.methodologist.vsum.model.Vsum;
import tools.vitruv.methodologist.vsum.model.repository.ConstraintRuleSetRepository;

/** Tests how the constraint rule sets of a VSUM affect its build. */
class VsumBuildInputCollectorConstraintsTest {
  private static final long VSUM_ID = 7L;

  private final ConstraintRuleSetRepository ruleSetRepository =
      mock(ConstraintRuleSetRepository.class);
  private final VsumBuildInputCollector collector =
      new VsumBuildInputCollector(
          new ReactionBuildCollector(new LowCodeReactionService(null)),
          mock(UtilityMethodService.class),
          ruleSetRepository);

  private Vsum vsum;

  @BeforeEach
  void setUp() {
    vsum = new Vsum();
    vsum.setId(VSUM_ID);
    MetaModel metaModel = new MetaModel();
    metaModel.setEcoreFile(fs(1L, "a.ecore", "ecore"));
    MetaModelRelation relation = new MetaModelRelation();
    relation.setSource(metaModel);
    relation.setReactionFileStorage(fs(3L, "a.reactions", "reactions"));
    vsum.setMetaModelRelations(Set.of(relation));
  }

  @Test
  void fingerprintChangesWithRuleSetContentAndName() {
    when(ruleSetRepository.findByVsumId(VSUM_ID))
        .thenReturn(List.of(ruleSet(1L, "Rules", "context m::A inv a: true")));
    String original = collector.collect(vsum).fingerprint();

    when(ruleSetRepository.findByVsumId(VSUM_ID))
        .thenReturn(List.of(ruleSet(1L, "Rules", "context m::A inv a: false")));
    String changedContent = collector.collect(vsum).fingerprint();

    when(ruleSetRepository.findByVsumId(VSUM_ID))
        .thenReturn(List.of(ruleSet(1L, "New Rules", "context m::A inv a: true")));
    String renamed = collector.collect(vsum).fingerprint();

    when(ruleSetRepository.findByVsumId(VSUM_ID)).thenReturn(List.of());
    String withoutRuleSets = collector.collect(vsum).fingerprint();

    assertThat(List.of(original, changedContent, renamed, withoutRuleSets)).doesNotHaveDuplicates();
  }

  @Test
  void reportsTheNamesOfTheRuleSetsWithConstraints() {
    ConstraintRuleSet unnamed = ruleSet(4L, "x", "context m::C inv c: true");
    unnamed.setName(" ");
    ConstraintRuleSet withoutFile = ruleSet(5L, "Without file", "x");
    withoutFile.setOclFile(null);
    when(ruleSetRepository.findByVsumId(VSUM_ID))
        .thenReturn(
            List.of(
                ruleSet(1L, "Prices", "context m::A inv a: true"),
                ruleSet(2L, "Empty", ""),
                ruleSet(3L, "Components", "context m::B inv b: true"),
                unnamed,
                withoutFile));

    assertThat(collector.collectConstraintRuleSetNames(vsum))
        .containsExactly("Prices", "Components", VsumBuildInputCollector.UNNAMED_RULE_SET);
  }

  @Test
  void reportsNoRuleSetsWithoutConstraints() {
    when(ruleSetRepository.findByVsumId(VSUM_ID)).thenReturn(List.of());

    assertThat(collector.collectConstraintRuleSetNames(vsum)).isEmpty();
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
