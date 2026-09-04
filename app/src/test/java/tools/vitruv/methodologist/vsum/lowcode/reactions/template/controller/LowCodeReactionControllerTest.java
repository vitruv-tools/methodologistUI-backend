package tools.vitruv.methodologist.vsum.lowcode.reactions.template.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tools.vitruv.methodologist.messages.Message.LOWCODE_REACTIONS_METADATA_LOADED_SUCCESSFULLY;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.vitruv.methodologist.ResponseTemplateDto;
import tools.vitruv.methodologist.vsum.lowcode.reactions.template.dto.response.LowCodeReactionMetadata;
import tools.vitruv.methodologist.vsum.lowcode.reactions.template.dto.response.LowCodeReactionMetadataResponse;
import tools.vitruv.methodologist.vsum.lowcode.reactions.template.service.LowCodeReactionMetadataService;

@ExtendWith(MockitoExtension.class)
class LowCodeReactionControllerTest {

  @InjectMocks LowCodeReactionController controller;
  @Mock LowCodeReactionMetadataService lowCodeReactionMetadataService;

  @Test
  void getAllLowCodeReactionMetadata_wrapsServiceResult() {
    LowCodeReactionMetadataResponse metadata =
        LowCodeReactionMetadataResponse.builder()
            .reactionMetadataMap(
                Map.of(
                    "create_corresponding_root_on_insert_root",
                    LowCodeReactionMetadata.builder().name("Create Corresponding Root").build()))
            .build();
    when(lowCodeReactionMetadataService.getAllLowCodeReactionMetadata()).thenReturn(metadata);

    ResponseTemplateDto<LowCodeReactionMetadataResponse> response =
        controller.getAllLowCodeReactionMetadata();

    assertThat(response.getData()).isSameAs(metadata);
    assertThat(response.getMessage()).isEqualTo(LOWCODE_REACTIONS_METADATA_LOADED_SUCCESSFULLY);
    verify(lowCodeReactionMetadataService).getAllLowCodeReactionMetadata();
  }
}
