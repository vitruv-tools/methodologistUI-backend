package tools.vitruv.methodologist.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tools.vitruv.methodologist.messages.Error.USER_WRONG_PASSWORD_ERROR;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.jboss.resteasy.client.jaxrs.internal.ClientResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RoleMappingResource;
import org.keycloak.admin.client.resource.RoleResource;
import org.keycloak.admin.client.resource.RoleScopeResource;
import org.keycloak.admin.client.resource.RolesResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.admin.client.token.TokenManager;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import tools.vitruv.methodologist.exception.NotFoundException;
import tools.vitruv.methodologist.exception.UncheckedRuntimeException;
import tools.vitruv.methodologist.user.controller.dto.KeycloakUser;

@ExtendWith(MockitoExtension.class)
class KeycloakServiceTest {

  private static final String REALM = "test-realm";

  @Mock private Keycloak keycloakAdmin;
  @Mock private RealmResource realmResource;
  @Mock private UsersResource usersResource;
  @Mock private UserResource userResource;
  @Mock private RolesResource rolesResource;
  @Mock private RoleResource roleResource;
  @Mock private RoleMappingResource roleMappingResource;
  @Mock private RoleScopeResource roleScopeResource;
  @Mock private Response response;
  @Mock private ClientResponse clientResponse;
  @Mock private KeycloakBuilder keycloakBuilder;
  @Mock private Keycloak passwordKeycloak;
  @Mock private TokenManager tokenManager;

  private KeycloakService keycloakService;

  @BeforeEach
  void setUp() {
    keycloakService = createKeycloakService("http://localhost/dummy");

    ReflectionTestUtils.setField(keycloakService, "keycloakAdmin", keycloakAdmin);
    lenient().when(keycloakAdmin.realm(REALM)).thenReturn(realmResource);
  }

  @Test
  void assignUserRole_assignsRole_whenUserExists() {
    UserRepresentation userRepresentation = new UserRepresentation();
    userRepresentation.setId("user-1");
    RoleRepresentation roleRepresentation = new RoleRepresentation();

    when(realmResource.users()).thenReturn(usersResource);
    when(usersResource.search("alice")).thenReturn(List.of(userRepresentation));
    when(realmResource.roles()).thenReturn(rolesResource);
    when(rolesResource.get("USER")).thenReturn(roleResource);
    when(roleResource.toRepresentation()).thenReturn(roleRepresentation);
    when(usersResource.get("user-1")).thenReturn(userResource);
    when(userResource.roles()).thenReturn(roleMappingResource);
    when(roleMappingResource.realmLevel()).thenReturn(roleScopeResource);

    keycloakService.assignUserRole("alice", "USER");

    verify(roleScopeResource).add(List.of(roleRepresentation));
  }

  @Test
  void assignUserRole_throwsNotFound_whenUserDoesNotExist() {
    when(realmResource.users()).thenReturn(usersResource);
    when(usersResource.search("missing")).thenReturn(List.of());

    assertThatThrownBy(() -> keycloakService.assignUserRole("missing", "USER"))
        .isInstanceOf(NotFoundException.class);

    verify(realmResource, never()).roles();
  }

