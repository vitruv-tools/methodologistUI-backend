package tools.vitruv.methodologist.apihandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.vitruv.methodologist.apihandler.dto.response.KeycloakWebToken;
import tools.vitruv.methodologist.exception.UncheckedRuntimeException;

@ExtendWith(MockitoExtension.class)
class ClientCredentialsTokenCacheTest {

  private static final String CLIENT_ID = "methodologist-backend";
  private static final String CLIENT_SECRET = "backend-secret";
  private static final Instant START = Instant.parse("2026-01-01T10:00:00Z");
  private static final int LIFETIME_SECONDS = 300;

  @Mock private KeycloakApiHandler keycloakApiHandler;
  @Mock private Clock clock;

  private ClientCredentialsTokenCache cache;

  @BeforeEach
  void setUp() {
    cache = new ClientCredentialsTokenCache(keycloakApiHandler, CLIENT_ID, CLIENT_SECRET, clock);
  }

  @Test
  void fetchesTheTokenOnce_andReusesItWhileItIsValid() {
    givenTokens(token("first", LIFETIME_SECONDS));
    when(clock.instant()).thenReturn(START, START.plusSeconds(100), lastReuseInstant());

    assertThat(cache.getAccessToken()).isEqualTo("first");
    assertThat(cache.getAccessToken()).isEqualTo("first");
    assertThat(cache.getAccessToken()).isEqualTo("first");

    verify(keycloakApiHandler, times(1)).getClientCredentialsTokenOrThrow(CLIENT_ID, CLIENT_SECRET);
  }

  @Test
  void fetchesANewToken_shortlyBeforeTheCachedOneExpires() {
    givenTokens(token("first", LIFETIME_SECONDS), token("second", LIFETIME_SECONDS));
    when(clock.instant()).thenReturn(START, lastReuseInstant(), lastReuseInstant().plusSeconds(1));

    assertThat(cache.getAccessToken()).isEqualTo("first");
    assertThat(cache.getAccessToken()).isEqualTo("first");
    assertThat(cache.getAccessToken()).isEqualTo("second");

    verify(keycloakApiHandler, times(2)).getClientCredentialsTokenOrThrow(CLIENT_ID, CLIENT_SECRET);
  }

  @Test
  void fetchesANewToken_afterTheCachedOneWasInvalidated() {
    givenTokens(token("first", LIFETIME_SECONDS), token("second", LIFETIME_SECONDS));
    when(clock.instant()).thenReturn(START);

    assertThat(cache.getAccessToken()).isEqualTo("first");
    cache.invalidate();

    assertThat(cache.getAccessToken()).isEqualTo("second");
  }

  @Test
  void fetchesANewTokenEveryTime_whenKeycloakSendsNoLifetime() {
    givenTokens(token("first", null), token("second", null));
    when(clock.instant()).thenReturn(START);

    assertThat(cache.getAccessToken()).isEqualTo("first");
    assertThat(cache.getAccessToken()).isEqualTo("second");
  }

  @Test
  void failsAndCachesNothing_whenKeycloakAnswersWithoutAnAccessToken() {
    givenTokens(token(" ", LIFETIME_SECONDS), token("valid", LIFETIME_SECONDS));
    when(clock.instant()).thenReturn(START);

    assertThatThrownBy(() -> cache.getAccessToken())
        .isInstanceOf(UncheckedRuntimeException.class)
        .hasMessageNotContaining(CLIENT_SECRET);
    assertThat(cache.getAccessToken()).isEqualTo("valid");
  }

  @Test
  void propagatesTheFailure_whenTheTokenRequestFails() {
    when(clock.instant()).thenReturn(START);
    when(keycloakApiHandler.getClientCredentialsTokenOrThrow(CLIENT_ID, CLIENT_SECRET))
        .thenThrow(new UncheckedRuntimeException("Keycloak down"));

    assertThatThrownBy(() -> cache.getAccessToken())
        .isInstanceOf(UncheckedRuntimeException.class)
        .hasMessage("Keycloak down");
  }

  /** The last instant at which the first token is still reused, given the expiry margin. */
  private static Instant lastReuseInstant() {
    return START.plusSeconds(
        LIFETIME_SECONDS - ClientCredentialsTokenCache.EXPIRY_MARGIN_SECONDS - 1);
  }

  private void givenTokens(KeycloakWebToken first, KeycloakWebToken... more) {
    when(keycloakApiHandler.getClientCredentialsTokenOrThrow(CLIENT_ID, CLIENT_SECRET))
        .thenReturn(first, more);
  }

  private static KeycloakWebToken token(String accessToken, Integer expiresIn) {
    return KeycloakWebToken.builder().accessToken(accessToken).expiresIn(expiresIn).build();
  }
}
