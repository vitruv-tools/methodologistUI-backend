package tools.vitruv.methodologist.health;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Reports an upstream service as UP when a GET to one of its URLs answers with a 2xx status within
 * the timeout, and DOWN otherwise (error status, connection failure or timeout).
 *
 * <p>The probe uses its own short-timeout client on purpose: the clients the application uses for
 * real work (e.g. the 300 s setup-service client) would make a dead upstream hang the health
 * endpoint instead of reporting it.
 */
abstract class HttpProbeHealthIndicator extends AbstractHealthIndicator {

  private final String url;
  private final RestClient restClient;

  /**
   * Creates the probe.
   *
   * @param url the URL to GET
   * @param timeout connect and read timeout
   */
  protected HttpProbeHealthIndicator(String url, Duration timeout) {
    super("Health probe failed");
    this.url = url;
    HttpClient httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(timeout);
    this.restClient = RestClient.builder().requestFactory(requestFactory).build();
  }

  @Override
  protected void doHealthCheck(Health.Builder builder) {
    builder.withDetail("url", url);
    try {
      HttpStatusCode status =
          restClient.get().uri(url).exchange((request, response) -> response.getStatusCode(), true);
      builder.withDetail("status", status.value());
      if (status.is2xxSuccessful()) {
        builder.up();
      } else {
        builder.down();
      }
    } catch (RuntimeException e) {
      builder.down().withDetail("error", e.getClass().getSimpleName() + ": " + e.getMessage());
    }
  }
}
