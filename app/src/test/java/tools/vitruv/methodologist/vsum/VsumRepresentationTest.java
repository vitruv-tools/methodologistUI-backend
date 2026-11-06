package tools.vitruv.methodologist.vsum;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Reads history snapshots the way their JSON column does, with a plain Jackson {@link
 * ObjectMapper}.
 */
class VsumRepresentationTest {

  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

  @Test
  void readsViewsOfSnapshotsTakenBeforeViewsHadNames() throws JsonProcessingException {
    String snapshot = "{\"views\":[{\"metaModelIds\":[1,2],\"fileStorageId\":10}]}";

    VsumRepresentation representation = objectMapper.readValue(snapshot, VsumRepresentation.class);

    assertThat(representation.getViews())
        .singleElement()
        .satisfies(
            view -> {
              assertThat(view.getId()).isNull();
              assertThat(view.getName()).isNull();
              assertThat(view.getDescription()).isNull();
              assertThat(view.getFileStorageId()).isEqualTo(10L);
              assertThat(view.getMetaModelIds()).containsExactly(1L, 2L);
            });
  }

  @Test
  void keepsTheIdNameAndDescriptionOfViews() throws JsonProcessingException {
    VsumRepresentation representation =
        VsumRepresentation.builder()
            .views(
                Set.of(
                    VsumRepresentation.View.builder()
                        .id(7L)
                        .name("Brake")
                        .description("brake components")
                        .fileStorageId(10L)
                        .metaModelIds(List.of(1L))
                        .build()))
            .build();

    VsumRepresentation read =
        objectMapper.readValue(
            objectMapper.writeValueAsString(representation), VsumRepresentation.class);

    assertThat(read.getViews())
        .singleElement()
        .satisfies(
            view -> {
              assertThat(view.getId()).isEqualTo(7L);
              assertThat(view.getName()).isEqualTo("Brake");
              assertThat(view.getDescription()).isEqualTo("brake components");
            });
  }
}
