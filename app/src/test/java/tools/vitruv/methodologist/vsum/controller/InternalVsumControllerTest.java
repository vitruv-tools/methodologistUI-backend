package tools.vitruv.methodologist.vsum.controller;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static tools.vitruv.methodologist.messages.Error.VSUM_ID_NOT_FOUND_ERROR;

import java.util.List;
import java.util.Map;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.vitruv.methodologist.config.GrantedAuthoritiesConverter;
import tools.vitruv.methodologist.config.KeycloakAuthentication;
import tools.vitruv.methodologist.config.SecurityConfiguration;
import tools.vitruv.methodologist.exception.GlobalExceptionHandlerController;
import tools.vitruv.methodologist.exception.NotFoundException;
import tools.vitruv.methodologist.user.service.UserService;
import tools.vitruv.methodologist.vsum.VsumRole;
import tools.vitruv.methodologist.vsum.controller.dto.response.VsumMemberAccessResponse;
import tools.vitruv.methodologist.vsum.controller.dto.response.VsumMetaModelResponse;
import tools.vitruv.methodologist.vsum.service.VsumDeploymentBundleService;
import tools.vitruv.methodologist.vsum.service.VsumService;

/**
 * Checks who may call the internal endpoints of the collaboration server and that its service
 * account can't call the user endpoints, with the real security configuration and exception
 * handling. The context leaves out persistence, so no database is needed.
 */
@SpringBootTest(classes = InternalVsumControllerTest.TestApplication.class)
class InternalVsumControllerTest {
  private static final String CALLER_EMAIL = "alice@example.com";
  private static final String INTERNAL_DETAILS_URL = "/api/internal/v1/vsums/7/details";
  private static final String INTERNAL_SYNC_URL = "/api/internal/v1/vsums/7/sync-changes";
  private static final String INTERNAL_MEMBERS_URL = "/api/internal/v1/vsums/7/members";
  private static final String USER_DETAILS_URL = "/api/v1/vsums/7/details";
  private static final String USER_SYNC_URL = "/api/v1/vsums/7/sync-changes";

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

