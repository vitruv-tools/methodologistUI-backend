package tools.vitruv.methodologist.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

class KeycloakGatewayImplTest {

  private static final String REALM = "methodologist";
  private static final String CLIENT_ID = "methodologist-client";
  private static final String ADMIN_USERNAME = "admin";
  private static final String ADMIN_PASSWORD = "admin-password";
  private static final String CLIENT_SECRET = "client-secret";

  @Test
  void createUser_returnsCreatedResult_whenKeycloakCreatesUser() throws Exception {
    try (KeycloakServerFixture fixture = new KeycloakServerFixture()) {
      fixture.enqueueTokenResponse();
      fixture.enqueue(new MockResponse().setResponseCode(201));
      final UserRepresentation userRepresentation = new UserRepresentation();
      userRepresentation.setUsername("alice");

      final KeycloakGateway.UserCreationResult result =
          fixture.gateway.createUser(userRepresentation);

      assertThat(result.status()).isEqualTo(201);
      assertThat(result.reasonPhrase()).isNull();
      fixture.assertAdminTokenRequest(fixture.takeRequest());
      final RecordedRequest createRequest = fixture.takeRequest();
      assertThat(createRequest.getMethod()).isEqualTo("POST");
      assertThat(createRequest.getPath()).isEqualTo("/admin/realms/methodologist/users");
      assertThat(createRequest.getBody().readUtf8()).contains("\"username\":\"alice\"");
    }
  }

  @Test
  void createUser_returnsErrorResult_whenKeycloakRejectsUser() throws Exception {
    try (KeycloakServerFixture fixture = new KeycloakServerFixture()) {
      fixture.enqueueTokenResponse();
      fixture.enqueue(
          new MockResponse()
              .setStatus("HTTP/1.1 400 Bad Request")
              .setHeader("Content-Type", "application/json")
              .setBody("{}"));
      final UserRepresentation userRepresentation = new UserRepresentation();

      final KeycloakGateway.UserCreationResult result =
          fixture.gateway.createUser(userRepresentation);

      assertThat(result.status()).isEqualTo(400);
      assertThat(result.reasonPhrase()).isEqualTo("Bad Request");
      fixture.assertAdminTokenRequest(fixture.takeRequest());
      final RecordedRequest createRequest = fixture.takeRequest();
      assertThat(createRequest.getMethod()).isEqualTo("POST");
      assertThat(createRequest.getPath()).isEqualTo("/admin/realms/methodologist/users");
    }
  }

  @Test
  void findUser_returnsFirstMatchingUser() throws Exception {
    try (KeycloakServerFixture fixture = new KeycloakServerFixture()) {
      fixture.enqueueTokenResponse();
      fixture.enqueueJson(
          """
          [
            {"id": "user-1", "username": "alice"},
            {"id": "user-2", "username": "alice.duplicate"}
          ]
          """);

      final Optional<UserRepresentation> result = fixture.gateway.findUser("alice");

      assertThat(result).isPresent();
      assertThat(result.orElseThrow().getId()).isEqualTo("user-1");
      fixture.assertAdminTokenRequest(fixture.takeRequest());
      final RecordedRequest searchRequest = fixture.takeRequest();
      assertThat(searchRequest.getMethod()).isEqualTo("GET");
      assertThat(searchRequest.getPath())
          .startsWith("/admin/realms/methodologist/users?")
          .contains("username=alice");
    }
  }

  @Test
  void findUser_returnsEmpty_whenNoUserMatches() throws Exception {
    try (KeycloakServerFixture fixture = new KeycloakServerFixture()) {
      fixture.enqueueTokenResponse();
      fixture.enqueueJson("[]");

      final Optional<UserRepresentation> result = fixture.gateway.findUser("missing");

      assertThat(result).isEmpty();
      fixture.assertAdminTokenRequest(fixture.takeRequest());
      final RecordedRequest searchRequest = fixture.takeRequest();
      assertThat(searchRequest.getMethod()).isEqualTo("GET");
      assertThat(searchRequest.getPath())
          .startsWith("/admin/realms/methodologist/users?")
          .contains("username=missing");
    }
  }

  @Test
  void assignRealmRole_addsResolvedRoleToUserRealmRoles() throws Exception {
    try (KeycloakServerFixture fixture = new KeycloakServerFixture()) {
      fixture.enqueueTokenResponse();
      fixture.enqueueJson(
          """
          {
            "id": "role-1",
            "name": "USER",
            "description": "User role",
            "composite": false,
            "clientRole": false,
            "containerId": "methodologist"
          }
          """);
      fixture.enqueue(new MockResponse().setResponseCode(204));

      fixture.gateway.assignRealmRole("user-1", "USER");

      fixture.assertAdminTokenRequest(fixture.takeRequest());
      final RecordedRequest roleRequest = fixture.takeRequest();
      assertThat(roleRequest.getMethod()).isEqualTo("GET");
      assertThat(roleRequest.getPath()).isEqualTo("/admin/realms/methodologist/roles/USER");
      final RecordedRequest assignmentRequest = fixture.takeRequest();
      assertThat(assignmentRequest.getMethod()).isEqualTo("POST");
      assertThat(assignmentRequest.getPath())
          .isEqualTo("/admin/realms/methodologist/users/user-1/role-mappings/realm");
      assertThat(assignmentRequest.getBody().readUtf8())
          .contains("\"id\":\"role-1\"")
          .contains("\"name\":\"USER\"");
    }
  }

