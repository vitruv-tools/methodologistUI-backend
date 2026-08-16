package tools.vitruv.methodologist.vsum.lowcode.reactions.template.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.vitruv.methodologist.vsum.lowcode.reactions.template.dto.request.CompositeReactionsRequest;
import tools.vitruv.methodologist.vsum.lowcode.reactions.template.dto.request.CreateCorrespondingRootOnInsertRootRequest;
import tools.vitruv.methodologist.vsum.lowcode.reactions.template.dto.request.ExampleRequest;
import tools.vitruv.methodologist.vsum.lowcode.reactions.template.dto.response.LowCodeReactionMetadataResponse;

/** Unit tests for {@link LowCodeReactionMetadataService}. */
class LowCodeReactionMetadataServiceTest {

  @Test
  void getAllLowCodeReactionMetadata_excludesHiddenCompositeTemplate() {
    LowCodeReactionMetadataService service =
        new LowCodeReactionMetadataService(
            List.of(
                new CreateCorrespondingRootOnInsertRootRequest(),
                new CompositeReactionsRequest(),
                new ExampleRequest()));

    LowCodeReactionMetadataResponse response = service.getAllLowCodeReactionMetadata();

    assertThat(response.getReactionMetadataMap())
        .containsKeys("create_corresponding_root_on_insert_root", "example_request")
        .doesNotContainKey("composite_reactions");
    assertThat(
            response
                .getReactionMetadataMap()
                .get("create_corresponding_root_on_insert_root")
                .getName())
        .isEqualTo("Create Corresponding Root");
  }
}
