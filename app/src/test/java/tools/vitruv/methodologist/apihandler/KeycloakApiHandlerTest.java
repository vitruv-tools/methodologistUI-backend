package tools.vitruv.methodologist.apihandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.netty.handler.timeout.ReadTimeoutException;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
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

  @Test
  void getClientCredentialsToken_postsTheClientCredentialsGrant() throws InterruptedException {
    keycloak.enqueue(json(200, "{\"access_token\":\"abc\",\"expires_in\":300}"));

    KeycloakWebToken token =
        handler.getClientCredentialsTokenOrThrow("methodologist-backend", "backend-secret");

    assertThat(token.getAccessToken()).isEqualTo("abc");
    assertThat(token.getExpiresIn()).isEqualTo(300);
    RecordedRequest request = keycloak.takeRequest();
    assertThat(request.getPath()).isEqualTo(KeycloakApiHandler.POST_TOKEN_URL);
    assertThat(request.getBody().readUtf8())
        .contains("grant_type=client_credentials")
        .contains("client_id=methodologist-backend")
        .contains("client_secret=backend-secret");
  }

  @Test
  void getClientCredentialsToken_failsWithoutTheSecret_whenKeycloakRejectsTheClient() {
    keycloak.enqueue(
        json(401, "{\"error\":\"invalid_client\",\"error_description\":\"Invalid secret\"}"));

    assertThatThrownBy(
            () ->
                handler.getClientCredentialsTokenOrThrow("methodologist-backend", "backend-secret"))
        .isInstanceOf(UncheckedRuntimeException.class)
        .hasMessageContaining("invalid_client")
        .hasMessageNotContaining("backend-secret");
  }

  @Test
  @Timeout(value = 30, unit = TimeUnit.SECONDS) // guards against a hang; the timeout itself is 5 s
  void getClientCredentialsToken_failsWithTheResponseTimeout_whenKeycloakDoesNotAnswer() {
    keycloak.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));

    assertThatThrownBy(
            () ->
                handler.getClientCredentialsTokenOrThrow("methodologist-backend", "backend-secret"))
        .isInstanceOf(WebClientRequestException.class)
        .hasRootCauseInstanceOf(ReadTimeoutException.class);
  }

  private static MockResponse json(int status, String body) {
    return new MockResponse()
        .setResponseCode(status)
        .setHeader("Content-Type", "application/json")
        .setBody(body);
  }
}
