package tools.vitruv.methodologist.health;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

class HttpProbeHealthIndicatorTest {

  private static final Duration TIMEOUT = Duration.ofMillis(500);

  private MockWebServer server;

  @BeforeEach
  void setUp() throws IOException {
    server = new MockWebServer();
    server.start();
  }

  @AfterEach
  void tearDown() throws IOException {
    server.shutdown();
  }

  @Test
  void setupService_isUp_whenItsHealthEndpointAnswers2xx() throws InterruptedException {
    server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"status\":\"UP\"}"));

    Health health = new SetupServiceHealthIndicator(baseUrl() + "/", TIMEOUT).health();

    assertThat(health.getStatus()).isEqualTo(Status.UP);
    assertThat(health.getDetails()).containsEntry("status", 200);
    RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
    assertThat(request.getMethod()).isEqualTo("GET");
    assertThat(request.getPath()).isEqualTo("/actuator/health");
  }

  @Test
  void setupService_isDown_whenItReportsAnErrorStatus() {
    server.enqueue(new MockResponse().setResponseCode(503).setBody("{\"status\":\"DOWN\"}"));

    Health health = new SetupServiceHealthIndicator(baseUrl(), TIMEOUT).health();

    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    assertThat(health.getDetails()).containsEntry("status", 503);
  }

  @Test
  void setupService_isDown_whenItDoesNotAnswerWithinTheTimeout() {
    server.enqueue(new MockResponse().setResponseCode(200).setHeadersDelay(2, TimeUnit.SECONDS));

    long start = System.nanoTime();
    Health health = new SetupServiceHealthIndicator(baseUrl(), TIMEOUT).health();
    long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    assertThat(health.getDetails()).containsKey("error");
    assertThat(elapsedMillis).isLessThan(1_900);
  }

  @Test
  void setupService_isDown_whenNothingListens() throws IOException {
    int freePort;
    try (ServerSocket socket = new ServerSocket(0)) {
      freePort = socket.getLocalPort();
    }

    Health health =
        new SetupServiceHealthIndicator("http://localhost:" + freePort, TIMEOUT).health();

    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    assertThat(health.getDetails())
        .containsEntry("url", "http://localhost:" + freePort + "/actuator/health")
        .containsKey("error");
  }

  @Test
  void keycloak_probesTheRealmsDiscoveryDocument() throws InterruptedException {
    server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"issuer\":\"x\"}"));

    Health health = new KeycloakHealthIndicator(baseUrl(), "methodologist", TIMEOUT).health();

    assertThat(health.getStatus()).isEqualTo(Status.UP);
    assertThat(server.takeRequest(1, TimeUnit.SECONDS).getPath())
        .isEqualTo("/realms/methodologist/.well-known/openid-configuration");
  }

  @Test
  void keycloak_isDown_whenTheRealmDoesNotExist() {
    server.enqueue(new MockResponse().setResponseCode(404));

    Health health = new KeycloakHealthIndicator(baseUrl(), "missing", TIMEOUT).health();

    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    assertThat(health.getDetails()).containsEntry("status", 404);
  }

  private String baseUrl() {
    String url = server.url("/").toString();
    return url.substring(0, url.length() - 1);
  }
}
