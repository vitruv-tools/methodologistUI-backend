package tools.vitruv.methodologist.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.vitruv.methodologist.config.KeycloakAuthentication;
import tools.vitruv.methodologist.config.SecurityConfiguration;
import tools.vitruv.methodologist.exception.GlobalExceptionHandlerController;
import tools.vitruv.methodologist.user.service.UserService;

/**
 * Checks that updating and removing users goes through method security, with the real security
 * configuration and exception handling. The context leaves out persistence, so no database is
 * needed; MockMvc is built by hand for the reason given in {@code ActuatorSecurityTest}.
 */
@SpringBootTest(classes = UserControllerSecurityTest.TestApplication.class)
class UserControllerSecurityTest {
  private static final String CALLER_EMAIL = "alice@example.com";
  private static final String RENAME_BODY = "{\"firstName\":\"Changed\",\"lastName\":\"Name\"}";

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

  @Test
  void update_isForbidden_withoutAToken() throws Exception {
    mockMvc.perform(rename(8)).andExpect(status().isForbidden());

    verify(userService, never()).update(any(), any(), any());
  }

  @Test
  void remove_isForbidden_withoutAToken() throws Exception {
    mockMvc.perform(delete("/api/v1/users/8")).andExpect(status().isForbidden());

    verify(userService, never()).remove(any(), any());
  }

  @Test
  void updateAndRemove_areForbidden_forATokenWithoutTheUserRole() throws Exception {
    mockMvc.perform(rename(8).with(caller(List.of()))).andExpect(status().isForbidden());
    mockMvc
        .perform(delete("/api/v1/users/8").with(caller(List.of())))
        .andExpect(status().isForbidden());

    verify(userService, never()).update(any(), any(), any());
    verify(userService, never()).remove(any(), any());
  }

  @Test
  void update_passesTheCallersEmail_toTheService() throws Exception {
    mockMvc.perform(rename(8).with(user())).andExpect(status().isOk());

    verify(userService).update(eq(CALLER_EMAIL), eq(8L), any());
  }

  @Test
  void remove_answersForbidden_whenTheServiceRejectsTheId() throws Exception {
    when(userService.remove(CALLER_EMAIL, 8L)).thenThrow(new AccessDeniedException("denied"));

    mockMvc
        .perform(delete("/api/v1/users/8").with(user()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("denied"));
  }

  private static MockHttpServletRequestBuilder rename(long id) {
    return put("/api/v1/users/" + id).contentType(MediaType.APPLICATION_JSON).content(RENAME_BODY);
  }

  private static RequestPostProcessor user() {
    return caller(List.of(new SimpleGrantedAuthority("ROLE_user")));
  }

  /** A caller as the security configuration would authenticate it from a Keycloak token. */
  private static RequestPostProcessor caller(List<GrantedAuthority> authorities) {
    Jwt jwt =
        Jwt.withTokenValue("token").header("alg", "none").claim("email", CALLER_EMAIL).build();
    return authentication(new KeycloakAuthentication(jwt, authorities));
  }

  /** Security configuration, exception handling and the controller; persistence is left out. */
  @SpringBootConfiguration
  @EnableAutoConfiguration(
      exclude = {
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        JpaRepositoriesAutoConfiguration.class,
        FlywayAutoConfiguration.class
      })
  @Import({
    SecurityConfiguration.class,
    GlobalExceptionHandlerController.class,
    UserController.class
  })
  static class TestApplication {}
}