  @Test
  void createUser_createsUserAndAssignsRole_whenKeycloakCreatesUser() {
    KeycloakUser keycloakUser = createKeycloakUser();
    UserRepresentation createdUser = new UserRepresentation();
    createdUser.setId("user-1");
    RoleRepresentation roleRepresentation = new RoleRepresentation();

    when(realmResource.users()).thenReturn(usersResource);
    when(usersResource.create(any(UserRepresentation.class))).thenReturn(response);
    when(response.getStatus()).thenReturn(HttpStatus.CREATED.value());
    when(usersResource.search("alice")).thenReturn(List.of(createdUser));
    when(realmResource.roles()).thenReturn(rolesResource);
    when(rolesResource.get("USER")).thenReturn(roleResource);
    when(roleResource.toRepresentation()).thenReturn(roleRepresentation);
    when(usersResource.get("user-1")).thenReturn(userResource);
    when(userResource.roles()).thenReturn(roleMappingResource);
    when(roleMappingResource.realmLevel()).thenReturn(roleScopeResource);

    keycloakService.createUser(keycloakUser);

    ArgumentCaptor<UserRepresentation> userCaptor =
        ArgumentCaptor.forClass(UserRepresentation.class);
    verify(usersResource).create(userCaptor.capture());
    UserRepresentation userRepresentation = userCaptor.getValue();
    assertThat(userRepresentation.getUsername()).isEqualTo("alice");
    assertThat(userRepresentation.getEmail()).isEqualTo("alice@example.com");
    assertThat(userRepresentation.getFirstName()).isEqualTo("Alice");
    assertThat(userRepresentation.getLastName()).isEqualTo("Doe");
    assertThat(userRepresentation.isEnabled()).isTrue();
    assertThat(userRepresentation.getAttributes())
        .containsEntry(KeycloakService.USER_CONFIRMED, List.of("false"))
        .containsEntry(KeycloakService.ROLE_TYPE, List.of("USER"));
    assertThat(userRepresentation.getCredentials()).hasSize(1);
    assertThat(userRepresentation.getCredentials().get(0).getValue()).isEqualTo("p@ssw0rd");
    assertThat(userRepresentation.getCredentials().get(0).isTemporary()).isFalse();
    verify(response).close();
    verify(roleScopeResource).add(List.of(roleRepresentation));
  }

  @Test
  void createUser_throwsClientError_whenKeycloakDoesNotCreateUser() {
    when(realmResource.users()).thenReturn(usersResource);
    when(usersResource.create(any(UserRepresentation.class))).thenReturn(clientResponse);
    when(clientResponse.getStatus()).thenReturn(HttpStatus.BAD_REQUEST.value());
    when(clientResponse.getReasonPhrase()).thenReturn("Bad Request");

    assertThatThrownBy(() -> keycloakService.createUser(createKeycloakUser()))
        .isInstanceOf(ClientErrorException.class);

    verify(clientResponse, never()).close();
    verify(realmResource, never()).roles();
  }

  @Test
  void createUser_removesCreatedUser_whenRoleAssignmentFails() {
    KeycloakUser keycloakUser = createKeycloakUser();
    UserRepresentation createdUser = new UserRepresentation();
    createdUser.setId("user-1");
    RuntimeException roleError = new RuntimeException("role error");

    when(realmResource.users()).thenReturn(usersResource);
    when(usersResource.create(any(UserRepresentation.class))).thenReturn(response);
    when(response.getStatus()).thenReturn(HttpStatus.CREATED.value());
    when(usersResource.search("alice")).thenReturn(List.of(createdUser));
    when(realmResource.roles()).thenReturn(rolesResource);
    when(rolesResource.get("USER")).thenThrow(roleError);
    when(usersResource.get("user-1")).thenReturn(userResource);

    assertThatThrownBy(() -> keycloakService.createUser(keycloakUser)).isSameAs(roleError);

    verify(response).close();
    verify(userResource).remove();
  }

  @Test
  void removeUser_removesUser_whenUserExists() {
    UserRepresentation userRepresentation = new UserRepresentation();
    userRepresentation.setId("user-1");

    when(realmResource.users()).thenReturn(usersResource);
    when(usersResource.search("alice")).thenReturn(List.of(userRepresentation));
    when(usersResource.get("user-1")).thenReturn(userResource);

    keycloakService.removeUser("alice");

    verify(userResource).remove();
  }

  @Test
  void removeUser_throwsNotFound_whenUserDoesNotExist() {
    when(realmResource.users()).thenReturn(usersResource);
    when(usersResource.search("missing")).thenReturn(List.of());

    assertThatThrownBy(() -> keycloakService.removeUser("missing"))
        .isInstanceOf(NotFoundException.class);

    verify(usersResource, never()).get(anyString());
  }

