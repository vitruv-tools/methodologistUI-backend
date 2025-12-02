package tools.vitruv.methodologist.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import tools.vitruv.methodologist.user.RoleType;

/**
 * Custom authentication token for Keycloak JWTs. Parses JWT claims into a strongly-typed {@link
 * KeycloakAuthentication.ParsedToken} object for easier access.
 */
@Getter
public class KeycloakAuthentication extends JwtAuthenticationToken implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  private static final ObjectMapper MAPPER;

  static {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    mapper.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false);
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    MAPPER = mapper;
  }

  private final transient ParsedToken parsedToken;

  /**
   * Constructs a new {@code KeycloakAuthentication} by parsing JWT claims into a {@link
   * KeycloakAuthentication.ParsedToken}.
   *
   * @param jwt the JWT token containing claims
   * @param authorities the granted authorities for the authenticated user
   */
  public KeycloakAuthentication(Jwt jwt, Collection<? extends GrantedAuthority> authorities) {
    super(jwt, authorities);
    this.parsedToken = MAPPER.convertValue(jwt.getClaims(), ParsedToken.class);
  }

  /** Strongly-typed representation of JWT claims. */
  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ParsedToken implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    private String scope;
    private Boolean emailVerified;
    private String preferredUsername;
    private RoleType roleType;
    private String givenName;
    private String familyName;
    private String email;
    private Instant exp;
    private RealmAccess realmAccess;

    /**
     * Represents the Keycloak realm access claim, containing the list of roles assigned to the
     * user.
     *
     * <p>Used to extract and manage user roles from the JWT token for authorization purposes.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RealmAccess implements Serializable {

      @Serial private static final long serialVersionUID = 1L;

      private List<String> roles;
    }
  }
}
