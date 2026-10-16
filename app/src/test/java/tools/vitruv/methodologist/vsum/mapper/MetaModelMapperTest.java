package tools.vitruv.methodologist.vsum.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.vitruv.methodologist.vsum.controller.dto.request.MetaModelPostRequest;
import tools.vitruv.methodologist.vsum.controller.dto.response.MetaModelResponse;
import tools.vitruv.methodologist.vsum.model.MetaModel;

class MetaModelMapperTest {

  private final MetaModelMapper metaModelMapper = new MetaModelMapperImpl();

  @Test
  void toMetaModel_mapsVersionFromCreationRequest() {
    MetaModelPostRequest request = MetaModelPostRequest.builder().version("2.0").build();

    MetaModel result = metaModelMapper.toMetaModel(request);

    assertThat(result.getVersion()).isEqualTo("2.0");
  }

  @Test
  void toMetaModel_defaultsVersionForExistingClients() {
    MetaModelPostRequest request = MetaModelPostRequest.builder().build();

    MetaModel result = metaModelMapper.toMetaModel(request);

    assertThat(result.getVersion()).isEqualTo("1.0");
  }

  @Test
  void toMetaModelResponse_mapsVersion() {
    MetaModel metaModel = MetaModel.builder().version("2.0").build();

    MetaModelResponse result = metaModelMapper.toMetaModelResponse(metaModel);

    assertThat(result.getVersion()).isEqualTo("2.0");
  }
}
