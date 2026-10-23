
package tools.vitruv.methodologist.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class GrantedAuthoritiesConverterTest {

  private final GrantedAuthoritiesConverter converter = new GrantedAuthoritiesConverter();

  @Test
  void nullJwt_returnsEmptyAuthorities() {
    assertThat(converter.convert(null)).isEmpty();
  }

  @Test
  void noRealmAccess_returnsEmptyAuthorities() {
    Jwt jwt = jwt(Map.of("sub", "test-user"));

    assertThat(converter.convert(jwt)).isEmpty();
  }

  @Test
  void noRoles_returnsEmptyAuthorities() {
    Jwt jwt = jwt(Map.of("realm_access", Map.of()));

    assertThat(converter.convert(jwt)).isEmpty();
  }

  @Test
  void convertsRealmRoles() {
    Jwt jwt = jwt(Map.of("realm_access", Map.of("roles", List.of("admin", "user"))));

    assertThat(converter.convert(jwt))
        .extracting(GrantedAuthority::getAuthority)
        .containsExactly("ROLE_admin", "ROLE_user");
  }

  @Test
  void emptyRoles_returnsEmptyAuthorities() {
    Jwt jwt = jwt(Map.of("realm_access", Map.of("roles", List.of())));

    assertThat(converter.convert(jwt)).isEmpty();
  }

  @Test
  void rolesNotAList_returnsEmptyAuthorities() {
    Jwt jwt = jwt(Map.of("realm_access", Map.of("roles", "admin")));

    assertThat(converter.convert(jwt)).isEmpty();
  }

  @Test
  void ignoresNonStringRoles() {
    Jwt jwt = jwt(Map.of("realm_access", Map.of("roles", List.of("admin", 123, "user"))));

    assertThat(converter.convert(jwt))
        .extracting(GrantedAuthority::getAuthority)
        .containsExactly("ROLE_admin", "ROLE_user");
  }

  private static Jwt jwt(Map<String, Object> claims) {
    return Jwt.withTokenValue("test-token")
        .header("alg", "none")
        .claims(jwtClaims -> jwtClaims.putAll(claims))
        .build();
  }
}

