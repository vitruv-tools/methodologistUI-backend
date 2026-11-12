package tools.vitruv.methodologist.vsum.controller;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
import tools.vitruv.methodologist.exception.GlobalExceptionHandlerController;
import tools.vitruv.methodologist.user.service.UserService;
import tools.vitruv.methodologist.vsum.VsumRole;
import tools.vitruv.methodologist.vsum.controller.dto.response.VsumMetaModelResponse;
import tools.vitruv.methodologist.vsum.service.VsumDeploymentBundleService;
import tools.vitruv.methodologist.vsum.service.VsumService;

/**
 * Checks the serialized response of {@code GET /v1/vsums/{id}/details} with the real security
 * configuration, exception handling and JSON mapping. The context leaves out persistence, so no
 * database is needed.
 */
@SpringBootTest(classes = VsumControllerDetailsTest.TestApplication.class)
class VsumControllerDetailsTest {
  private static final String CALLER_EMAIL = "alice@example.com";
  private static final String DETAILS_URL = "/api/v1/vsums/7/details";

  @Autowired WebApplicationContext context;

  @MockitoBean VsumService vsumService;
  @MockitoBean VsumDeploymentBundleService vsumDeploymentBundleService;
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
  @EnumSource(VsumRole.class)
  void findVsumWithDetails_serializesCallerRole_alongsideExistingFields(VsumRole role)
      throws Exception {
    VsumMetaModelResponse response =
        VsumMetaModelResponse.builder()
            .id(7L)
            .name("Project")
            .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
            .updatedAt(Instant.parse("2026-01-02T00:00:00Z"))
            .metaModels(List.of())
            .metaModelsRelation(List.of())
            .views(List.of())
            .role(role)
            .build();
    when(vsumService.findVsumWithDetails(CALLER_EMAIL, 7L)).thenReturn(response);

    mockMvc
        .perform(get(DETAILS_URL).with(user()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.role").value(role.name()))
        .andExpect(jsonPath("$.data.id").value(7))
        .andExpect(jsonPath("$.data.name").value("Project"))
        .andExpect(jsonPath("$.data.metaModels").isArray())
        .andExpect(jsonPath("$.data.metaModelsRelation").isArray())
        .andExpect(jsonPath("$.data.views").isArray());
  }

  @Test
  void findVsumWithDetails_answersForbidden_whenCallerHasNoAccess() throws Exception {
    when(vsumService.findVsumWithDetails(CALLER_EMAIL, 7L))
        .thenThrow(new AccessDeniedException("denied"));

    mockMvc
        .perform(get(DETAILS_URL).with(user()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.data").doesNotExist());
  }

  private static RequestPostProcessor user() {
    Jwt jwt =
        Jwt.withTokenValue("token").header("alg", "none").claim("email", CALLER_EMAIL).build();
    return authentication(
        new KeycloakAuthentication(jwt, List.of(new SimpleGrantedAuthority("ROLE_user"))));
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
    VsumController.class
  })
  static class TestApplication {}
}