  @Test
  void removeUser_removesKeycloakUser() throws Exception {
    try (KeycloakServerFixture fixture = new KeycloakServerFixture()) {
      fixture.enqueueTokenResponse();
      fixture.enqueue(new MockResponse().setResponseCode(204));

      fixture.gateway.removeUser("user-1");

      fixture.assertAdminTokenRequest(fixture.takeRequest());
      final RecordedRequest removeRequest = fixture.takeRequest();
      assertThat(removeRequest.getMethod()).isEqualTo("DELETE");
      assertThat(removeRequest.getPath()).isEqualTo("/admin/realms/methodologist/users/user-1");
    }
  }

  @Test
  void verifyPassword_requestsTokenFromConfiguredKeycloakServer() throws Exception {
    try (KeycloakServerFixture fixture = new KeycloakServerFixture()) {
      fixture.enqueueTokenResponse();

      fixture.gateway.verifyPassword("alice", "password");

      final RecordedRequest request = fixture.takeRequest();
      assertThat(request.getMethod()).isEqualTo("POST");
      assertThat(request.getPath())
          .isEqualTo("/realms/methodologist/protocol/openid-connect/token");
      assertThat(request.getBody().readUtf8())
          .contains("grant_type=password")
          .contains("client_id=methodologist-client")
          .contains("username=alice")
          .contains("password=password");
    }
  }

  @Test
  void resetPassword_resetsPasswordForKeycloakUser() throws Exception {
    try (KeycloakServerFixture fixture = new KeycloakServerFixture()) {
      fixture.enqueueTokenResponse();
      fixture.enqueue(new MockResponse().setResponseCode(204));
      final CredentialRepresentation credentialRepresentation = new CredentialRepresentation();
      credentialRepresentation.setType(CredentialRepresentation.PASSWORD);
      credentialRepresentation.setValue("new-password");
      credentialRepresentation.setTemporary(false);

      fixture.gateway.resetPassword("user-1", credentialRepresentation);

      fixture.assertAdminTokenRequest(fixture.takeRequest());
      final RecordedRequest resetRequest = fixture.takeRequest();
      assertThat(resetRequest.getMethod()).isEqualTo("PUT");
      assertThat(resetRequest.getPath())
          .isEqualTo("/admin/realms/methodologist/users/user-1/reset-password");
      assertThat(resetRequest.getBody().readUtf8())
          .contains("\"type\":\"password\"")
          .contains("\"value\":\"new-password\"")
          .contains("\"temporary\":false");
    }
  }

  @Test
  void executeActionsEmail_sendsActionsEmailToKeycloakUser() throws Exception {
    try (KeycloakServerFixture fixture = new KeycloakServerFixture()) {
      fixture.enqueueTokenResponse();
      fixture.enqueue(new MockResponse().setResponseCode(204));

      fixture.gateway.executeActionsEmail("user-1", List.of("UPDATE_PASSWORD"));

      fixture.assertAdminTokenRequest(fixture.takeRequest());
      final RecordedRequest actionsRequest = fixture.takeRequest();
      assertThat(actionsRequest.getMethod()).isEqualTo("PUT");
      assertThat(actionsRequest.getPath())
          .isEqualTo("/admin/realms/methodologist/users/user-1/execute-actions-email");
      assertThat(actionsRequest.getBody().readUtf8()).isEqualTo("[\"UPDATE_PASSWORD\"]");
    }
  }

  private static final class KeycloakServerFixture implements AutoCloseable {

    private final MockWebServer server = new MockWebServer();
    private final KeycloakGatewayImpl gateway;

    private KeycloakServerFixture() throws IOException {
      server.start();
      gateway =
          new KeycloakGatewayImpl(
              server.url("/").toString(),
              REALM,
              ADMIN_USERNAME,
              ADMIN_PASSWORD,
              CLIENT_SECRET,
              CLIENT_ID);
    }

    private void enqueue(MockResponse response) {
      server.enqueue(response);
    }

    private void enqueueJson(String body) {
      enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody(body));
    }

    private void enqueueTokenResponse() {
      enqueueJson(
          """
          {
            "access_token": "access-token",
            "expires_in": 300,
            "refresh_expires_in": 1800,
            "refresh_token": "refresh-token",
            "token_type": "Bearer",
            "not-before-policy": 0,
            "session_state": "session",
            "scope": "profile"
          }
          """);
    }

    private RecordedRequest takeRequest() throws InterruptedException {
      final RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
      assertThat(request).isNotNull();
      return request;
    }

    private void assertAdminTokenRequest(RecordedRequest request) {
      assertThat(request.getMethod()).isEqualTo("POST");
      assertThat(request.getPath()).isEqualTo("/realms/master/protocol/openid-connect/token");
      assertThat(request.getBody().readUtf8())
          .contains("grant_type=password")
          .contains("username=admin")
          .contains("password=admin-password");
    }

    @Override
    public void close() throws IOException {
      gateway.close();
      server.shutdown();
    }
  }
}
