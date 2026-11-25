package tools.vitruv.methodologist.apihandler;

import io.netty.channel.ChannelOption;
import java.time.Clock;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;

/**
 * Component responsible for calling the internal API of the collaboration server, which keeps the
 * shared document of every open project.
 *
 * <p>The backend authenticates as its own service account (Keycloak client {@code
 * methodologist-backend}, realm role {@code methodologist-backend}) with the client credentials
 * grant; the token is cached until shortly before it expires. The integration is disabled if {@code
 * collaboration.url} is not set, or if the client credentials are missing.
 */
@Slf4j
@Component
public class CollaborationServiceApiHandler {
  public static final String RELOAD_URL = "/internal/projects/{id}/reload";
  public static final int CONNECT_TIMEOUT_IN_MILLIS = 2_000;
  public static final int RESPONSE_TIMEOUT_IN_SECONDS = 3;

  /** The delay between two attempts doubles with every failure, up to this. */
  public static final Duration MAX_RETRY_DELAY = Duration.ofSeconds(10);

  private final WebClient webClient;
  private final ClientCredentialsTokenCache tokenCache;
  private final boolean enabled;
  private final Retry retry;

  /**
   * Constructs the handler. Configures a WebClient with short connect and response timeouts, so an
   * unreachable collaboration server does not hold up the notifications for long.
   *
   * @param baseUrl the base URL of the collaboration server; blank disables the integration
   * @param clientId the Keycloak client ID of the backend's service account
   * @param clientSecret the Keycloak client secret of the backend's service account
   * @param maxRetries how often a reload request is sent again after a transient failure; {@code 0}
   *     sends it only once
   * @param firstRetryDelay the delay before the first retry; it doubles with every further failure,
   *     up to {@link #MAX_RETRY_DELAY}
   * @param keycloakApiHandler fetches the service account's access token
   */
  public CollaborationServiceApiHandler(
      @Value("${collaboration.url:}") String baseUrl,
      @Value("${keycloak.backend.client-id:}") String clientId,
      @Value("${keycloak.backend.client-secret:}") String clientSecret,
      @Value("${collaboration.reload.max-retries:5}") int maxRetries,
      @Value("${collaboration.reload.first-retry-delay:1s}") Duration firstRetryDelay,
      KeycloakApiHandler keycloakApiHandler) {
    this.enabled = isConfigured(baseUrl, clientId, clientSecret);
    // Reactor rejects a maximum below the first delay.
    Duration maxRetryDelay =
        firstRetryDelay.compareTo(MAX_RETRY_DELAY) > 0 ? firstRetryDelay : MAX_RETRY_DELAY;
    this.retry =
        Retry.backoff(maxRetries, firstRetryDelay)
            .maxBackoff(maxRetryDelay)
            .filter(CollaborationServiceApiHandler::isTransient)
            // fail with the last error, not with Reactor's RetryExhaustedException
            .onRetryExhaustedThrow((spec, signal) -> signal.failure());
    this.tokenCache =
        new ClientCredentialsTokenCache(
            keycloakApiHandler, clientId, clientSecret, Clock.systemUTC());

    HttpClient httpClient =
        HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_IN_MILLIS)
            .responseTimeout(Duration.ofSeconds(RESPONSE_TIMEOUT_IN_SECONDS));
    this.webClient =
        WebClient.builder()
            .baseUrl(baseUrl)
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .build();
  }

  /**
   * Tells whether notifications are sent to the collaboration server.
   *
   * @return {@code true} if the collaboration server URL and the client credentials are configured
   */
  public boolean isEnabled() {
    return enabled;
  }

  /**
   * Asks the collaboration server to reload the project of the given VSUM from the backend, so its
   * next writeback does not undo a change made through a user endpoint. Does nothing if the
   * integration is disabled.
   *
   * <p>A request the collaboration server could not take (see {@link #isTransient(Throwable)}) is
   * sent again after a growing delay, up to {@code collaboration.reload.max-retries} times. If the
   * collaboration server rejects the token, a new one is fetched and the request is sent once more.
   * Blocks until the collaboration server accepted the request or all attempts failed.
   *
   * @param vsumId the id of the changed VSUM
   * @throws org.springframework.web.reactive.function.client.WebClientException if the
   *     collaboration server cannot be reached or answers with an error, also after the retries
   * @throws RuntimeException if no access token can be fetched from Keycloak
   */
  public void requestReload(Long vsumId) {
    if (!enabled) {
      return;
    }
    try {
      postReload(vsumId);
    } catch (WebClientResponseException.Unauthorized e) {
      // The token was revoked or the collaboration server's clock is ahead: try once more with a
      // new token instead of losing the notification.
      postReload(vsumId);
    }
  }

  /**
   * Sends the reload request with the cached token, retrying transient failures. Discards the token
   * if the collaboration server rejects it, so the next call fetches a new one.
   */
  private void postReload(Long vsumId) {
    String accessToken = tokenCache.getAccessToken();
    try {
      webClient
          .post()
          .uri(RELOAD_URL, vsumId)
          .headers(headers -> headers.setBearerAuth(accessToken))
          .retrieve()
          .toBodilessEntity()
          .retryWhen(retry)
          .block();
    } catch (WebClientResponseException.Unauthorized e) {
      tokenCache.invalidate();
      throw e;
    }
  }

  /**
   * Tells whether a failed reload request may succeed when it is sent again: the collaboration
   * server could not be reached or did not answer in time, failed, or asked to wait. Other answers,
   * e.g. {@code 400} for an invalid project id, would come again.
   *
   * @param error the failure of a reload request
   * @return {@code true} if the request is retried
   */
  static boolean isTransient(Throwable error) {
    if (error instanceof WebClientResponseException response) {
      HttpStatusCode status = response.getStatusCode();
      return status.is5xxServerError() || status.value() == HttpStatus.TOO_MANY_REQUESTS.value();
    }
    return error instanceof WebClientRequestException;
  }

  private static boolean isConfigured(String baseUrl, String clientId, String clientSecret) {
    if (!StringUtils.hasText(baseUrl)) {
      log.info("Collaboration server notifications are disabled: collaboration.url is not set");
      return false;
    }
    if (!StringUtils.hasText(clientId) || !StringUtils.hasText(clientSecret)) {
      log.warn(
          "Collaboration server notifications are disabled: keycloak.backend.client-id or "
              + "keycloak.backend.client-secret is not set");
      return false;
    }
    return true;
  }
}
