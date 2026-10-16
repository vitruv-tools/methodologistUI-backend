package tools.vitruv.methodologist.vsum.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.vitruv.methodologist.vsum.lowcode.reactions.template.dto.request.CreateCorrespondingRootOnInsertRootRequest;
import tools.vitruv.methodologist.vsum.lowcode.reactions.template.dto.request.LowCodeReactionRequestBase;

class LowCodeReactionRequestMapperTest {

  private LowCodeReactionRequestMapper mapper;

  @BeforeEach
  void setup() {
    mapper = new LowCodeReactionRequestMapper(new ObjectMapper());
  }

  @Test
  void map_returnsTypedRequest_whenTemplateAndParamsPresent() {
    Map<String, Object> params =
        Map.of(
            "model1Uri", "http://pcm",
            "model2Uri", "http://uml",
            "model1Alias", "pcm",
            "model2Alias", "uml",
            "reactionName", "createCorrespondingRoot",
            "model1RootType", "Component",
            "model2RootType", "Class",
            "model1RootVar", "component");

    LowCodeReactionRequestBase mapped =
        mapper.map("create_corresponding_root_on_insert_root", params);

    assertThat(mapped).isInstanceOf(CreateCorrespondingRootOnInsertRootRequest.class);
    CreateCorrespondingRootOnInsertRootRequest request =
        (CreateCorrespondingRootOnInsertRootRequest) mapped;
    assertThat(request.getName()).isEqualTo("create_corresponding_root_on_insert_root");
    assertThat(request.getModel1Alias()).isEqualTo("pcm");
    assertThat(request.getModel2RootType()).isEqualTo("Class");
    assertThat(request.getModel1RootVar()).isEqualTo("component");
  }

  @Test
  void map_returnsNull_whenTemplateIsNull() {
    assertThat(mapper.map(null, Map.of("model1Alias", "pcm"))).isNull();
  }
}
