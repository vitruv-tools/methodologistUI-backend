package tools.vitruv.methodologist.user.controller.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request payload used to verify a user's account (for example during email verification).
 *
 * <p>Holds the verification input code supplied by the client. The value must be present and not
 * blank; validation annotations enforce these constraints.
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserPutVerifyRequest {
  @NotNull @NotBlank String inputCode;
}