  @Test
  void verifyUserPasswordOrThrow_doesNotThrow_whenPasswordIsValid() {
    try (MockedStatic<KeycloakBuilder> mockedBuilder = mockPasswordVerificationBuilder()) {
      when(tokenManager.getAccessTokenString()).thenReturn("access-token");

      keycloakService.verifyUserPasswordOrThrow("alice", "correct-password");

      verify(tokenManager).getAccessTokenString();
      mockedBuilder.verify(KeycloakBuilder::builder);
    }
  }

  @Test
  void verifyUserPasswordOrThrow_throwsBadRequest_whenPasswordIsWrong() {
    try (MockedStatic<KeycloakBuilder> ignored = mockPasswordVerificationBuilder()) {
      when(tokenManager.getAccessTokenString()).thenThrow(new NotAuthorizedException("Bearer"));

      assertThatThrownBy(() -> keycloakService.verifyUserPasswordOrThrow("alice", "wrong-password"))
          .isInstanceOf(BadRequestException.class)
          .hasMessageContaining(USER_WRONG_PASSWORD_ERROR);
    }
  }

  @Test
  void verifyUserPasswordOrThrow_throwsUncheckedRuntime_whenKeycloakFailsUnexpectedly() {
    try (MockedStatic<KeycloakBuilder> ignored = mockPasswordVerificationBuilder()) {
      when(tokenManager.getAccessTokenString()).thenThrow(new RuntimeException("server error"));

      assertThatThrownBy(() -> keycloakService.verifyUserPasswordOrThrow("alice", "password"))
          .isInstanceOf(UncheckedRuntimeException.class)
          .hasMessageContaining("server error");
    }
  }

  @Test
  void resetPassword_resetsPassword_whenUserExists() {
    UserRepresentation userRepresentation = new UserRepresentation();
    userRepresentation.setId("user-1");

    when(realmResource.users()).thenReturn(usersResource);
    when(usersResource.search("alice")).thenReturn(List.of(userRepresentation));
    when(usersResource.get("user-1")).thenReturn(userResource);

    keycloakService.resetPassword("alice", "new-password");

    ArgumentCaptor<CredentialRepresentation> credentialCaptor =
        ArgumentCaptor.forClass(CredentialRepresentation.class);
    verify(userResource).resetPassword(credentialCaptor.capture());
    CredentialRepresentation credentialRepresentation = credentialCaptor.getValue();
    assertThat(credentialRepresentation.getType()).isEqualTo(CredentialRepresentation.PASSWORD);
    assertThat(credentialRepresentation.getValue()).isEqualTo("new-password");
    assertThat(credentialRepresentation.isTemporary()).isFalse();
  }

  @Test
  void resetPassword_throwsIndexOutOfBounds_whenUserDoesNotExist() {
    when(realmResource.users()).thenReturn(usersResource);
    when(usersResource.search("missing")).thenReturn(List.of());

    assertThatThrownBy(() -> keycloakService.resetPassword("missing", "new-password"))
        .isInstanceOf(IndexOutOfBoundsException.class);

    verify(usersResource, never()).get(anyString());
  }

  @Test
  void sendResetPasswordEmail_sendsUpdatePasswordEmail_whenUserExists() {
    UserRepresentation userRepresentation = new UserRepresentation();
    userRepresentation.setId("user-1");

    when(realmResource.users()).thenReturn(usersResource);
    when(usersResource.search("alice")).thenReturn(List.of(userRepresentation));
    when(usersResource.get("user-1")).thenReturn(userResource);

    keycloakService.sendResetPasswordEmail("alice");

    verify(userResource).executeActionsEmail(List.of("UPDATE_PASSWORD"));
  }

  @Test
  void sendResetPasswordEmail_throwsIndexOutOfBounds_whenUserDoesNotExist() {
    when(realmResource.users()).thenReturn(usersResource);
    when(usersResource.search("missing")).thenReturn(List.of());

    assertThatThrownBy(() -> keycloakService.sendResetPasswordEmail("missing"))
        .isInstanceOf(IndexOutOfBoundsException.class);

    verify(usersResource, never()).get(anyString());
  }

