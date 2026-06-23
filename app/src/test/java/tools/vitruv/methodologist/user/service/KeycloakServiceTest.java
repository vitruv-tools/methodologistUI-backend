package tools.vitruv.methodologist.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tools.vitruv.methodologist.messages.Error.USER_WRONG_PASSWORD_ERROR;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.NotAuthorizedException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.http.HttpStatus;
import tools.vitruv.methodologist.exception.NotFoundException;
import tools.vitruv.methodologist.exception.UncheckedRuntimeException;
import tools.vitruv.methodologist.user.controller.dto.KeycloakUser;

class KeycloakServiceTest {

  private FakeKeycloakGateway keycloakGateway;
  private KeycloakService keycloakService;

  @BeforeEach
  void setUp() {
    keycloakGateway = new FakeKeycloakGateway();
    keycloakService = new KeycloakService(keycloakGateway);
  }

  @Test
  void assignUserRole_assignsRole_whenUserExists() {
    keycloakGateway.addUser("alice", "user-1");

    keycloakService.assignUserRole("alice", "USER");

    assertThat(keycloakGateway.assignedRoleUserId).isEqualTo("user-1");
    assertThat(keycloakGateway.assignedRole).isEqualTo("USER");
  }

  @Test
  void assignUserRole_throwsNotFound_whenUserDoesNotExist() {
    assertThatThrownBy(() -> keycloakService.assignUserRole("missing", "USER"))
        .isInstanceOf(NotFoundException.class);

    assertThat(keycloakGateway.assignedRole).isNull();
  }

  @Test
  void createUser_createsUserAndAssignsRole_whenKeycloakCreatesUser() {
    keycloakService.createUser(createKeycloakUser());

    final UserRepresentation createdUser = keycloakGateway.createdUserRepresentation;
    assertThat(createdUser.getUsername()).isEqualTo("alice");
    assertThat(createdUser.getEmail()).isEqualTo("alice@example.com");
    assertThat(createdUser.getFirstName()).isEqualTo("Alice");
    assertThat(createdUser.getLastName()).isEqualTo("Doe");
    assertThat(createdUser.isEnabled()).isTrue();
    assertThat(createdUser.getAttributes())
        .containsEntry(KeycloakService.USER_CONFIRMED, List.of("false"))
        .containsEntry(KeycloakService.ROLE_TYPE, List.of("USER"));
    assertThat(createdUser.getCredentials()).hasSize(1);
    assertThat(createdUser.getCredentials().get(0).getValue()).isEqualTo("p@ssw0rd");
    assertThat(createdUser.getCredentials().get(0).isTemporary()).isFalse();
    assertThat(keycloakGateway.assignedRoleUserId).isEqualTo("user-1");
    assertThat(keycloakGateway.assignedRole).isEqualTo("USER");
  }

  @Test
  void createUser_throwsClientError_whenKeycloakDoesNotCreateUser() {
    keycloakGateway.creationResult =
        new KeycloakGateway.UserCreationResult(HttpStatus.BAD_REQUEST.value(), "Bad Request");

    assertThatThrownBy(() -> keycloakService.createUser(createKeycloakUser()))
        .isInstanceOf(ClientErrorException.class);

    assertThat(keycloakGateway.createdUserRepresentation).isNotNull();
    assertThat(keycloakGateway.assignedRole).isNull();
  }

  @Test
  void createUser_removesCreatedUser_whenRoleAssignmentFails() {
    final RuntimeException roleError = new RuntimeException("role error");
    keycloakGateway.assignRoleException = roleError;

    assertThatThrownBy(() -> keycloakService.createUser(createKeycloakUser())).isSameAs(roleError);

    assertThat(keycloakGateway.removedUserIds).containsExactly("user-1");
  }

  @Test
  void removeUser_removesUser_whenUserExists() {
    keycloakGateway.addUser("alice", "user-1");

    keycloakService.removeUser("alice");

    assertThat(keycloakGateway.removedUserIds).containsExactly("user-1");
  }

  @Test
  void removeUser_throwsNotFound_whenUserDoesNotExist() {
    assertThatThrownBy(() -> keycloakService.removeUser("missing"))
        .isInstanceOf(NotFoundException.class);

    assertThat(keycloakGateway.removedUserIds).isEmpty();
  }

  @Test
  void verifyUserPasswordOrThrow_doesNotThrow_whenPasswordIsValid() {
    keycloakService.verifyUserPasswordOrThrow("alice", "correct-password");

    assertThat(keycloakGateway.verifiedUsername).isEqualTo("alice");
    assertThat(keycloakGateway.verifiedPassword).isEqualTo("correct-password");
  }

  @Test
  void verifyUserPasswordOrThrow_throwsBadRequest_whenPasswordIsWrong() {
    keycloakGateway.verifyPasswordException = new NotAuthorizedException("Bearer");

    assertThatThrownBy(() -> keycloakService.verifyUserPasswordOrThrow("alice", "wrong-password"))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining(USER_WRONG_PASSWORD_ERROR);
  }

  @Test
  void verifyUserPasswordOrThrow_throwsUncheckedRuntime_whenKeycloakFailsUnexpectedly() {
    keycloakGateway.verifyPasswordException = new RuntimeException("server error");

    assertThatThrownBy(() -> keycloakService.verifyUserPasswordOrThrow("alice", "password"))
        .isInstanceOf(UncheckedRuntimeException.class)
        .hasMessageContaining("server error");
  }

