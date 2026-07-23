package tools.vitruv.methodologist.user.service;

import jakarta.annotation.PreDestroy;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Optional;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Default {@link KeycloakGateway} implementation backed by the Keycloak Admin SDK. */
@Component
public class KeycloakGatewayImpl implements KeycloakGateway {

  private final Keycloak keycloakAdmin;
  private final String authServerUrl;
  private final String realm;
  private final String clientId;

  /**
   * Constructs a new KeycloakGatewayImpl with the specified configuration parameters.
   *
   * @param authServerUrl the base URL of the Keycloak server
   * @param realm the realm name
   * @param adminUsername the admin username for Keycloak access
   * @param adminPassword the admin password
   * @param secret the client secret
   * @param clientId the client ID
   */
  public KeycloakGatewayImpl(
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
            .serverUrl(authServerUrl)
            .realm("master")
            .clientId("admin-cli")
            .clientSecret(secret)
            .username(adminUsername)
            .password(adminPassword)
            .grantType(OAuth2Constants.PASSWORD)
            .build();
  }

  private static void requestAccessToken(
      String authServerUrl, String realm, String clientId, String username, String password) {
    try (Keycloak keycloak =
        KeycloakBuilder.builder()
            .serverUrl(authServerUrl)
            .grantType(OAuth2Constants.PASSWORD)
            .realm(realm)
            .clientId(clientId)
            .username(username)
            .password(password)
            .build()) {
      keycloak.tokenManager().getAccessTokenString();
    }
  }

  @PreDestroy
  void close() {
    keycloakAdmin.close();
  }

  @Override
  public UserCreationResult createUser(UserRepresentation userRepresentation) {
    try (Response response = keycloakAdmin.realm(realm).users().create(userRepresentation)) {
      final int status = response.getStatus();
      if (status != Response.Status.CREATED.getStatusCode()) {
        return new UserCreationResult(status, response.getStatusInfo().getReasonPhrase());
      }
      return new UserCreationResult(status, null);
    }
  }

  @Override
  public Optional<UserRepresentation> findUser(String username) {
    return keycloakAdmin.realm(realm).users().search(username).stream().findFirst();
  }

  @Override
  public void assignRealmRole(String userId, String role) {
    final RoleRepresentation roleRepresentation =
        keycloakAdmin.realm(realm).roles().get(role).toRepresentation();

    keycloakAdmin
        .realm(realm)
        .users()
        .get(userId)
        .roles()
        .realmLevel()
        .add(List.of(roleRepresentation));
  }

  @Override
  public void removeUser(String userId) {
    keycloakAdmin.realm(realm).users().get(userId).remove();
  }

  @Override
  public void updateUserProfile(String userId, String firstName, String lastName) {
    final var userResource = keycloakAdmin.realm(realm).users().get(userId);
    final UserRepresentation userRepresentation = userResource.toRepresentation();
    userRepresentation.setFirstName(firstName);
    userRepresentation.setLastName(lastName);
    userResource.update(userRepresentation);
  }

  @Override
  public void verifyPassword(String username, String password) {
    requestAccessToken(authServerUrl, realm, clientId, username, password);
  }

  @Override
  public void resetPassword(String userId, CredentialRepresentation credentialRepresentation) {
    keycloakAdmin.realm(realm).users().get(userId).resetPassword(credentialRepresentation);
  }

  @Override
  public void executeActionsEmail(String userId, List<String> actions) {
    keycloakAdmin.realm(realm).users().get(userId).executeActionsEmail(actions);
  }
}
