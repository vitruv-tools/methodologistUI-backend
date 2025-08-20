package com.vitruv.methodologist.apihandler.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object (DTO) representing a Keycloak authentication token response.
 * Maps the JSON response from Keycloak's token endpoint to a Java object.
 * Uses snake case naming strategy for JSON property mapping.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class KeycloakWebToken implements Serializable {
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
