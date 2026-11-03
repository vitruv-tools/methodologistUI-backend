package tools.vitruv.methodologist.vsum.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import tools.vitruv.methodologist.exception.BuildProblem;
import tools.vitruv.methodologist.user.model.User;
import tools.vitruv.methodologist.vsum.controller.dto.response.VsumBuildResponse;
import tools.vitruv.methodologist.vsum.model.Vsum;
import tools.vitruv.methodologist.vsum.model.VsumBuild;
import tools.vitruv.methodologist.vsum.model.VsumBuildStatus;

/** Unit tests for {@link VsumBuildMapper} and the JSON of the mapped {@link VsumBuildResponse}. */
class VsumBuildMapperTest {

  private static final BuildProblem PROBLEM =
      new BuildProblem(
          "REACTIONS",
          "my.reactions",
          12L,
          51,
          30,
          "unknown class",
          "new model2::Entitiy",
          "Did you mean 'Entity'?",
          3);

  private final VsumBuildMapper mapper = Mappers.getMapper(VsumBuildMapper.class);
  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

  @Test
  void toResponse_mapsTheErrorCodeAndProblemsOfAFailedBuild() throws Exception {
    VsumBuild build = build(VsumBuildStatus.FAILED);
    build.setErrorMessage("rejected");
    build.setErrorCode("VSUM_REACTIONS_ERROR");
    build.setProblems(
        List.of(
            PROBLEM,
            new BuildProblem(
                "INFRASTRUCTURE", null, null, null, null, "offline", null, null, null)));

    VsumBuildResponse response = mapper.toResponse(build);

    assertThat(response.getErrorMessage()).isEqualTo("rejected");
    assertThat(response.getErrorCode()).isEqualTo("VSUM_REACTIONS_ERROR");
    assertThat(response.getProblems()).isEqualTo(build.getProblems());

    JsonNode json = objectMapper.valueToTree(response);
    assertThat(json.get("errorCode").asText()).isEqualTo("VSUM_REACTIONS_ERROR");
    JsonNode problem = json.get("problems").get(0);
    assertThat(problem.get("source").asText()).isEqualTo("REACTIONS");
    assertThat(problem.get("file").asText()).isEqualTo("my.reactions");
    assertThat(problem.get("fileId").asLong()).isEqualTo(12L);
    assertThat(problem.get("line").asInt()).isEqualTo(51);
    assertThat(problem.get("column").asInt()).isEqualTo(30);
    assertThat(problem.get("message").asText()).isEqualTo("unknown class");
    assertThat(problem.get("excerpt").asText()).isEqualTo("new model2::Entitiy");
    assertThat(problem.get("hint").asText()).isEqualTo("Did you mean 'Entity'?");
    assertThat(problem.get("hiddenFollowUpErrors").asInt()).isEqualTo(3);
    assertThat(json.get("problems").get(1).fieldNames())
        .toIterable()
        .containsExactlyInAnyOrder("source", "message");
  }

  @Test
  void toResponse_leavesErrorCodeAndProblemsOutOfTheJson_whenThereAreNone() {
    VsumBuildResponse response = mapper.toResponse(build(VsumBuildStatus.SUCCEEDED));

    JsonNode json = objectMapper.valueToTree(response);
    assertThat(json.has("errorCode")).isFalse();
    assertThat(json.has("problems")).isFalse();
    assertThat(json.has("errorMessage")).isTrue();
    assertThat(json.get("vsumId").asLong()).isEqualTo(7L);
  }

  private static VsumBuild build(VsumBuildStatus status) {
    Vsum vsum = new Vsum();
    vsum.setId(7L);
    User user = new User();
    user.setEmail("x@y.com");
    return VsumBuild.builder().id(1L).vsum(vsum).requestedBy(user).status(status).build();
  }
}
