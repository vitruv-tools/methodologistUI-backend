package tools.vitruv.methodologist.vsum.controller.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.vitruv.methodologist.vsum.controller.dto.request.UtilityMethodRequest;
import tools.vitruv.methodologist.vsum.controller.dto.response.UtilityMethodPreviewResponse;
import tools.vitruv.methodologist.vsum.controller.dto.response.UtilityMethodProblemResponse;
import tools.vitruv.methodologist.vsum.controller.dto.response.UtilityMethodResponse;

/** Checks the JSON shape and the input limits of the utility method DTOs. */
class UtilityMethodDtoTest {

  private static ValidatorFactory factory;
  private static Validator validator;
  private final ObjectMapper mapper =
      new ObjectMapper()
          .registerModule(new JavaTimeModule())
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  @BeforeAll
  static void setUpValidator() {
    factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @AfterAll
  static void closeValidator() {
    factory.close();
  }

  private static UtilityMethodRequest request(List<String> imports, String body) {
    return new UtilityMethodRequest("answer", null, "int", List.of(), imports, body);
  }

  @Test
  void response_exposesTheXtendDefinitionAndProblemSeverity() throws Exception {
    UtilityMethodResponse response =
        new UtilityMethodResponse(
            1L,
            10L,
            "answer",
            null,
            "int",
            List.of(),
            List.of(),
            "42",
            "def static int answer() {\n  42\n}",
            null,
            List.of(new UtilityMethodProblemResponse("answer", "Unused", 1, 1, "WARNING")),
            Instant.EPOCH,
            Instant.EPOCH);

    String json = mapper.writeValueAsString(response);

    assertThat(json)
        .contains("\"xtendDefinition\":\"def static int answer() {\\n  42\\n}\"")
        .contains("\"severity\":\"WARNING\"")
        .doesNotContain("oclDefinition");
  }

  @Test
  void preview_exposesTheXtendDefinition() throws Exception {
    String json =
        mapper.writeValueAsString(new UtilityMethodPreviewResponse("def", "java", List.of()));

    assertThat(json).contains("\"xtendDefinition\":\"def\"").doesNotContain("oclDefinition");
  }

  @Test
  void request_acceptsABodyAndImportsWithinTheLimits() {
    assertThat(validator.validate(request(List.of("java.util.List"), "1".repeat(20_000))))
        .isEmpty();
  }

  @Test
  void request_rejectsABodyLongerThan20000Characters() {
    Set<ConstraintViolation<UtilityMethodRequest>> violations =
        validator.validate(request(List.of(), "1".repeat(20_001)));

    assertThat(violations).extracting(v -> v.getPropertyPath().toString()).containsExactly("body");
  }

  @Test
  void request_rejectsMoreThan50ImportsAndOverlongImports() {
    assertThat(validator.validate(request(Collections.nCopies(51, "a.B"), "1")))
        .extracting(v -> v.getPropertyPath().toString())
        .containsExactly("imports");
    assertThat(validator.validate(request(List.of("a." + "b".repeat(255)), "1")))
        .extracting(v -> v.getPropertyPath().toString())
        .containsExactly("imports[0].<list element>");
  }
}
