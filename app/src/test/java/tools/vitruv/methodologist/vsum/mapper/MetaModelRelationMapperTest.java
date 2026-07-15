package tools.vitruv.methodologist.vsum.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.vitruv.methodologist.general.model.FileStorage;
import tools.vitruv.methodologist.vsum.controller.dto.response.MetaModelRelationResponse;
import tools.vitruv.methodologist.vsum.model.MetaModel;
import tools.vitruv.methodologist.vsum.model.MetaModelRelation;

class MetaModelRelationMapperTest {

  private final MetaModelRelationMapper mapper = new MetaModelRelationMapperImpl();

  @Test
  void toMetaModelRelationResponse_resolvesCatalogIdThroughCloneSourceReference() {
    MetaModel catalogSource = MetaModel.builder().id(2L).build();
    MetaModel vsumScopedSourceClone = MetaModel.builder().id(11L).source(catalogSource).build();

    MetaModel catalogTarget = MetaModel.builder().id(5L).build();
    MetaModel vsumScopedTargetClone = MetaModel.builder().id(12L).source(catalogTarget).build();

    MetaModelRelation relation =
        MetaModelRelation.builder()
            .id(1L)
            .source(vsumScopedSourceClone)
            .target(vsumScopedTargetClone)
            .reactionFileStorage(FileStorage.builder().id(35L).build())
            .build();

    MetaModelRelationResponse response = mapper.toMetaModelRelationResponse(relation);

    assertThat(response.getSourceId()).isEqualTo(2L);
    assertThat(response.getTargetId()).isEqualTo(5L);
    assertThat(response.getReactionFileStorageId()).isEqualTo(35L);
  }

  @Test
  void toMetaModelRelationResponse_fallsBackToOwnIdWhenNotAClone() {
    MetaModel source = MetaModel.builder().id(2L).build();
    MetaModel target = MetaModel.builder().id(5L).build();

    MetaModelRelation relation =
        MetaModelRelation.builder().id(1L).source(source).target(target).build();

    MetaModelRelationResponse response = mapper.toMetaModelRelationResponse(relation);

    assertThat(response.getSourceId()).isEqualTo(2L);
    assertThat(response.getTargetId()).isEqualTo(5L);
  }
}
