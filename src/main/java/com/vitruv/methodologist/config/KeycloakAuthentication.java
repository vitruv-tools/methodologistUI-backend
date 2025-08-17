package com.vitruv.methodologist.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

import com.vitruv.methodologist.user.RoleType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Custom authentication token for Keycloak JWTs. Parses JWT claims into a strongly-typed {@link
 * ParsedToken} object for easier access.
 */
@Getter
public class KeycloakAuthentication extends JwtAuthenticationToken {
  private final ParsedToken parsedToken;

  /**
   * Constructs a new {@code KeycloakAuthentication} by parsing JWT claims into a {@link
   * ParsedToken}.
   *
   * @param jwt the JWT token containing claims
   * @param authorities the granted authorities for the authenticated user
   */
  public KeycloakAuthentication(Jwt jwt, Collection<? extends GrantedAuthority> authorities) {
    super(jwt, authorities);
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

    mapper.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false);
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    this.parsedToken = mapper.convertValue(jwt.getClaims(), ParsedToken.class);
  }

  /** Strongly-typed representation of JWT claims. */
  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public static class ParsedToken {
    private String scope;
    private String emailVerified;
    private String preferredUsername;
    private RoleType roleType;
    private String givenName;
    private String familyName;
    private String email;
    private Instant exp;
    private RealmAccess realmAccess;

    private static class RealmAccess {
      private List<String> roles;
    }
  }
}
