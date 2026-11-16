package tools.vitruv.methodologist.vsum.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import org.junit.jupiter.api.Test;
import tools.vitruv.methodologist.general.model.FileStorage;
import tools.vitruv.methodologist.vsum.controller.dto.response.ViewsResponse;
import tools.vitruv.methodologist.vsum.model.MetaModel;
import tools.vitruv.methodologist.vsum.model.VsumView;
import tools.vitruv.methodologist.vsum.model.VsumViewMetaModel;

/** Unit tests for {@link VsumViewMapper}. */
class VsumViewMapperTest {

  private final VsumViewMapper mapper = new VsumViewMapperImpl();

  @Test
  void toViewsResponse_mapsNameDescriptionFileAndLinkedMetaModelsWithTheirSourceIds() {
    MetaModel source = MetaModel.builder().id(1L).name("Brake").build();
    MetaModel cloned = MetaModel.builder().id(101L).name("Brake").source(source).build();
    VsumView view =
        VsumView.builder()
            .id(7L)
            .name("Brake view")
            .description("brake components")
            .fileStorage(FileStorage.builder().id(70L).build())
            .viewMetaModels(new HashSet<>())
            .build();
    view.getViewMetaModels()
        .add(VsumViewMetaModel.builder().vsumView(view).metaModel(cloned).build());

    ViewsResponse response = mapper.toViewsResponse(view);

    assertThat(response.getId()).isEqualTo(7L);
    assertThat(response.getName()).isEqualTo("Brake view");
    assertThat(response.getDescription()).isEqualTo("brake components");
    assertThat(response.getFileStorageId()).isEqualTo(70L);
    assertThat(response.getAssignedModels())
        .singleElement()
        .satisfies(
            metaModel -> {
              assertThat(metaModel.getId()).isEqualTo(101L);
              assertThat(metaModel.getSourceId()).isEqualTo(1L);
            });
  }
}
