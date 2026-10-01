package tools.vitruv.methodologist.health;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Health of the Keycloak realm that issues and signs the application's tokens, reported as the
 * {@code keycloak} component. Probes the realm's OpenID Connect discovery document, which is public
 * and only served when the realm exists.
 */
@Component
public class KeycloakHealthIndicator extends HttpProbeHealthIndicator {

  /**
   * Creates the indicator.
   *
   * @param keycloakUrl base URL of Keycloak
   * @param realm the realm the application uses
   * @param timeout probe timeout
   */
  public KeycloakHealthIndicator(
      @Value("${keycloak.url}") String keycloakUrl,
      @Value("${keycloak.realm}") String realm,
      @Value("${health.upstream.timeout:3s}") Duration timeout) {
    super(
        SetupServiceHealthIndicator.stripTrailingSlash(keycloakUrl)
            + "/realms/"
            + realm
            + "/.well-known/openid-configuration",
        timeout);
  }
}
