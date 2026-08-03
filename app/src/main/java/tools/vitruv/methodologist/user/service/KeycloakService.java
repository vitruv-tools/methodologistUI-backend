package tools.vitruv.methodologist.user.service;

import static tools.vitruv.methodologist.messages.Error.USER_EMAIL_NOT_FOUND_ERROR;
import static tools.vitruv.methodologist.messages.Error.USER_WRONG_PASSWORD_ERROR;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.NotAuthorizedException;
import java.util.List;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.vitruv.methodologist.exception.NotFoundException;
import tools.vitruv.methodologist.exception.UncheckedRuntimeException;
import tools.vitruv.methodologist.user.controller.dto.KeycloakUser;

/**
 * Service component that handles user management operations with Keycloak authentication server.
 * Provides functionality for user creation, role management, password operations, and user profile
 * updates.
 */
@Component
public class KeycloakService {
  public static final String USER_CONFIRMED;
  public static final String ROLE_TYPE;

  static {
    USER_CONFIRMED = "user_confirmed";
    ROLE_TYPE = "role_type";
  }

  private final KeycloakGateway keycloakGateway;

  /**
   * Constructs a new KeycloakService with the specified Keycloak gateway.
   *
   * @param keycloakGateway gateway for Keycloak operations
   */
  public KeycloakService(KeycloakGateway keycloakGateway) {
    this.keycloakGateway = keycloakGateway;
  }

  /**
   * Builds a Keycloak {@link CredentialRepresentation} for a password.
   *
   * <p>Sets the credential {@code type} to {@link CredentialRepresentation#PASSWORD}, assigns the
   * provided plaintext password as the {@code value}, and marks it as temporary or permanent based
   * on the {@code temporary} flag.
   *
   * <p>Executed within a Spring transaction.
   *
   * @param password the plaintext password to set; must not be {@code null}
   * @param temporary whether the password is temporary (forces update on next login)
   * @return a populated {@link CredentialRepresentation}
   */
  public CredentialRepresentation preparePasswordRepresentation(
      String password, Boolean temporary) {
    CredentialRepresentation credentialRepresentation = new CredentialRepresentation();
    credentialRepresentation.setTemporary(temporary);
    credentialRepresentation.setType(CredentialRepresentation.PASSWORD);
    credentialRepresentation.setValue(password);
    return credentialRepresentation;
  }

  /**
   * Builds a Keycloak {@link UserRepresentation} from the provided {@link KeycloakUser}.
   *
   * <p>Populates username, email, first name, last name, enables the user, attaches a password
   * credential marked as non-temporary, and sets custom attributes: {@code user_confirmed} to
   * {@code "false"} and {@code role_type} to the user's role.
   *
   * <p>This method does not persist the user or assign roles; callers must use the Keycloak Admin
   * API to create the user and manage role assignments. <implNote>First-login password change is
   * disabled by creating the credential with {@code temporary = false}.</implNote>
   *
   * @param keycloakUser the source DTO containing username, email, first name, last name, password,
   *     and role
   * @return a populated {@link UserRepresentation} ready for creation via the Keycloak Admin API
   */
  private UserRepresentation prepareUserRepresentation(KeycloakUser keycloakUser) {
    UserRepresentation userRepresentation = new UserRepresentation();

    CredentialRepresentation credentialRepresentation =
        preparePasswordRepresentation(keycloakUser.getPassword(), false);

    userRepresentation.setUsername(keycloakUser.getUsername());
    userRepresentation.setEmail(keycloakUser.getEmail());
    userRepresentation.setFirstName(keycloakUser.getFirstName());
    userRepresentation.setLastName(keycloakUser.getLastName());
    userRepresentation.setCredentials(List.of(credentialRepresentation));
    userRepresentation.setEnabled(true);
    userRepresentation.singleAttribute(USER_CONFIRMED, "false");
    userRepresentation.singleAttribute(ROLE_TYPE, keycloakUser.getRole());
    return userRepresentation;
  }

  /**
   * Assigns a role to a user in Keycloak.
   *
   * @param username the username of the user
   * @param role the role to assign
   */
  public void assignUserRole(String username, String role) {
    final UserRepresentation userRepresentation = getUserRepresentationOrThrow(username);
    keycloakGateway.assignRealmRole(userRepresentation.getId(), role);
  }

