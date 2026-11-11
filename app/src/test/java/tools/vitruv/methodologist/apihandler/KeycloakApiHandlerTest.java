package tools.vitruv.methodologist.apihandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import tools.vitruv.methodologist.apihandler.dto.response.KeycloakWebToken;
import tools.vitruv.methodologist.exception.UnauthorizedException;
import tools.vitruv.methodologist.exception.UncheckedRuntimeException;

/**
 * Checks that the token requests tell a grant Keycloak rejects apart from a failing Keycloak, and
 * that the exceptions do not carry Keycloak's response body.
 */
class KeycloakApiHandlerTest {
  private MockWebServer keycloak;
  private KeycloakApiHandler handler;

  @BeforeEach
  void setUp() throws IOException {
    keycloak = new MockWebServer();
    keycloak.start();
    handler = new KeycloakApiHandler(keycloak.url("/").toString());
    ReflectionTestUtils.setField(handler, "clientId", "methodologist");
  }

  @AfterEach
  void tearDown() throws IOException {
    keycloak.shutdown();
  }

  @Test
  void returnsTheToken_whenKeycloakAcceptsTheCredentials() {
    keycloak.enqueue(json(200, "{\"access_token\":\"abc\",\"refresh_token\":\"def\"}"));

    KeycloakWebToken token = handler.getAccessTokenOrThrow("alice", "secret");

    assertThat(token).isNotNull();
  }

  @ParameterizedTest
  @CsvSource({"401", "400"})
  void rejectsTheCredentials_whenKeycloakAnswersInvalidGrant(int status) {
    keycloak.enqueue(
        json(status, "{\"error\":\"invalid_grant\",\"error_description\":\"Invalid user\"}"));

    assertThatThrownBy(() -> handler.getAccessTokenOrThrow("alice", "secret"))
        .isInstanceOf(UnauthorizedException.class);
  }

  @Test
  void rejectsTheCredentials_whenKeycloakAnswers401WithoutReadableError() {
    keycloak.enqueue(new MockResponse().setResponseCode(401).setBody("<html>no</html>"));

    assertThatThrownBy(() -> handler.getAccessTokenOrThrow("alice", "secret"))
        .isInstanceOf(UnauthorizedException.class);
  }

  @ParameterizedTest
  @CsvSource({
    "401, '{\"error\":\"invalid_client\",\"error_description\":\"alice@example.com\"}'",
    "503, '{\"error\":\"temporarily_unavailable\"}'",
    "500, '<html>alice@example.com</html>'"
  })
  void failsWithoutTheResponseBody_whenKeycloakAnswersAnotherError(int status, String body) {
    keycloak.enqueue(json(status, body));

    assertThatThrownBy(() -> handler.getAccessTokenOrThrow("alice", "secret"))
        .isInstanceOf(UncheckedRuntimeException.class)
        .hasMessageContaining(String.valueOf(status))
        .hasMessageNotContaining("alice@example.com");
  }

  @Test
  void failsWithTheConnectionError_whenKeycloakCannotBeReached() throws IOException {
    keycloak.shutdown();

    assertThatThrownBy(() -> handler.getAccessTokenOrThrow("alice", "secret"))
        .isInstanceOf(WebClientRequestException.class);
  }

  @Test
  void rejectsTheRefreshToken_whenItExpired() {
    keycloak.enqueue(
        json(400, "{\"error\":\"invalid_grant\",\"error_description\":\"Token is not active\"}"));

    assertThatThrownBy(() -> handler.getAccessTokenByRefreshToken("refresh"))
        .isInstanceOf(UnauthorizedException.class);
  }

  @Test
  void failsTheRefresh_whenKeycloakFails() {
    keycloak.enqueue(json(502, "{\"error\":\"bad_gateway\"}"));

    assertThatThrownBy(() -> handler.getAccessTokenByRefreshToken("refresh"))
        .isInstanceOf(UncheckedRuntimeException.class);
  }

  private static MockResponse json(int status, String body) {
    return new MockResponse()
        .setResponseCode(status)
        .setHeader("Content-Type", "application/json")
        .setBody(body);
  }
}
