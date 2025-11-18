package tools.vitruv.methodologist.config;

import java.util.Collection;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Converts a {@link Jwt} into an {@link AbstractAuthenticationToken} backed by {@link
 * KeycloakAuthentication}.
 *
 * <p>This converter delegates extraction of granted authorities to a dedicated {@code
 * GrantedAuthoritiesConverter} and constructs a {@code KeycloakAuthentication} containing the
 * original JWT and the derived authorities.
 */
public class KeycloakJwtAuthenticationConverter
    implements Converter<Jwt, AbstractAuthenticationToken> {

  private final GrantedAuthoritiesConverter authoritiesConverter =
      new GrantedAuthoritiesConverter();

  /**
   * Convert the given {@link Jwt} to an {@link AbstractAuthenticationToken}.
   *
   * @param jwt the JWT to convert
   * @return an authentication token containing the JWT and the extracted authorities
   */
  @Override
  public AbstractAuthenticationToken convert(Jwt jwt) {
    Collection<? extends GrantedAuthority> authorities = authoritiesConverter.convert(jwt);
    return new KeycloakAuthentication(jwt, authorities);
  }
}
