package com.vitruv.methodologist.user.service;

import static com.vitruv.methodologist.messages.Error.USER_EMAIL_NOT_FOUND_ERROR;
import static com.vitruv.methodologist.messages.Error.USER_WRONG_PASSWORD_ERROR;

import com.vitruv.methodologist.exception.NotFoundException;
import com.vitruv.methodologist.exception.UncaughtRuntimeException;
import com.vitruv.methodologist.user.controller.dto.KeycloakUser;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.NotAuthorizedException;
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
    var credentialRepresentation =
        preparePasswordRepresentation(
            keycloakUser.getPassword(), false /*forceChangePasswordOnFirstLogin*/);

    userRepresentation.setUsername(keycloakUser.getUsername());
    userRepresentation.setEmail(keycloakUser.getEmail());
    userRepresentation.setFirstName(keycloakUser.getFirstName());
    userRepresentation.setLastName(keycloakUser.getLastName());
    userRepresentation.setCredentials(List.of(credentialRepresentation));
    userRepresentation.setEnabled(true);
    /* force verified email: userRepresentation.setEmailVerified(true) */
    userRepresentation.singleAttribute(USER_CONFIRMED, "false");
    userRepresentation.singleAttribute(ROLE_TYPE, keycloakUser.getRole());
    /* force verified email and update password: setRequiredActions(List.of("UPDATE_PASSWORD", "VERIFY_EMAIL")) */

    return userRepresentation;
  }

  public void assignUserRole(String username, String role) {
    var userRepresentation = getUserRepresentationOrThrow(username);

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

  public void createUser(KeycloakUser keycloakUser) {
    UserRepresentation userRepresentation = prepareUserRepresentation(keycloakUser);

    var response = keycloakAdmin.realm(realm).users().create(userRepresentation);
    if (response.getStatus() != HttpStatus.CREATED.value())
      throw new ClientErrorException(
          ((ClientResponse) response).getReasonPhrase(), response.getStatus());
    response.close();

    /* assign role user to the new user in keycloak, if not remove the created user */
    var userName = keycloakUser.getUsername();
    try {
      assignUserRole(userName, keycloakUser.getRole());
    } catch (Exception any) {
      removeUser(userName);
      throw any;
    }
  }

  public void removeUser(String username) {
    var userRepresentation = getUserRepresentationOrThrow(username);

    keycloakAdmin.realm(realm).users().get(userRepresentation.getId()).remove();
  }

  public void confirmUser(String username) {
    var userRepresentation = getUserRepresentationOrThrow(username);

    keycloakAdmin
        .realm(realm)
        .users()
        .get(userRepresentation.getId())
        .update(userRepresentation.singleAttribute(USER_CONFIRMED, "true"));
  }

  public void updateName(String username, String firstName, String lastName) {
    var userRepresentation = getUserRepresentationOrThrow(username);
    userRepresentation.setFirstName(firstName);
    userRepresentation.setLastName(lastName);
    keycloakAdmin.realm(realm).users().get(userRepresentation.getId()).update(userRepresentation);
  }

  public void updateUsernameAndEmail(String username, String email) {
    var userRepresentation = getUserRepresentationOrThrow(username);
    userRepresentation.setUsername(email);
    userRepresentation.setEmail(email);
    keycloakAdmin.realm(realm).users().get(userRepresentation.getId()).update(userRepresentation);
  }

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
    } catch (NotAuthorizedException notAuthorizedException) // change it!
    {
      throw new BadRequestException(USER_WRONG_PASSWORD_ERROR);
    } catch (Exception e) {
      throw new UncaughtRuntimeException(e.getMessage());
    }
  }

  public void resetPassword(String username, String password) {
    var credentialRepresentation = preparePasswordRepresentation(password, false);
    keycloakAdmin
        .realm(realm)
        .users()
        .get(keycloakAdmin.realm(realm).users().search(username).get(0).getId())
        .resetPassword(credentialRepresentation);
  }

  public void sendResetPasswordEmail(String username) {
    keycloakAdmin
        .realm(realm)
        .users()
        .get(keycloakAdmin.realm(realm).users().search(username).get(0).getId())
        .executeActionsEmail(List.of("UPDATE_PASSWORD"));
  }

  public UserRepresentation getUserRepresentationOrThrow(String username) {
    return keycloakAdmin.realm(realm).users().search(username).stream()
        .findFirst()
        .orElseThrow(() -> new NotFoundException(USER_EMAIL_NOT_FOUND_ERROR));
  }

  public void setPassword(String username, String password) {
    var credentialRepresentation = preparePasswordRepresentation(password, false);
    keycloakAdmin
        .realm(realm)
        .users()
        .get(keycloakAdmin.realm(realm).users().search(username).get(0).getId())
        .resetPassword(credentialRepresentation);
  }

  public Boolean existUser(String username) {
    return keycloakAdmin.realm(realm).users().search(username).stream().findFirst().isPresent();
  }
}
