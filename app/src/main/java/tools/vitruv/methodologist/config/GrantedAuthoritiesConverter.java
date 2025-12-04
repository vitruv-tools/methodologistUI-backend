package tools.vitruv.methodologist.config;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Converts JWT realm roles to Spring Security {@link
 * org.springframework.security.core.GrantedAuthority} objects. Used to extract roles from the
 * "realm_access" claim in Keycloak tokens.
 */
public class GrantedAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

  /**
   * Converts the "realm_access" claim from the JWT into a collection of {@link
   * org.springframework.security.core.GrantedAuthority}.
   *
   * @param source the JWT token containing claims
   * @return a non-null collection of granted authorities
   */
  @Override
  public Collection<GrantedAuthority> convert(Jwt source) {
    Map<String, Object> realmAccess = source.getClaimAsMap("realm_access");

    if (realmAccess != null) {
      Object rolesObj = realmAccess.get("roles");

      if (rolesObj instanceof List<?> roles) {
        return roles.stream()
                .filter(String.class::isInstance)
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .collect(Collectors.toList());
      }
    }

    return Collections.emptyList();
  }
}