package tools.vitruv.methodologist.apihandler;

import io.netty.channel.ChannelOption;
import java.time.Clock;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.HttpClient;

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

  private final WebClient webClient;
  private final ClientCredentialsTokenCache tokenCache;
  private final boolean enabled;

  /**
   * Constructs the handler. Configures a WebClient with short connect and response timeouts, so an
   * unreachable collaboration server does not hold up the notifications for long.
   *
   * @param baseUrl the base URL of the collaboration server; blank disables the integration
   * @param clientId the Keycloak client ID of the backend's service account
   * @param clientSecret the Keycloak client secret of the backend's service account
   * @param keycloakApiHandler fetches the service account's access token
   */
  public CollaborationServiceApiHandler(
      @Value("${collaboration.url:}") String baseUrl,
      @Value("${keycloak.backend.client-id:}") String clientId,
      @Value("${keycloak.backend.client-secret:}") String clientSecret,
      KeycloakApiHandler keycloakApiHandler) {
    this.enabled = isConfigured(baseUrl, clientId, clientSecret);
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
   * integration is disabled. Blocks until the collaboration server answers or the timeout expires.
   *
   * @param vsumId the id of the changed VSUM
   * @throws org.springframework.web.reactive.function.client.WebClientException if the
   *     collaboration server cannot be reached or answers with an error
   * @throws RuntimeException if no access token can be fetched from Keycloak
   */
  public void requestReload(Long vsumId) {
    if (!enabled) {
      return;
    }
    String accessToken = tokenCache.getAccessToken();
    try {
      webClient
          .post()
          .uri(RELOAD_URL, vsumId)
          .headers(headers -> headers.setBearerAuth(accessToken))
          .retrieve()
          .toBodilessEntity()
          .block();
    } catch (WebClientResponseException.Unauthorized e) {
      // The token was revoked or the collaboration server's clock is ahead; fetch a new one next
      // time.
      tokenCache.invalidate();
      throw e;
    }
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
