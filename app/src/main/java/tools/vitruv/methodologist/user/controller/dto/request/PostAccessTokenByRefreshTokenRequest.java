package tools.vitruv.methodologist.user.controller.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request payload for obtaining a new access token using a refresh token. Contains the refresh
 * token string that is validated and exchanged for a new user session without requiring re-login.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PostAccessTokenByRefreshTokenRequest {

  @NotNull @NotBlank String refreshToken;
}
