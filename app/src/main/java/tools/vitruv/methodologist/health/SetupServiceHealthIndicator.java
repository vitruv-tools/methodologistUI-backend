package tools.vitruv.methodologist.health;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Health of the setup-service every VSUM build is delegated to, reported as the {@code
 * setupService} component. Probes the setup-service's own actuator health endpoint.
 */
@Component
public class SetupServiceHealthIndicator extends HttpProbeHealthIndicator {

  /**
   * Creates the indicator.
   *
   * @param baseUrl base URL of the setup-service
   * @param timeout probe timeout
   */
  public SetupServiceHealthIndicator(
      @Value("${third_api.setup_service.base_url}") String baseUrl,
      @Value("${health.upstream.timeout:3s}") Duration timeout) {
    super(stripTrailingSlash(baseUrl) + "/actuator/health", timeout);
  }

  static String stripTrailingSlash(String url) {
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }
}
