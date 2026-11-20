package tools.vitruv.methodologist.apihandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import tools.vitruv.methodologist.apihandler.dto.response.KeycloakWebToken;
import tools.vitruv.methodologist.exception.UncheckedRuntimeException;

@ExtendWith(MockitoExtension.class)
class CollaborationServiceApiHandlerTest {

  private static final String CLIENT_ID = "methodologist-backend";
  private static final String CLIENT_SECRET = "backend-secret";

  @Mock private KeycloakApiHandler keycloakApiHandler;

  private MockWebServer collaborationServer;
  private CollaborationServiceApiHandler handler;

  @BeforeEach
  void setUp() throws IOException {
    collaborationServer = new MockWebServer();
    collaborationServer.start();
    handler = handlerFor(collaborationServer.url("/").toString(), CLIENT_ID, CLIENT_SECRET);
  }

  @AfterEach
  void tearDown() throws IOException {
    collaborationServer.shutdown();
  }

  @Test
  void requestReload_postsToTheReloadEndpoint_withTheServiceAccountToken()
      throws InterruptedException {
    givenTokens("service-token");
    collaborationServer.enqueue(new MockResponse().setResponseCode(202));

    handler.requestReload(42L);

    RecordedRequest request = collaborationServer.takeRequest(1, TimeUnit.SECONDS);
    assertThat(request).isNotNull();
    assertThat(request.getMethod()).isEqualTo("POST");
    assertThat(request.getPath()).isEqualTo("/internal/projects/42/reload");
    assertThat(request.getHeader(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer service-token");
  }

  @Test
  void requestReload_reusesTheCachedToken() throws InterruptedException {
    givenTokens("service-token");
    collaborationServer.enqueue(new MockResponse().setResponseCode(202));
    collaborationServer.enqueue(new MockResponse().setResponseCode(202));

    handler.requestReload(1L);
    handler.requestReload(2L);

    verify(keycloakApiHandler, times(1)).getClientCredentialsTokenOrThrow(CLIENT_ID, CLIENT_SECRET);
    collaborationServer.takeRequest(1, TimeUnit.SECONDS);
    assertThat(collaborationServer.takeRequest(1, TimeUnit.SECONDS).getHeader("Authorization"))
        .isEqualTo("Bearer service-token");
  }

  @Test
  void requestReload_fetchesANewToken_afterTheCollaborationServerRejectedTheCachedOne()
      throws InterruptedException {
    givenTokens("revoked-token", "new-token");
    collaborationServer.enqueue(new MockResponse().setResponseCode(401));
    collaborationServer.enqueue(new MockResponse().setResponseCode(202));

    assertThatThrownBy(() -> handler.requestReload(1L))
        .isInstanceOf(WebClientResponseException.Unauthorized.class);
    handler.requestReload(1L);

    collaborationServer.takeRequest(1, TimeUnit.SECONDS);
    assertThat(collaborationServer.takeRequest(1, TimeUnit.SECONDS).getHeader("Authorization"))
        .isEqualTo("Bearer new-token");
  }

  @Test
  void requestReload_keepsTheToken_whenTheCollaborationServerFailsOtherwise() {
    givenTokens("service-token");
    collaborationServer.enqueue(new MockResponse().setResponseCode(503));
    collaborationServer.enqueue(new MockResponse().setResponseCode(202));

    assertThatThrownBy(() -> handler.requestReload(1L))
        .isInstanceOf(WebClientResponseException.class);
    handler.requestReload(1L);

    verify(keycloakApiHandler, times(1)).getClientCredentialsTokenOrThrow(CLIENT_ID, CLIENT_SECRET);
  }

  @Test
  void requestReload_fails_whenTheCollaborationServerCannotBeReached() throws IOException {
    givenTokens("service-token");
    collaborationServer.shutdown();

    assertThatThrownBy(() -> handler.requestReload(1L))
        .isInstanceOf(WebClientRequestException.class)
        .hasMessageNotContaining("service-token");
  }

  @Test
  @Timeout(value = 30, unit = TimeUnit.SECONDS) // guards against a hang; the timeout itself is 3 s
  void requestReload_failsWithTheResponseTimeout_whenTheCollaborationServerDoesNotAnswer() {
    givenTokens("service-token");
    collaborationServer.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));

    assertThatThrownBy(() -> handler.requestReload(1L))
        .isInstanceOf(WebClientRequestException.class)
        .hasRootCauseInstanceOf(ReadTimeoutException.class);
  }

  @Test
  void requestReload_sendsNothing_whenNoTokenCanBeFetched() {
    when(keycloakApiHandler.getClientCredentialsTokenOrThrow(CLIENT_ID, CLIENT_SECRET))
        .thenThrow(new UncheckedRuntimeException("Keycloak down"));

    assertThatThrownBy(() -> handler.requestReload(1L))
        .isInstanceOf(UncheckedRuntimeException.class);
    assertThat(collaborationServer.getRequestCount()).isZero();
  }

  @Test
  void isDisabledAndSendsNothing_whenTheCollaborationUrlIsMissing() {
    CollaborationServiceApiHandler unconfigured = handlerFor("", CLIENT_ID, CLIENT_SECRET);

    unconfigured.requestReload(1L);

    assertThat(unconfigured.isEnabled()).isFalse();
    verifyNoInteractions(keycloakApiHandler);
  }

  @Test
  void isDisabledAndSendsNothing_whenTheClientCredentialsAreMissing() {
    String url = collaborationServer.url("/").toString();
    CollaborationServiceApiHandler withoutSecret = handlerFor(url, CLIENT_ID, "");
    CollaborationServiceApiHandler withoutClientId = handlerFor(url, " ", CLIENT_SECRET);

    withoutSecret.requestReload(1L);
    withoutClientId.requestReload(1L);

    assertThat(withoutSecret.isEnabled()).isFalse();
    assertThat(withoutClientId.isEnabled()).isFalse();
    verifyNoInteractions(keycloakApiHandler);
    assertThat(collaborationServer.getRequestCount()).isZero();
  }

  @Test
  void isEnabled_whenUrlAndClientCredentialsAreConfigured() {
    assertThat(handler.isEnabled()).isTrue();
  }

  private CollaborationServiceApiHandler handlerFor(
      String url, String clientId, String clientSecret) {
    return new CollaborationServiceApiHandler(url, clientId, clientSecret, keycloakApiHandler);
  }

  private void givenTokens(String first, String... more) {
    KeycloakWebToken[] moreTokens = new KeycloakWebToken[more.length];
    for (int i = 0; i < more.length; i++) {
      moreTokens[i] = token(more[i]);
    }
    when(keycloakApiHandler.getClientCredentialsTokenOrThrow(CLIENT_ID, CLIENT_SECRET))
        .thenReturn(token(first), moreTokens);
  }

  private static KeycloakWebToken token(String accessToken) {
    return KeycloakWebToken.builder().accessToken(accessToken).expiresIn(300).build();
  }
}
