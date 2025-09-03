package tools.vitruv.methodologist.user.service;

import static tools.vitruv.methodologist.messages.Error.USER_EMAIL_NOT_FOUND_ERROR;
import static tools.vitruv.methodologist.messages.Error.USER_WRONG_PASSWORD_ERROR;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.jboss.resteasy.client.jaxrs.internal.ClientResponse;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
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

  private final Keycloak keycloakAdmin;
  private final String authServerUrl;
  private final String realm;
  private final String clientId;

  /**
   * Constructs a new KeycloakService with the specified configuration parameters.
   *
   * @param authServerUrl the base URL of the Keycloak server
   * @param realm the realm name
   * @param adminUsername the admin username for Keycloak access
   * @param adminPassword the admin password
   * @param secret the client secret
   * @param clientId the client ID
   */
  public KeycloakService(
      @Value("${keycloak.url}") String authServerUrl,
      @Value("${keycloak.realm}") String realm,
      @Value("${keycloak.admin.username}") String adminUsername,
      @Value("${keycloak.admin.password}") String adminPassword,
      @Value("${keycloak.admin.client-secret}") String secret,
      @Value("${spring.security.oauth2.client.registration.keycloak.client-id}") String clientId) {
    this.authServerUrl = authServerUrl;
    this.realm = realm;
    this.clientId = clientId;
    this.keycloakAdmin =
        KeycloakBuilder.builder()
            .serverUrl(authServerUrl) // Replace with your Keycloak server URL
            .realm("master") // Replace with your realm name
            .clientId("admin-cli") // Replace with your client ID
            .clientSecret(secret) // Replace with your client secret
            .username(adminUsername) // Replace with a Keycloak admin user
            .password(adminPassword)
            .grantType(
                OAuth2Constants
                    .PASSWORD) // Use client credentials grant// Replace with the admin password
            .build();
    //        this.keycloakAdmin = Keycloak.getInstance(authServerUrl, realm, adminUsername,
    // adminPassword, clientId, secret);
  }

  private CredentialRepresentation preparePasswordRepresentation(
      String password, Boolean temporary) {
    CredentialRepresentation credentialRepresentation = new CredentialRepresentation();
    credentialRepresentation.setTemporary(temporary);
    credentialRepresentation.setType(CredentialRepresentation.PASSWORD);
    credentialRepresentation.setValue(password);
    return credentialRepresentation;
  }

  private UserRepresentation prepareUserRepresentation(KeycloakUser keycloakUser) {
    UserRepresentation userRepresentation = new UserRepresentation();

    // todo, we disable password change for all the manager users, even registrar! Change it in the
    // future flow!
    //        boolean forceChangePasswordOnFirstLogin = !keycloakUser.getRole().equals(USER_ROLE);
    CredentialRepresentation credentialRepresentation =
        preparePasswordRepresentation(
            keycloakUser.getPassword(), false /*forceChangePasswordOnFirstLogin*/);

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
    UserRepresentation userRepresentation = getUserRepresentationOrThrow(username);

    RoleRepresentation roleRepresentation =
        keycloakAdmin.realm(realm).roles().get(role).toRepresentation();

    keycloakAdmin
        .realm(realm)
        .users()
        .get(userRepresentation.getId())
        .roles()
        .realmLevel()
        .add(List.of(roleRepresentation));
  }

  /**
   * Creates a new user in Keycloak with the specified details and assigns roles.
   *
   * @param keycloakUser the user details to create
   * @throws jakarta.ws.rs.ClientErrorException if user creation fails
   */
  public void createUser(KeycloakUser keycloakUser) {
    UserRepresentation userRepresentation = prepareUserRepresentation(keycloakUser);

    Response response = keycloakAdmin.realm(realm).users().create(userRepresentation);
    if (response.getStatus() != HttpStatus.CREATED.value()) {
      throw new ClientErrorException(
          ((ClientResponse) response).getReasonPhrase(), response.getStatus());
    }
    response.close();

    /* assign role user to the new user in keycloak, if not remove the created user */
    String userName = keycloakUser.getUsername();
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
    UserRepresentation userRepresentation = getUserRepresentationOrThrow(username);
    keycloakAdmin.realm(realm).users().get(userRepresentation.getId()).remove();
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
  public void verifyUserPasswordOrThrow(String username, String password) {
    try {
      String dummy =
          KeycloakBuilder.builder()
              .serverUrl(authServerUrl)
              .grantType(OAuth2Constants.PASSWORD)
              .realm(realm)
              .clientId(clientId)
              .username(username)
              .password(password)
              .build()
              .tokenManager()
              .getAccessTokenString();
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
  public void resetPassword(String username, String password) {
    CredentialRepresentation credentialRepresentation =
        preparePasswordRepresentation(password, false);
    keycloakAdmin
        .realm(realm)
        .users()
        .get(keycloakAdmin.realm(realm).users().search(username).get(0).getId())
        .resetPassword(credentialRepresentation);
  }

  /**
   * Sends a reset password email to the user with instructions. Triggers Keycloak's built-in
   * password reset email workflow.
   *
   * @param username the username of the user to send reset email to
   */
  public void sendResetPasswordEmail(String username) {
    keycloakAdmin
        .realm(realm)
        .users()
        .get(keycloakAdmin.realm(realm).users().search(username).get(0).getId())
        .executeActionsEmail(List.of("UPDATE_PASSWORD"));
  }

  /**
   * Retrieves a user's representation from Keycloak.
   *
   * @param username the username to look up
   * @return UserRepresentation of the found user
   * @throws tools.vitruv.methodologist.exception.NotFoundException if the user doesn't exist
   */
  public UserRepresentation getUserRepresentationOrThrow(String username) {
    return keycloakAdmin.realm(realm).users().search(username).stream()
        .findFirst()
        .orElseThrow(() -> new NotFoundException(USER_EMAIL_NOT_FOUND_ERROR));
  }

  /**
   * Sets a new password for a user in Keycloak. Similar to resetPassword but used in different
   * contexts.
   *
   * @param username the username of the user
   * @param password the new password to set
   */
  public void setPassword(String username, String password) {
    CredentialRepresentation credentialRepresentation =
        preparePasswordRepresentation(password, false);
    keycloakAdmin
        .realm(realm)
        .users()
        .get(keycloakAdmin.realm(realm).users().search(username).get(0).getId())
        .resetPassword(credentialRepresentation);
  }

  /**
   * Checks if a user exists in Keycloak.
   *
   * @param username the username to check
   * @return true if the user exists, false otherwise
   */
  public Boolean existUser(String username) {
    return keycloakAdmin.realm(realm).users().search(username).stream().findFirst().isPresent();
  }
}