  @Test
  void resetPassword_resetsPassword_whenUserExists() {
    keycloakGateway.addUser("alice", "user-1");

    keycloakService.resetPassword("alice", "new-password");

    final CredentialRepresentation credentialRepresentation =
        keycloakGateway.resetPasswordCredential;
    assertThat(keycloakGateway.resetPasswordUserId).isEqualTo("user-1");
    assertThat(credentialRepresentation.getType()).isEqualTo(CredentialRepresentation.PASSWORD);
    assertThat(credentialRepresentation.getValue()).isEqualTo("new-password");
    assertThat(credentialRepresentation.isTemporary()).isFalse();
  }

  @Test
  void resetPassword_throwsIndexOutOfBounds_whenUserDoesNotExist() {
    assertThatThrownBy(() -> keycloakService.resetPassword("missing", "new-password"))
        .isInstanceOf(IndexOutOfBoundsException.class);

    assertThat(keycloakGateway.resetPasswordCredential).isNull();
  }

  @Test
  void sendResetPasswordEmail_sendsUpdatePasswordEmail_whenUserExists() {
    keycloakGateway.addUser("alice", "user-1");

    keycloakService.sendResetPasswordEmail("alice");

    assertThat(keycloakGateway.actionsEmailUserId).isEqualTo("user-1");
    assertThat(keycloakGateway.actionsEmailActions).containsExactly("UPDATE_PASSWORD");
  }

  @Test
  void sendResetPasswordEmail_throwsIndexOutOfBounds_whenUserDoesNotExist() {
    assertThatThrownBy(() -> keycloakService.sendResetPasswordEmail("missing"))
        .isInstanceOf(IndexOutOfBoundsException.class);

    assertThat(keycloakGateway.actionsEmailActions).isEmpty();
  }

  @Test
  void setPassword_resetsPassword_whenUserExists() {
    keycloakGateway.addUser("alice", "user-1");

    keycloakService.setPassword("alice", "new-password");

    final CredentialRepresentation credentialRepresentation =
        keycloakGateway.resetPasswordCredential;
    assertThat(keycloakGateway.resetPasswordUserId).isEqualTo("user-1");
    assertThat(credentialRepresentation.getType()).isEqualTo(CredentialRepresentation.PASSWORD);
    assertThat(credentialRepresentation.getValue()).isEqualTo("new-password");
    assertThat(credentialRepresentation.isTemporary()).isFalse();
  }

  @Test
  void setPassword_throwsIndexOutOfBounds_whenUserDoesNotExist() {
    assertThatThrownBy(() -> keycloakService.setPassword("missing", "new-password"))
        .isInstanceOf(IndexOutOfBoundsException.class);

    assertThat(keycloakGateway.resetPasswordCredential).isNull();
  }

  @Test
  void existUser_returnsTrue_whenUserExists() {
    keycloakGateway.addUser("alice", "user-1");

    final Boolean result = keycloakService.existUser("alice");

    assertThat(result).isTrue();
  }

  @Test
  void existUser_returnsFalse_whenUserDoesNotExist() {
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

  private static final class FakeKeycloakGateway implements KeycloakGateway {

    private final Map<String, UserRepresentation> usersByUsername = new HashMap<>();
    private final List<String> removedUserIds = new ArrayList<>();
    private final List<String> actionsEmailActions = new ArrayList<>();

    private UserCreationResult creationResult =
        new UserCreationResult(HttpStatus.CREATED.value(), "Created");
    private RuntimeException assignRoleException;
    private RuntimeException verifyPasswordException;
    private UserRepresentation createdUserRepresentation;
    private String assignedRoleUserId;
    private String assignedRole;
    private String verifiedUsername;
    private String verifiedPassword;
    private String resetPasswordUserId;
    private CredentialRepresentation resetPasswordCredential;
    private String actionsEmailUserId;

    @Override
    public UserCreationResult createUser(UserRepresentation userRepresentation) {
      createdUserRepresentation = userRepresentation;
      if (creationResult.status() == HttpStatus.CREATED.value()) {
        addUser(userRepresentation.getUsername(), "user-1");
      }
      return creationResult;
    }

    @Override
    public Optional<UserRepresentation> findUser(String username) {
      return Optional.ofNullable(usersByUsername.get(username));
    }

    @Override
    public void assignRealmRole(String userId, String role) {
      if (assignRoleException != null) {
        throw assignRoleException;
      }
      assignedRoleUserId = userId;
      assignedRole = role;
    }

    @Override
    public void removeUser(String userId) {
      removedUserIds.add(userId);
      usersByUsername
          .values()
          .removeIf(userRepresentation -> userId.equals(userRepresentation.getId()));
    }

    @Override
    public void verifyPassword(String username, String password) {
      verifiedUsername = username;
      verifiedPassword = password;
      if (verifyPasswordException != null) {
        throw verifyPasswordException;
      }
    }

    @Override
    public void resetPassword(String userId, CredentialRepresentation credentialRepresentation) {
      resetPasswordUserId = userId;
      resetPasswordCredential = credentialRepresentation;
    }

    @Override
    public void executeActionsEmail(String userId, List<String> actions) {
      actionsEmailUserId = userId;
      actionsEmailActions.clear();
      actionsEmailActions.addAll(actions);
    }

    private void addUser(String username, String userId) {
      final UserRepresentation userRepresentation = new UserRepresentation();
      userRepresentation.setUsername(username);
      userRepresentation.setId(userId);
      usersByUsername.put(username, userRepresentation);
    }
  }
}
