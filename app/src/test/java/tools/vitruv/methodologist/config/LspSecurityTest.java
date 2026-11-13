package tools.vitruv.methodologist.config;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.vitruv.methodologist.user.service.UserService;

/**
 * Checks that {@link SecurityConfiguration} authenticates the handshake of every LSP WebSocket
 * ({@code /lsp}, {@code /ocl-lsp}, {@code /neojoin-lsp}), including a token sent as WebSocket
 * subprotocol. The context contains only the security configuration and no WebSocket handler, so a
 * request that passes security answers 404.
 *
 * <p>MockMvc is built by hand for the reason given in {@link ActuatorSecurityTest}.
 */
@SpringBootTest(classes = LspSecurityTest.TestApplication.class)
class LspSecurityTest {

  @Autowired WebApplicationContext context;

  @MockitoBean UserService userService;
  @MockitoBean JwtDecoder jwtDecoder;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(context)
            .apply(SecurityMockMvcConfigurers.springSecurity())
            .build();
  }

  @ParameterizedTest
  @ValueSource(strings = {"/lsp", "/ocl-lsp", "/neojoin-lsp"})
  void handshake_withoutToken_isUnauthorized(String path) throws Exception {
    mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
  }

  @ParameterizedTest
  @ValueSource(strings = {"/lsp", "/ocl-lsp", "/neojoin-lsp"})
  void handshake_withTokenWithoutUserRole_isForbidden(String path) throws Exception {
    mockMvc.perform(get(path).with(jwt())).andExpect(status().isForbidden());
  }

  @ParameterizedTest
  @ValueSource(strings = {"/lsp", "/ocl-lsp", "/neojoin-lsp"})
  void handshake_withUserRole_passesSecurity(String path) throws Exception {
    mockMvc
        .perform(get(path).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_user"))))
        .andExpect(status().isNotFound());
  }

  @ParameterizedTest
  @ValueSource(strings = {"/lsp", "/ocl-lsp", "/neojoin-lsp"})
  void handshake_withValidTokenAsSubprotocol_passesSecurity(String path) throws Exception {
    when(jwtDecoder.decode("valid-token")).thenReturn(userJwt("valid-token"));

    mockMvc.perform(withSubprotocolToken(path, "valid-token")).andExpect(status().isNotFound());
  }

  @ParameterizedTest
  @ValueSource(strings = {"/lsp", "/ocl-lsp", "/neojoin-lsp"})
  void handshake_withInvalidTokenAsSubprotocol_isUnauthorized(String path) throws Exception {
    when(jwtDecoder.decode("forged-token")).thenThrow(new BadJwtException("invalid"));

    mockMvc
        .perform(withSubprotocolToken(path, "forged-token"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void subprotocolToken_isIgnored_onOtherEndpoints() throws Exception {
    when(jwtDecoder.decode("valid-token")).thenReturn(userJwt("valid-token"));

    mockMvc
        .perform(get("/actuator/info").header("Sec-WebSocket-Protocol", "bearer, valid-token"))
        .andExpect(status().isUnauthorized());
  }

  private static MockHttpServletRequestBuilder withSubprotocolToken(String path, String token) {
    return get(path).header("Sec-WebSocket-Protocol", "bearer, " + token);
  }

  private static Jwt userJwt(String token) {
    return Jwt.withTokenValue(token)
        .header("alg", "RS256")
        .claim("email", "member@example.com")
        .claim("realm_access", Map.of("roles", List.of("user")))
        .build();
  }

  /** Security configuration only; persistence is left out on purpose. */
  @SpringBootConfiguration
  @EnableAutoConfiguration(
      exclude = {
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        JpaRepositoriesAutoConfiguration.class,
        FlywayAutoConfiguration.class
      })
  @Import(SecurityConfiguration.class)
  static class TestApplication {}
}