  @Test
  void setPassword_resetsPassword_whenUserExists() {
    UserRepresentation userRepresentation = new UserRepresentation();
    userRepresentation.setId("user-1");

    when(realmResource.users()).thenReturn(usersResource);
    when(usersResource.search("alice")).thenReturn(List.of(userRepresentation));
    when(usersResource.get("user-1")).thenReturn(userResource);

    keycloakService.setPassword("alice", "new-password");

    ArgumentCaptor<CredentialRepresentation> credentialCaptor =
        ArgumentCaptor.forClass(CredentialRepresentation.class);
    verify(userResource).resetPassword(credentialCaptor.capture());
    CredentialRepresentation credentialRepresentation = credentialCaptor.getValue();
    assertThat(credentialRepresentation.getType()).isEqualTo(CredentialRepresentation.PASSWORD);
    assertThat(credentialRepresentation.getValue()).isEqualTo("new-password");
    assertThat(credentialRepresentation.isTemporary()).isFalse();
  }

  @Test
  void setPassword_throwsIndexOutOfBounds_whenUserDoesNotExist() {
    when(realmResource.users()).thenReturn(usersResource);
    when(usersResource.search("missing")).thenReturn(List.of());

    assertThatThrownBy(() -> keycloakService.setPassword("missing", "new-password"))
        .isInstanceOf(IndexOutOfBoundsException.class);

    verify(usersResource, never()).get(anyString());
  }

  @Test
  void existUser_returnsTrue_whenUserExists() {
    when(realmResource.users()).thenReturn(usersResource);
    when(usersResource.search("alice")).thenReturn(List.of(new UserRepresentation()));

    Boolean result = keycloakService.existUser("alice");

    assertThat(result).isTrue();
  }

  @Test
  void existUser_returnsFalse_whenUserDoesNotExist() {
    when(realmResource.users()).thenReturn(usersResource);
    when(usersResource.search("missing")).thenReturn(List.of());

    Boolean result = keycloakService.existUser("missing");

    assertThat(result).isFalse();
  }

  @Test
  void preparePasswordRepresentation_returnsPasswordCredential() {
    CredentialRepresentation credentialRepresentation =
        keycloakService.preparePasswordRepresentation("password", true);

    assertThat(credentialRepresentation.getType()).isEqualTo(CredentialRepresentation.PASSWORD);
    assertThat(credentialRepresentation.getValue()).isEqualTo("password");
    assertThat(credentialRepresentation.isTemporary()).isTrue();
  }

  private KeycloakService createKeycloakService(String authServerUrl) {
    return new KeycloakService(authServerUrl, REALM, "admin", "password", "secret", "client-id");
  }

  private MockedStatic<KeycloakBuilder> mockPasswordVerificationBuilder() {
    MockedStatic<KeycloakBuilder> mockedBuilder = mockStatic(KeycloakBuilder.class);
    mockedBuilder.when(KeycloakBuilder::builder).thenReturn(keycloakBuilder);
    when(keycloakBuilder.serverUrl("http://localhost/dummy")).thenReturn(keycloakBuilder);
    when(keycloakBuilder.grantType("password")).thenReturn(keycloakBuilder);
    when(keycloakBuilder.realm(REALM)).thenReturn(keycloakBuilder);
    when(keycloakBuilder.clientId("client-id")).thenReturn(keycloakBuilder);
    when(keycloakBuilder.username("alice")).thenReturn(keycloakBuilder);
    when(keycloakBuilder.password(anyString())).thenReturn(keycloakBuilder);
    when(keycloakBuilder.build()).thenReturn(passwordKeycloak);
    when(passwordKeycloak.tokenManager()).thenReturn(tokenManager);
    return mockedBuilder;
  }

  private KeycloakUser createKeycloakUser() {
    return KeycloakUser.builder()
        .username("alice")
        .email("alice@example.com")
        .firstName("Alice")
        .lastName("Doe")
        .password("p@ssw0rd")
        .role("USER")
        .build();
  }
}
