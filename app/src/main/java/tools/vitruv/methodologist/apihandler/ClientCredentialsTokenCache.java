package tools.vitruv.methodologist.apihandler;

import java.time.Clock;
import java.time.Instant;
import org.springframework.util.StringUtils;
import tools.vitruv.methodologist.apihandler.dto.response.KeycloakWebToken;
import tools.vitruv.methodologist.exception.UncheckedRuntimeException;

/**
 * Caches the access token of a confidential client, fetched from Keycloak with the client
 * credentials grant, and reuses it until shortly before it expires.
 *
 * <p>Neither the token nor the client secret is ever logged or put into an exception message.
 */
final class ClientCredentialsTokenCache {

  /** A token is renewed this long before it expires, so it does not expire on the way. */
  static final long EXPIRY_MARGIN_SECONDS = 30;

  private final KeycloakApiHandler keycloakApiHandler;
  private final String clientId;
  private final String clientSecret;
  private final Clock clock;

  private String accessToken;
  private Instant renewAt = Instant.MIN;

  ClientCredentialsTokenCache(
      KeycloakApiHandler keycloakApiHandler, String clientId, String clientSecret, Clock clock) {
    this.keycloakApiHandler = keycloakApiHandler;
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.clock = clock;
  }

  /**
   * Returns the cached access token, or fetches a new one if there is none or it is about to
   * expire.
   *
   * @return a valid access token
   * @throws UncheckedRuntimeException if Keycloak answers without an access token
   * @throws RuntimeException if the token request fails, see {@link
   *     KeycloakApiHandler#getClientCredentialsTokenOrThrow(String, String)}
   */
  synchronized String getAccessToken() {
    Instant now = clock.instant();
    if (accessToken == null || !now.isBefore(renewAt)) {
      KeycloakWebToken token =
          keycloakApiHandler.getClientCredentialsTokenOrThrow(clientId, clientSecret);
      if (token == null || !StringUtils.hasText(token.getAccessToken())) {
        throw new UncheckedRuntimeException("Keycloak answered without an access token");
      }
      long lifetimeSeconds = token.getExpiresIn() == null ? 0 : token.getExpiresIn();
      accessToken = token.getAccessToken();
      renewAt = now.plusSeconds(lifetimeSeconds - EXPIRY_MARGIN_SECONDS);
    }
    return accessToken;
  }

  /** Discards the cached token, e.g. after it was rejected, so the next call fetches a new one. */
  synchronized void invalidate() {
    accessToken = null;
  }
}
