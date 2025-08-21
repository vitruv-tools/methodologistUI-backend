package com.vitruv.methodologist.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Converts JWT realm roles to Spring Security {@link GrantedAuthority} objects. Used to extract
 * roles from the "realm_access" claim in Keycloak tokens.
 */
public class GrantedAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

  /**
   * Converts the "realm_access" claim from the JWT into a collection of {@link GrantedAuthority}.
   *
   * @param source the JWT token containing claims
   * @return a collection of granted authorities based on realm roles
   */
  @Override
  public Collection<GrantedAuthority> convert(Jwt source) {
    Map<String, Object> realmAccess = source.getClaimAsMap("realm_access");

    if (Objects.nonNull(realmAccess)) {
      List<String> roles = (List<String>) realmAccess.get("roles");

      if (Objects.nonNull(roles)) {
        return roles.stream()
            .map(rn -> new SimpleGrantedAuthority("ROLE_" + rn))
            .collect(Collectors.toList());
      }
    }
    return new ArrayList<>();
  }
}
