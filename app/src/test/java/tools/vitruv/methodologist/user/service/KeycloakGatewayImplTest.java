package tools.vitruv.methodologist.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.Dispatcher;
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
  private static final String TOKEN_RESPONSE_JSON =
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
      """;

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
          .contains("username=alice")
          .contains("exact=true");
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
          .contains("username=missing")
          .contains("exact=true");
    }
  }

  @Test
  void findUser_returnsUserWithExactUsername_whenSimilarUsernamesExist() throws Exception {
    try (KeycloakServerFixture fixture = new KeycloakServerFixture()) {
      fixture.serveUsers(
          new StoredUser("user-1", "alisa", "alisa@example.com"),
          new StoredUser("user-2", "lisa", "lisa@example.com"));

      final Optional<UserRepresentation> result = fixture.gateway.findUser("lisa");

      assertThat(result).isPresent();
      assertThat(result.orElseThrow().getId()).isEqualTo("user-2");
    }
  }

  @Test
  void findUserByEmail_returnsUserWithExactEmail_whenSimilarEmailsExist() throws Exception {
    try (KeycloakServerFixture fixture = new KeycloakServerFixture()) {
      fixture.serveUsers(
          new StoredUser("user-1", "alisa", "alisa@example.com"),
          new StoredUser("user-2", "lisa", "lisa@example.com"));

      final Optional<UserRepresentation> result =
          fixture.gateway.findUserByEmail("lisa@example.com");

      assertThat(result).isPresent();
      assertThat(result.orElseThrow().getId()).isEqualTo("user-2");
    }
  }

  @Test
  void findUserByEmail_returnsEmpty_whenNoUserMatches() throws Exception {
    try (KeycloakServerFixture fixture = new KeycloakServerFixture()) {
      fixture.enqueueTokenResponse();
      fixture.enqueueJson("[]");

      final Optional<UserRepresentation> result =
          fixture.gateway.findUserByEmail("missing@example.com");

      assertThat(result).isEmpty();
      fixture.assertAdminTokenRequest(fixture.takeRequest());
      final RecordedRequest searchRequest = fixture.takeRequest();
      assertThat(searchRequest.getMethod()).isEqualTo("GET");
      assertThat(searchRequest.getPath())
          .startsWith("/admin/realms/methodologist/users?")
          .contains("email=missing")
          .contains("exact=true");
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
  void updateUserProfile_updatesKeycloakUser() throws Exception {
    try (KeycloakServerFixture fixture = new KeycloakServerFixture()) {
      fixture.enqueueTokenResponse();
      fixture.enqueueJson(
          """
          {
            "id": "user-1",
            "username": "alice",
            "email": "alice@example.com",
            "firstName": "Alice",
            "lastName": "Doe",
            "enabled": true
          }
          """);
      fixture.enqueue(new MockResponse().setResponseCode(204));

      fixture.gateway.updateUserProfile("user-1", "Alicia", "Smith");

      fixture.assertAdminTokenRequest(fixture.takeRequest());
      final RecordedRequest getRequest = fixture.takeRequest();
      assertThat(getRequest.getMethod()).isEqualTo("GET");
      assertThat(getRequest.getPath()).isEqualTo("/admin/realms/methodologist/users/user-1");
      final RecordedRequest updateRequest = fixture.takeRequest();
      assertThat(updateRequest.getMethod()).isEqualTo("PUT");
      assertThat(updateRequest.getPath()).isEqualTo("/admin/realms/methodologist/users/user-1");
      assertThat(updateRequest.getBody().readUtf8())
          .contains("\"username\":\"alice\"")
          .contains("\"email\":\"alice@example.com\"")
          .contains("\"firstName\":\"Alicia\"")
          .contains("\"lastName\":\"Smith\"");
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
      enqueue(jsonResponse(body));
    }

    private void enqueueTokenResponse() {
      enqueueJson(TOKEN_RESPONSE_JSON);
    }

    private void serveUsers(StoredUser... users) {
      server.setDispatcher(new UserSearchDispatcher(List.of(users)));
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

  private static MockResponse jsonResponse(String body) {
    return new MockResponse().setHeader("Content-Type", "application/json").setBody(body);
  }

  private record StoredUser(String id, String username, String email) {

    private String toJson() {
      return "{\"id\": \"%s\", \"username\": \"%s\", \"email\": \"%s\"}"
          .formatted(id, username, email);
    }
  }

  /**
   * Answers admin token and user search requests. Users are matched like Keycloak does: by
   * substring unless {@code exact=true}, sorted by username.
   */
  private static final class UserSearchDispatcher extends Dispatcher {

    private final List<StoredUser> users;

    private UserSearchDispatcher(List<StoredUser> users) {
      this.users = users;
    }

    @Override
    public MockResponse dispatch(RecordedRequest request) {
      final HttpUrl url = Objects.requireNonNull(request.getRequestUrl());
      if (url.encodedPath().endsWith("/protocol/openid-connect/token")) {
        return jsonResponse(TOKEN_RESPONSE_JSON);
      }
      final boolean exact = Boolean.parseBoolean(url.queryParameter("exact"));
      final String body =
          users.stream()
              .filter(user -> matches(user.username(), url.queryParameter("username"), exact))
              .filter(user -> matches(user.email(), url.queryParameter("email"), exact))
              .sorted(Comparator.comparing(StoredUser::username))
              .map(StoredUser::toJson)
              .collect(Collectors.joining(",", "[", "]"));
      return jsonResponse(body);
    }

    private static boolean matches(String value, String searchValue, boolean exact) {
      if (searchValue == null) {
        return true;
      }
      final String normalizedSearchValue = searchValue.toLowerCase(Locale.ROOT);
      return exact ? value.equals(normalizedSearchValue) : value.contains(normalizedSearchValue);
    }
  }
}