  /**
   * Creates a new user in Keycloak with the specified details and assigns roles.
   *
   * @param keycloakUser the user details to create
   * @throws jakarta.ws.rs.ClientErrorException if user creation fails
   */
  @Transactional
  public void createUser(KeycloakUser keycloakUser) {
    final UserRepresentation userRepresentation = prepareUserRepresentation(keycloakUser);

    final KeycloakGateway.UserCreationResult creationResult =
        keycloakGateway.createUser(userRepresentation);
    if (creationResult.status() != HttpStatus.CREATED.value()) {
      throw new ClientErrorException(creationResult.reasonPhrase(), creationResult.status());
    }

    /* assign role user to the new user in keycloak, if not remove the created user */
    final String userName = keycloakUser.getUsername();
    try {
      assignUserRole(userName, keycloakUser.getRole());
    } catch (Exception any) {
      removeUser(userName);
      throw any;
    }
  }

  /**
   * Removes a user from Keycloak.
   *
   * @param username the username of the user to remove
   */
  public void removeUser(String username) {
    final UserRepresentation userRepresentation = getUserRepresentationOrThrow(username);
    keycloakGateway.removeUser(userRepresentation.getId());
  }

  /**
   * Updates a user's editable profile names in Keycloak.
   *
   * @param username the username of the user to update
   * @param firstName the first name to persist
   * @param lastName the last name to persist
   */
  @Transactional
  public void updateUserProfile(String username, String firstName, String lastName) {
    final UserRepresentation userRepresentation = getUserRepresentationOrThrow(username);
    keycloakGateway.updateUserProfile(userRepresentation.getId(), firstName, lastName);
  }

  /**
   * Verifies a user's password against Keycloak.
   *
   * @param username the username
   * @param password the password to verify
   * @throws jakarta.ws.rs.BadRequestException if the password is incorrect
   * @throws tools.vitruv.methodologist.exception.UncheckedRuntimeException for other authentication
   *     errors
   */
  @Transactional
  public void verifyUserPasswordOrThrow(String username, String password) {
    try {
      keycloakGateway.verifyPassword(username, password);
    } catch (NotAuthorizedException notAuthorizedException) {
      throw new BadRequestException(USER_WRONG_PASSWORD_ERROR);
    } catch (Exception e) {
      throw new UncheckedRuntimeException(e.getMessage());
    }
  }

  /**
   * Resets a user's password in Keycloak.
   *
   * @param username the username of the user
   * @param password the new password to set
   */
  @Transactional
  public void resetPassword(String username, String password) {
    final CredentialRepresentation credentialRepresentation =
        preparePasswordRepresentation(password, false);
    final UserRepresentation userRepresentation =
        keycloakGateway.findUser(username).orElseThrow(IndexOutOfBoundsException::new);
    keycloakGateway.resetPassword(userRepresentation.getId(), credentialRepresentation);
  }

  /**
   * Sends a reset password email to the user with instructions. Triggers Keycloak's built-in
   * password reset email workflow.
   *
   * @param username the username of the user to send reset email to
   */
  @Transactional
  public void sendResetPasswordEmail(String username) {
    final UserRepresentation userRepresentation =
        keycloakGateway.findUser(username).orElseThrow(IndexOutOfBoundsException::new);
    keycloakGateway.executeActionsEmail(userRepresentation.getId(), List.of("UPDATE_PASSWORD"));
  }

  /**
   * Retrieves a user's representation from Keycloak.
   *
   * @param username the username to look up
   * @return UserRepresentation of the found user
   * @throws tools.vitruv.methodologist.exception.NotFoundException if the user doesn't exist
   */
  private UserRepresentation getUserRepresentationOrThrow(String username) {
    return keycloakGateway
        .findUser(username)
        .orElseThrow(() -> new NotFoundException(USER_EMAIL_NOT_FOUND_ERROR));
  }

  /**
   * Sets a new password for a user in Keycloak. Similar to resetPassword but used in different
   * contexts.
   *
   * @param username the username of the user
   * @param password the new password to set
   */
  @Transactional
  public void setPassword(String username, String password) {
    final CredentialRepresentation credentialRepresentation =
        preparePasswordRepresentation(password, false);
    final UserRepresentation userRepresentation =
        keycloakGateway.findUser(username).orElseThrow(IndexOutOfBoundsException::new);
    keycloakGateway.resetPassword(userRepresentation.getId(), credentialRepresentation);
  }

  /**
   * Checks if a user exists in Keycloak.
   *
   * @param username the username to check
   * @return true if the user exists, false otherwise
   */
  @Transactional
  public Boolean existUser(String username) {
    return keycloakGateway.findUser(username).isPresent();
  }
}
