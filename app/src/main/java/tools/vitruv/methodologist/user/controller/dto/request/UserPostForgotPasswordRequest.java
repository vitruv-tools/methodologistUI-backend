package tools.vitruv.methodologist.user.controller.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request payload for initiating a password reset by email.
 *
 * <p>Contains the email address to send the password reset message to. Validation: must not be null
 * or blank.
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserPostForgotPasswordRequest {

  @NotNull @NotBlank private String email;
}
