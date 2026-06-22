package tools.vitruv.methodologist.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tools.vitruv.methodologist.messages.Error.USER_WRONG_PASSWORD_ERROR;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.NotAuthorizedException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import tools.vitruv.methodologist.exception.NotFoundException;
import tools.vitruv.methodologist.exception.UncheckedRuntimeException;
import tools.vitruv.methodologist.user.controller.dto.KeycloakUser;

@ExtendWith(MockitoExtension.class)
class KeycloakServiceTest {

  @Mock private KeycloakGateway keycloakGateway;

  private KeycloakService keycloakService;

  @BeforeEach
  void setUp() {
    keycloakService = new KeycloakService(keycloakGateway);
  }

  @Test
  void assignUserRole_assignsRole_whenUserExists() {
    when(keycloakGateway.findUser("alice")).thenReturn(Optional.of(userRepresentation("user-1")));

    keycloakService.assignUserRole("alice", "USER");

    verify(keycloakGateway).assignRealmRole("user-1", "USER");
  }

  @Test
  void assignUserRole_throwsNotFound_whenUserDoesNotExist() {
    when(keycloakGateway.findUser("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> keycloakService.assignUserRole("missing", "USER"))
        .isInstanceOf(NotFoundException.class);

    verify(keycloakGateway, never()).assignRealmRole(anyString(), anyString());
  }

  @Test
  void createUser_createsUserAndAssignsRole_whenKeycloakCreatesUser() {
    final UserRepresentation createdUser = userRepresentation("user-1");
    when(keycloakGateway.createUser(any(UserRepresentation.class)))
        .thenReturn(new KeycloakGateway.UserCreationResult(HttpStatus.CREATED.value(), "Created"));
    when(keycloakGateway.findUser("alice")).thenReturn(Optional.of(createdUser));

    keycloakService.createUser(createKeycloakUser());

    final ArgumentCaptor<UserRepresentation> userCaptor =
        ArgumentCaptor.forClass(UserRepresentation.class);
    verify(keycloakGateway).createUser(userCaptor.capture());
    final UserRepresentation userRepresentation = userCaptor.getValue();
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
    verify(keycloakGateway).assignRealmRole("user-1", "USER");
  }

  @Test
  void createUser_throwsClientError_whenKeycloakDoesNotCreateUser() {
    when(keycloakGateway.createUser(any(UserRepresentation.class)))
        .thenReturn(
            new KeycloakGateway.UserCreationResult(HttpStatus.BAD_REQUEST.value(), "Bad Request"));

    assertThatThrownBy(() -> keycloakService.createUser(createKeycloakUser()))
        .isInstanceOf(ClientErrorException.class);

    verify(keycloakGateway, never()).findUser(anyString());
    verify(keycloakGateway, never()).assignRealmRole(anyString(), anyString());
  }

  @Test
  void createUser_removesCreatedUser_whenRoleAssignmentFails() {
    final RuntimeException roleError = new RuntimeException("role error");
    when(keycloakGateway.createUser(any(UserRepresentation.class)))
        .thenReturn(new KeycloakGateway.UserCreationResult(HttpStatus.CREATED.value(), "Created"));
    when(keycloakGateway.findUser("alice")).thenReturn(Optional.of(userRepresentation("user-1")));
    doThrow(roleError).when(keycloakGateway).assignRealmRole("user-1", "USER");

    assertThatThrownBy(() -> keycloakService.createUser(createKeycloakUser())).isSameAs(roleError);

    verify(keycloakGateway).removeUser("user-1");
  }

  @Test
  void removeUser_removesUser_whenUserExists() {
    when(keycloakGateway.findUser("alice")).thenReturn(Optional.of(userRepresentation("user-1")));

    keycloakService.removeUser("alice");

    verify(keycloakGateway).removeUser("user-1");
  }

  @Test
  void removeUser_throwsNotFound_whenUserDoesNotExist() {
    when(keycloakGateway.findUser("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> keycloakService.removeUser("missing"))
        .isInstanceOf(NotFoundException.class);

    verify(keycloakGateway, never()).removeUser(anyString());
  }

  @Test
  void verifyUserPasswordOrThrow_doesNotThrow_whenPasswordIsValid() {
    keycloakService.verifyUserPasswordOrThrow("alice", "correct-password");

    verify(keycloakGateway).verifyPassword("alice", "correct-password");
  }

  @Test
  void verifyUserPasswordOrThrow_throwsBadRequest_whenPasswordIsWrong() {
    doThrow(new NotAuthorizedException("Bearer"))
        .when(keycloakGateway)
        .verifyPassword("alice", "wrong-password");

    assertThatThrownBy(() -> keycloakService.verifyUserPasswordOrThrow("alice", "wrong-password"))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining(USER_WRONG_PASSWORD_ERROR);
  }

  @Test
  void verifyUserPasswordOrThrow_throwsUncheckedRuntime_whenKeycloakFailsUnexpectedly() {
    doThrow(new RuntimeException("server error"))
        .when(keycloakGateway)
        .verifyPassword("alice", "password");

    assertThatThrownBy(() -> keycloakService.verifyUserPasswordOrThrow("alice", "password"))
        .isInstanceOf(UncheckedRuntimeException.class)
        .hasMessageContaining("server error");
  }

  @Test
  void resetPassword_resetsPassword_whenUserExists() {
    when(keycloakGateway.findUser("alice")).thenReturn(Optional.of(userRepresentation("user-1")));

    keycloakService.resetPassword("alice", "new-password");

    final ArgumentCaptor<CredentialRepresentation> credentialCaptor =
        ArgumentCaptor.forClass(CredentialRepresentation.class);
    verify(keycloakGateway).resetPassword(eq("user-1"), credentialCaptor.capture());
    final CredentialRepresentation credentialRepresentation = credentialCaptor.getValue();
    assertThat(credentialRepresentation.getType()).isEqualTo(CredentialRepresentation.PASSWORD);
    assertThat(credentialRepresentation.getValue()).isEqualTo("new-password");
    assertThat(credentialRepresentation.isTemporary()).isFalse();
  }

  @Test
  void resetPassword_throwsNotFound_whenUserDoesNotExist() {
    when(keycloakGateway.findUser("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> keycloakService.resetPassword("missing", "new-password"))
        .isInstanceOf(NotFoundException.class);

    verify(keycloakGateway, never())
        .resetPassword(anyString(), any(CredentialRepresentation.class));
  }

  @Test
  void sendResetPasswordEmail_sendsUpdatePasswordEmail_whenUserExists() {
    when(keycloakGateway.findUser("alice")).thenReturn(Optional.of(userRepresentation("user-1")));

    keycloakService.sendResetPasswordEmail("alice");

    verify(keycloakGateway).executeActionsEmail("user-1", List.of("UPDATE_PASSWORD"));
  }

  @Test
  void sendResetPasswordEmail_throwsNotFound_whenUserDoesNotExist() {
    when(keycloakGateway.findUser("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> keycloakService.sendResetPasswordEmail("missing"))
        .isInstanceOf(NotFoundException.class);

    verify(keycloakGateway, never()).executeActionsEmail(anyString(), any());
  }

  @Test
  void setPassword_resetsPassword_whenUserExists() {
    when(keycloakGateway.findUser("alice")).thenReturn(Optional.of(userRepresentation("user-1")));

    keycloakService.setPassword("alice", "new-password");

    final ArgumentCaptor<CredentialRepresentation> credentialCaptor =
        ArgumentCaptor.forClass(CredentialRepresentation.class);
    verify(keycloakGateway).resetPassword(eq("user-1"), credentialCaptor.capture());
    final CredentialRepresentation credentialRepresentation = credentialCaptor.getValue();
    assertThat(credentialRepresentation.getType()).isEqualTo(CredentialRepresentation.PASSWORD);
    assertThat(credentialRepresentation.getValue()).isEqualTo("new-password");
    assertThat(credentialRepresentation.isTemporary()).isFalse();
  }

  @Test
  void setPassword_throwsNotFound_whenUserDoesNotExist() {
    when(keycloakGateway.findUser("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> keycloakService.setPassword("missing", "new-password"))
        .isInstanceOf(NotFoundException.class);

    verify(keycloakGateway, never())
        .resetPassword(anyString(), any(CredentialRepresentation.class));
  }

  @Test
  void existUser_returnsTrue_whenUserExists() {
    when(keycloakGateway.findUser("alice")).thenReturn(Optional.of(userRepresentation("user-1")));

    final Boolean result = keycloakService.existUser("alice");

    assertThat(result).isTrue();
  }

  @Test
  void existUser_returnsFalse_whenUserDoesNotExist() {
    when(keycloakGateway.findUser("missing")).thenReturn(Optional.empty());

    final Boolean result = keycloakService.existUser("missing");

    assertThat(result).isFalse();
  }

  @Test
  void preparePasswordRepresentation_returnsPasswordCredential() {
    final CredentialRepresentation credentialRepresentation =
        keycloakService.preparePasswordRepresentation("password", true);

    assertThat(credentialRepresentation.getType()).isEqualTo(CredentialRepresentation.PASSWORD);
    assertThat(credentialRepresentation.getValue()).isEqualTo("password");
    assertThat(credentialRepresentation.isTemporary()).isTrue();
  }

  private UserRepresentation userRepresentation(String userId) {
    final UserRepresentation userRepresentation = new UserRepresentation();
    userRepresentation.setId(userId);
    return userRepresentation;
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
