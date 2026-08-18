package tools.vitruv.methodologist.vsum.controller.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import tools.vitruv.methodologist.general.model.FileStorage;
import tools.vitruv.methodologist.vsum.lowcode.reactions.template.dto.request.CreateCorrespondingRootOnInsertRootRequest;
import tools.vitruv.methodologist.vsum.mapper.LowCodeReactionRequestMapper;
import tools.vitruv.methodologist.vsum.model.FineGranularMetaModelRelation;

class FineGranularMetaModelRelationRequestTest {

  private final LowCodeReactionRequestMapper mapper =
      new LowCodeReactionRequestMapper(new ObjectMapper());

  @Test
  void equals_matchesPersistedRelation_whenIdsAndFileMatch() {
    FineGranularMetaModelRelationRequest request =
        new FineGranularMetaModelRelationRequest(1L, "Component", "Class", 14L, null);

    assertThat(request.equals(mapper, relation(1L, "Component", "Class", 14L))).isTrue();
  }

  @Test
  void equals_doesNotMatch_whenSourceDiffers() {
    FineGranularMetaModelRelationRequest request =
        new FineGranularMetaModelRelationRequest(1L, "Component", "Class", 14L, null);

    assertThat(request.equals(mapper, relation(1L, "Interface", "Class", 14L))).isFalse();
  }

  @Test
  void equals_neverMatches_whenRegenerateTrue() {
    CreateCorrespondingRootOnInsertRootRequest template =
        new CreateCorrespondingRootOnInsertRootRequest();
    template.setRegenerate(true);
    FineGranularMetaModelRelationRequest request =
        new FineGranularMetaModelRelationRequest(1L, "Component", "Class", 14L, template);

    assertThat(request.equals(mapper, relation(1L, "Component", "Class", 14L))).isFalse();
  }

  private FineGranularMetaModelRelation relation(
      Long id, String sourceId, String targetId, Long fileId) {
    FileStorage fileStorage = new FileStorage();
    fileStorage.setId(fileId);
    return FineGranularMetaModelRelation.builder()
        .id(id)
        .sourceId(sourceId)
        .targetId(targetId)
        .reactionFileStorage(fileStorage)
        .build();
  }
}
