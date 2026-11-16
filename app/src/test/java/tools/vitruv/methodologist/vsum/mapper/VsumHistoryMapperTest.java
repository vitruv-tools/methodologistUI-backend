package tools.vitruv.methodologist.vsum.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.vitruv.methodologist.general.model.FileStorage;
import tools.vitruv.methodologist.vsum.VsumRepresentation;
import tools.vitruv.methodologist.vsum.controller.dto.response.VsumHistoryResponse;
import tools.vitruv.methodologist.vsum.model.FineGranularMetaModelRelation;
import tools.vitruv.methodologist.vsum.model.MetaModel;
import tools.vitruv.methodologist.vsum.model.MetaModelRelation;
import tools.vitruv.methodologist.vsum.model.Vsum;
import tools.vitruv.methodologist.vsum.model.VsumHistory;
import tools.vitruv.methodologist.vsum.model.VsumMetaModel;
import tools.vitruv.methodologist.vsum.model.VsumView;
import tools.vitruv.methodologist.vsum.model.VsumViewMetaModel;

/** Unit tests for {@link VsumHistoryMapper} relation snapshot mapping. */
class VsumHistoryMapperTest {

  private final VsumHistoryMapper mapper =
      new VsumHistoryMapper() {
        @Override
        public VsumHistoryResponse toVsumHistoryResponse(VsumHistory vsumHistory) {
          return null;
        }
      };

  @Test
  void toMetaModelsRelation_allowsNullCoarseReactionFile_andMapsFineGranularChildren() {
    MetaModel source =
        MetaModel.builder().id(11L).source(MetaModel.builder().id(19L).build()).build();
    MetaModel target =
        MetaModel.builder().id(22L).source(MetaModel.builder().id(20L).build()).build();
    FileStorage fgFile = FileStorage.builder().id(44L).build();
    MetaModelRelation relation =
        MetaModelRelation.builder()
            .source(source)
            .target(target)
            .reactionFileStorage(null)
            .fineGranularMetaModelRelationSet(new HashSet<>())
            .build();
    FineGranularMetaModelRelation fg =
        FineGranularMetaModelRelation.builder()
            .sourceId("Component")
            .targetId("Class")
            .reactionFileStorage(fgFile)
            .lowCodeReactionTemplate("create_corresponding_root_on_insert_root")
            .metaModelRelation(relation)
            .build();
    relation.getFineGranularMetaModelRelationSet().add(fg);

    Set<VsumRepresentation.MetaModelRelation> mapped =
        mapper.toMetaModelsRelation(Set.of(relation));

    assertThat(mapped).hasSize(1);
    VsumRepresentation.MetaModelRelation snapshot = mapped.iterator().next();
    assertThat(snapshot.getSourceId()).isEqualTo(19L);
    assertThat(snapshot.getTargetId()).isEqualTo(20L);
    assertThat(snapshot.getRelationFileStorage()).isNull();
    assertThat(snapshot.getFineGranularMetaModelRelationSet()).hasSize(1);
    VsumRepresentation.FineGranularMetaModelRelation mappedFg =
        snapshot.getFineGranularMetaModelRelationSet().iterator().next();
    assertThat(mappedFg.getSourceId()).isEqualTo("Component");
    assertThat(mappedFg.getReactionFileStorageId()).isEqualTo(44L);
  }

  @Test
  void toMetaModelsRelation_returnsEmpty_whenNull() {
    assertThat(mapper.toMetaModelsRelation(null)).isEmpty();
  }

  @Test
  void toVsumRepresentation_preservesProjectSpecificMetaModelNames() {
    MetaModel source = MetaModel.builder().id(19L).name("Library name").build();
    MetaModel cloned = MetaModel.builder().id(11L).source(source).name("Library name").build();
    Vsum vsum = new Vsum();
    vsum.setVsumUsers(Set.of());
    vsum.setMetaModelRelations(Set.of());
    vsum.setViews(Set.of());
    vsum.setVsumMetaModels(
        Set.of(VsumMetaModel.builder().vsum(vsum).metaModel(cloned).name("Project name").build()));

    VsumRepresentation result = mapper.toVsumRepresentation(vsum);

    assertThat(result.getMetaModelNames()).containsEntry(19L, "Project name");
  }

  @Test
  void toView_mapsIdNameDescriptionFileAndSortedSourceMetaModelIds() {
    VsumView view =
        VsumView.builder()
            .id(7L)
            .name("Brake")
            .description("brake components")
            .fileStorage(FileStorage.builder().id(70L).build())
            .viewMetaModels(new HashSet<>())
            .build();
    for (long sourceId : new long[] {3L, 1L}) {
      MetaModel cloned =
          MetaModel.builder()
              .id(100L + sourceId)
              .source(MetaModel.builder().id(sourceId).build())
              .build();
      view.getViewMetaModels()
          .add(VsumViewMetaModel.builder().vsumView(view).metaModel(cloned).build());
    }

    Set<VsumRepresentation.View> result = mapper.toView(Set.of(view));

    assertThat(result)
        .singleElement()
        .satisfies(
            snapshot -> {
              assertThat(snapshot.getId()).isEqualTo(7L);
              assertThat(snapshot.getName()).isEqualTo("Brake");
              assertThat(snapshot.getDescription()).isEqualTo("brake components");
              assertThat(snapshot.getFileStorageId()).isEqualTo(70L);
              assertThat(snapshot.getMetaModelIds()).containsExactly(1L, 3L);
            });
  }
}
