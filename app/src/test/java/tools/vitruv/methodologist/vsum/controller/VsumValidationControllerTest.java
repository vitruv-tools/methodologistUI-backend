package tools.vitruv.methodologist.vsum.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.vitruv.methodologist.config.KeycloakAuthentication;
import tools.vitruv.methodologist.config.SecurityConfiguration;
import tools.vitruv.methodologist.exception.BuildProblem;
import tools.vitruv.methodologist.exception.GlobalExceptionHandlerController;
import tools.vitruv.methodologist.user.service.UserService;
import tools.vitruv.methodologist.vsum.controller.dto.response.VsumValidationResponse;
import tools.vitruv.methodologist.vsum.service.VsumValidationService;

@SpringBootTest(classes = VsumValidationControllerTest.TestApplication.class)
class VsumValidationControllerTest {

  private static final String CALLER_EMAIL = "alice@example.com";
  private static final String URL = "/api/v1/vsums/7/validation";

  @Autowired WebApplicationContext context;

  @MockitoBean VsumValidationService vsumValidationService;
  @MockitoBean JwtDecoder jwtDecoder;
  @MockitoBean UserService userService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(context)
            .apply(SecurityMockMvcConfigurers.springSecurity())
            .build();
  }

  @Test
  void returnsTheLatestResult() throws Exception {
    when(vsumValidationService.findLatestResult(CALLER_EMAIL, 7L))
        .thenReturn(
            Optional.of(
                VsumValidationResponse.builder()
                    .valid(false)
                    .problems(
                        List.of(
                            new BuildProblem(
                                "REACTIONS", "r.reactions", 21L, 3, 1, "broken", null, null, null)))
                    .validatedAt(Instant.parse("2026-11-06T10:15:30Z"))
                    .build()));

    mockMvc
        .perform(get(URL).with(user()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.valid").value(false))
        .andExpect(jsonPath("$.data.problems[0].fileId").value(21))
        .andExpect(jsonPath("$.data.problems[0].message").value("broken"))
        .andExpect(jsonPath("$.data.validatedAt").value("2026-11-06T10:15:30Z"));
  }

  @Test
  void returnsNullData_whenNothingWasValidatedYet() throws Exception {
    when(vsumValidationService.findLatestResult(CALLER_EMAIL, 7L)).thenReturn(Optional.empty());

    mockMvc
        .perform(get(URL).with(user()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").doesNotExist());
  }

  @Test
  void isForbidden_whenTheServiceDeniesAccess() throws Exception {
    when(vsumValidationService.findLatestResult(CALLER_EMAIL, 7L))
        .thenThrow(new AccessDeniedException("denied"));

    mockMvc.perform(get(URL).with(user())).andExpect(status().isForbidden());
  }

  @Test
  void isForbidden_withoutAToken() throws Exception {
    mockMvc.perform(get(URL)).andExpect(status().isForbidden());

    verify(vsumValidationService, never()).findLatestResult(any(String.class), any(Long.class));
  }

  @Test
  void isForbidden_forATokenWithoutTheUserRole() throws Exception {
    mockMvc.perform(get(URL).with(caller(List.of()))).andExpect(status().isForbidden());

    verify(vsumValidationService, never()).findLatestResult(any(String.class), any(Long.class));
  }

  private static RequestPostProcessor user() {
    return caller(List.of(new SimpleGrantedAuthority("ROLE_user")));
  }

  private static RequestPostProcessor caller(List<GrantedAuthority> authorities) {
    Jwt jwt =
        Jwt.withTokenValue("token").header("alg", "none").claim("email", CALLER_EMAIL).build();
    return authentication(new KeycloakAuthentication(jwt, authorities));
  }

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
    VsumValidationController.class
  })
  static class TestApplication {}
}
