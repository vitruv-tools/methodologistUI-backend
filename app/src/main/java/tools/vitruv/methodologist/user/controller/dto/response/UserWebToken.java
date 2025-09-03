package tools.vitruv.methodologist.user.controller.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents the authentication tokens issued to a user after a successful login or token refresh.
 * This class models the standard Keycloak web token response, adapted for application use. It
 * contains both access and refresh tokens, expiration details, token type, and session metadata.
 * The naming strategy is configured to use snake_case to align with Keycloak's JSON response
 * format.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class UserWebToken implements java.io.Serializable {
  private String accessToken;
  private String refreshToken;
  private Integer expiresIn;
  private Integer refreshExpiresIn;
  private String tokenType;

  @JsonProperty("not-before-policy")
  private Integer notBeforePolicy;

  private String sessionState;
  private String scope;
}
