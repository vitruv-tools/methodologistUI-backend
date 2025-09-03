package tools.vitruv.methodologist.user.controller.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request DTO for exchanging a login password with an access token. This object contains the
 * username and the verification password required for the token request.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PostAccessTokenRequest {

  @NotNull @NotBlank String username;
  @NotNull @NotBlank String password;
}
