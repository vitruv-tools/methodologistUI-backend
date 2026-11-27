package tools.vitruv.methodologist.vsum.controller.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Checks that the metamodel requests reject text longer than the VARCHAR(255) columns. */
class MetaModelRequestTest {

  private static final String MAX_LENGTH = "a".repeat(255);
  private static final String TOO_LONG = "a".repeat(256);

  private static ValidatorFactory factory;
  private static Validator validator;

  @BeforeAll
  static void setUpValidator() {
    factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @AfterAll
  static void closeValidator() {
    factory.close();
  }

  private static MetaModelPostRequest postRequest() {
    return MetaModelPostRequest.builder()
        .name("name")
        .version("1.0")
        .description("description")
        .domain("domain")
        .keyword(List.of())
        .ecoreFileId(1L)
        .build();
  }

  private static MetaModelPutRequest putRequest() {
    return MetaModelPutRequest.builder()
        .name("name")
        .description("description")
        .domain("domain")
        .keyword(List.of())
        .ecoreFileId(1L)
        .build();
  }

  private static void setPostField(MetaModelPostRequest request, String field, String value) {
    switch (field) {
      case "name" -> request.setName(value);
      case "version" -> request.setVersion(value);
      case "description" -> request.setDescription(value);
      case "domain" -> request.setDomain(value);
      default -> throw new IllegalArgumentException(field);
    }
  }

  private static void setPutField(MetaModelPutRequest request, String field, String value) {
    switch (field) {
      case "name" -> request.setName(value);
      case "description" -> request.setDescription(value);
      case "domain" -> request.setDomain(value);
      default -> throw new IllegalArgumentException(field);
    }
  }

  @Test
  void postRequest_acceptsFieldsOf255Characters() {
    MetaModelPostRequest request = postRequest();
    List.of("name", "version", "description", "domain")
        .forEach(field -> setPostField(request, field, MAX_LENGTH));

    assertThat(validator.validate(request)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"name", "version", "description", "domain"})
  void postRequest_rejectsFieldLongerThan255Characters(String field) {
    MetaModelPostRequest request = postRequest();
    setPostField(request, field, TOO_LONG);

    assertThat(validator.validate(request))
        .extracting(v -> v.getPropertyPath().toString())
        .containsExactly(field);
  }

  @Test
  void putRequest_acceptsFieldsOf255Characters() {
    MetaModelPutRequest request = putRequest();
    List.of("name", "description", "domain")
        .forEach(field -> setPutField(request, field, MAX_LENGTH));

    assertThat(validator.validate(request)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"name", "description", "domain"})
  void putRequest_rejectsFieldLongerThan255Characters(String field) {
    MetaModelPutRequest request = putRequest();
    setPutField(request, field, TOO_LONG);

    assertThat(validator.validate(request))
        .extracting(v -> v.getPropertyPath().toString())
        .containsExactly(field);
  }
}
