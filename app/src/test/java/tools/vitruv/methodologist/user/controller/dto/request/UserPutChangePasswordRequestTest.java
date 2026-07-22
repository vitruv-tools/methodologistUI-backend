package tools.vitruv.methodologist.user.controller.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class UserPutChangePasswordRequestTest {

  private static ValidatorFactory validatorFactory;
  private static Validator validator;

  @BeforeAll
  static void setUpValidator() {
    validatorFactory = Validation.buildDefaultValidatorFactory();
    validator = validatorFactory.getValidator();
  }

  @AfterAll
  static void closeValidatorFactory() {
    validatorFactory.close();
  }

  @Test
  void validate_fails_whenCurrentPasswordIsMissing() {
    UserPutChangePasswordRequest request =
        UserPutChangePasswordRequest.builder().newPassword("StrongPass#12345").build();

    assertThat(validator.validate(request))
        .anySatisfy(
            violation ->
                assertThat(violation.getPropertyPath().toString()).isEqualTo("currentPassword"));
  }

  @Test
  void validate_fails_whenNewPasswordViolatesExistingPasswordRules() {
    UserPutChangePasswordRequest request =
        UserPutChangePasswordRequest.builder()
            .currentPassword("CurrentPass#12345")
            .newPassword("weak")
            .build();

    assertThat(validator.validate(request))
        .anySatisfy(
            violation -> {
              assertThat(violation.getPropertyPath().toString()).isEqualTo("newPassword");
              assertThat(violation.getConstraintDescriptor().getAnnotation())
                  .isInstanceOf(Pattern.class);
            });
  }

  @Test
  void validate_passes_whenCurrentPasswordExists_andNewPasswordIsStrong() {
    UserPutChangePasswordRequest request =
        UserPutChangePasswordRequest.builder()
            .currentPassword("CurrentPass#12345")
            .newPassword("StrongPass#12345")
            .build();

    assertThat(validator.validate(request)).isEmpty();
  }
}