  @Test
  void details_returnsAnyProject_toTheServiceAccount() throws Exception {
    VsumMetaModelResponse response =
        VsumMetaModelResponse.builder()
            .id(7L)
            .name("Project")
            .metaModels(List.of())
            .metaModelsRelation(List.of())
            .views(List.of())
            .build();
    when(vsumService.findVsumWithDetailsForService(7L)).thenReturn(response);

    mockMvc
        .perform(get(INTERNAL_DETAILS_URL).with(serviceAccount()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(7))
        .andExpect(jsonPath("$.data.name").value("Project"))
        .andExpect(jsonPath("$.data.role").doesNotExist())
        .andExpect(jsonPath("$.data.userId").doesNotExist());
  }

  @Test
  void syncChanges_syncsAnyProject_forTheServiceAccount() throws Exception {
    mockMvc
        .perform(syncRequest(INTERNAL_SYNC_URL).with(serviceAccount()))
        .andExpect(status().isOk());

    verify(vsumService)
        .updateForService(
            eq(7L), argThat(request -> List.of(1L).equals(request.getMetaModelIds())));
  }

  @Test
  void members_returnsTheUserIdsAndRolesOfAnyProject_toTheServiceAccount() throws Exception {
    when(vsumService.findMembersForService(7L))
        .thenReturn(
            List.of(
                new VsumMemberAccessResponse(7L, VsumRole.OWNER),
                new VsumMemberAccessResponse(9L, VsumRole.MEMBER),
                new VsumMemberAccessResponse(12L, VsumRole.VIEWER)));

    mockMvc
        .perform(get(INTERNAL_MEMBERS_URL).with(serviceAccount()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(3))
        .andExpect(jsonPath("$.data[0].userId").value(7))
        .andExpect(jsonPath("$.data[0].role").value("OWNER"))
        .andExpect(jsonPath("$.data[1].userId").value(9))
        .andExpect(jsonPath("$.data[1].role").value("MEMBER"))
        .andExpect(jsonPath("$.data[2].userId").value(12))
        .andExpect(jsonPath("$.data[2].role").value("VIEWER"))
        .andExpect(jsonPath("$.data[0].email").doesNotExist())
        .andExpect(jsonPath("$.message").isEmpty());
  }

  @Test
  void members_answersNotFound_whenTheProjectIsMissingOrInTheTrash() throws Exception {
    when(vsumService.findMembersForService(7L))
        .thenThrow(new NotFoundException(VSUM_ID_NOT_FOUND_ERROR));

    mockMvc
        .perform(get(INTERNAL_MEMBERS_URL).with(serviceAccount()))
        .andExpect(status().isNotFound());
  }

  @Test
  void members_isForbidden_forTheBackendsOwnServiceAccount() throws Exception {
    mockMvc
        .perform(get(INTERNAL_MEMBERS_URL).with(authenticatedAs(backendServiceAccountJwt())))
        .andExpect(status().isForbidden());

    verifyNoInteractions(vsumService);
  }

  @Test
  void internalEndpoints_areForbidden_forUserTokens() throws Exception {
    mockMvc.perform(get(INTERNAL_DETAILS_URL).with(user())).andExpect(status().isForbidden());
    mockMvc.perform(syncRequest(INTERNAL_SYNC_URL).with(user())).andExpect(status().isForbidden());
    mockMvc.perform(get(INTERNAL_MEMBERS_URL).with(user())).andExpect(status().isForbidden());

    verifyNoInteractions(vsumService);
  }

  @Test
  void internalEndpoints_areUnauthorized_withoutToken() throws Exception {
    mockMvc.perform(get(INTERNAL_DETAILS_URL)).andExpect(status().isUnauthorized());
    mockMvc.perform(syncRequest(INTERNAL_SYNC_URL)).andExpect(status().isUnauthorized());
    mockMvc.perform(get(INTERNAL_MEMBERS_URL)).andExpect(status().isUnauthorized());

    verifyNoInteractions(vsumService);
  }

  @Test
  void userEndpoints_areForbidden_forTheServiceAccount() throws Exception {
    mockMvc.perform(get(USER_DETAILS_URL).with(serviceAccount())).andExpect(status().isForbidden());
    mockMvc
        .perform(syncRequest(USER_SYNC_URL).with(serviceAccount()))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(get("/api/v1/vsums/7/build/artifact").with(serviceAccount()))
        .andExpect(status().isForbidden());

    verifyNoInteractions(vsumService);
  }

  @Test
  void serviceAccountToken_doesNotCreateOrUpdateAPlatformUser() throws Exception {
    when(jwtDecoder.decode("service-token")).thenReturn(serviceAccountJwt("service-token"));
    when(vsumService.findVsumWithDetailsForService(7L)).thenReturn(new VsumMetaModelResponse());

    mockMvc
        .perform(
            get(INTERNAL_DETAILS_URL).header(HttpHeaders.AUTHORIZATION, "Bearer service-token"))
        .andExpect(status().isOk());

    verifyNoInteractions(userService);
  }

  @Test
  void userToken_stillSyncsThePlatformUser() throws Exception {
    when(jwtDecoder.decode("user-token")).thenReturn(userJwt("user-token"));

    mockMvc
        .perform(get(USER_DETAILS_URL).header(HttpHeaders.AUTHORIZATION, "Bearer user-token"))
        .andExpect(status().isOk());

    verify(userService).syncWithKeycloak(CALLER_EMAIL, "alice", "Alice", "Example");
  }

  private static MockHttpServletRequestBuilder syncRequest(String url) {
    return put(url).contentType(MediaType.APPLICATION_JSON).content("{\"metaModelIds\":[1]}");
  }

  private static RequestPostProcessor serviceAccount() {
    return authenticatedAs(serviceAccountJwt("token"));
  }

  private static RequestPostProcessor user() {
    return authenticatedAs(userJwt("token"));
  }

  private static RequestPostProcessor authenticatedAs(Jwt jwt) {
    return authentication(
        new KeycloakAuthentication(jwt, new GrantedAuthoritiesConverter().convert(jwt)));
  }

  /** A token as Keycloak issues it to the collaboration server: no email, no user role. */
  private static Jwt serviceAccountJwt(String token) {
    return Jwt.withTokenValue(token)
        .header("alg", "RS256")
        .subject("collaboration-service-account-id")
        .claim("preferred_username", "service-account-collaboration-service")
        .claim(
            "realm_access",
            Map.of("roles", List.of(SecurityConfiguration.COLLABORATION_SERVICE_ROLE)))
        .build();
  }

  /**
   * A token of the backend's own service account, which sends the notifications to the
   * collaboration server.
   */
  private static Jwt backendServiceAccountJwt() {
    return Jwt.withTokenValue("token")
        .header("alg", "RS256")
        .subject("backend-service-account-id")
        .claim("preferred_username", "service-account-methodologist-backend")
        .claim("realm_access", Map.of("roles", List.of("methodologist-backend")))
        .build();
  }

  private static Jwt userJwt(String token) {
    return Jwt.withTokenValue(token)
        .header("alg", "RS256")
        .claim("email", CALLER_EMAIL)
        .claim("preferred_username", "alice")
        .claim("given_name", "Alice")
        .claim("family_name", "Example")
        .claim("realm_access", Map.of("roles", List.of("user")))
        .build();
  }

  /** Security configuration, exception handling and the controllers; persistence is left out. */
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
    InternalVsumController.class,
    VsumController.class
  })
  static class TestApplication {}
}
