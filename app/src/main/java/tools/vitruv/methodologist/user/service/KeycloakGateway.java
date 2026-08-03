package tools.vitruv.methodologist.user.service;

import java.util.List;
import java.util.Optional;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

/** Gateway that hides Keycloak SDK details from service-layer business logic. */
public interface KeycloakGateway {

  /**
   * Creates a user in Keycloak.
   *
   * @param userRepresentation the prepared user representation
   * @return status and reason phrase returned by Keycloak
   */
  UserCreationResult createUser(UserRepresentation userRepresentation);

  /**
   * Finds a user by username.
   *
   * @param username the username to search for
   * @return the first matching user, if one exists
   */
  Optional<UserRepresentation> findUser(String username);

  /**
   * Assigns a realm role to a user.
   *
   * @param userId the Keycloak user ID
   * @param role the role to assign
   */
  void assignRealmRole(String userId, String role);

  /**
   * Removes a user by Keycloak user ID.
   *
   * @param userId the Keycloak user ID
   */
  void removeUser(String userId);

  /**
   * Updates a user's editable profile names by Keycloak user ID.
   *
   * @param userId the Keycloak user ID
   * @param firstName the first name to persist
   * @param lastName the last name to persist
   */
  void updateUserProfile(String userId, String firstName, String lastName);

  /**
   * Verifies a username and password against Keycloak.
   *
   * @param username the username to verify
   * @param password the password to verify
   */
  void verifyPassword(String username, String password);

  /**
   * Resets a user's password.
   *
   * @param userId the Keycloak user ID
   * @param credentialRepresentation the password credential to set
   */
  void resetPassword(String userId, CredentialRepresentation credentialRepresentation);

  /**
   * Sends a Keycloak required-actions email.
   *
   * @param userId the Keycloak user ID
   * @param actions the required actions to include in the email
   */
  void executeActionsEmail(String userId, List<String> actions);

  /** Result returned by Keycloak after a user creation request. */
  record UserCreationResult(int status, String reasonPhrase) {}
}
