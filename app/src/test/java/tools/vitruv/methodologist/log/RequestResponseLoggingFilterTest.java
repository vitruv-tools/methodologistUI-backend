package tools.vitruv.methodologist.log;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestResponseLoggingFilterTest {
  private final RequestResponseLoggingFilter filter = new RequestResponseLoggingFilter();
  private final Logger logger =
      (Logger) LoggerFactory.getLogger(RequestResponseLoggingFilter.class);
  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
  private Level previousLevel;

  @BeforeEach
  void captureLogs() {
    previousLevel = logger.getLevel();
    logger.setLevel(Level.DEBUG);
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void stopCapturing() {
    logger.detachAppender(appender);
    logger.setLevel(previousLevel);
    MDC.clear();
  }

  @Test
  void logsARequestOnce_withoutBodiesQueryOrClientAddress() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/users/sign-up");
    request.setQueryString("email=alice@example.com");
    request.setRemoteAddr("192.0.2.17");
    request.setContentType("application/json");
    request.setContent(
        "{\"email\":\"alice@example.com\",\"password\":\"secret\"}"
            .getBytes(StandardCharsets.UTF_8));

    filter.doFilter(
        request,
        new MockHttpServletResponse(),
        (req, res) -> {
          ((HttpServletResponse) res).setStatus(201);
          res.getWriter().write("{\"email\":\"alice@example.com\"}");
        });

    assertThat(appender.list).hasSize(1);
    ILoggingEvent event = appender.list.getFirst();
    assertThat(event.getLevel()).isEqualTo(Level.INFO);
    assertThat(event.getFormattedMessage()).startsWith("POST /api/v1/users/sign-up -> 201 in ");
    assertThat(event.getThrowableProxy()).isNull();
    assertThat(event.toString() + event.getMDCPropertyMap() + event.getMarkerList())
        .doesNotContain("alice@example.com", "secret", "192.0.2.17");
  }

  @ParameterizedTest
  @CsvSource({
    "/api/v1/vsums/1, 200, INFO",
    "/api/v1/vsums/1, 204, INFO",
    "/api/v1/vsums/1, 404, WARN",
    "/api/v1/vsums/1, 400, WARN",
    "/api/v1/vsums/1, 500, ERROR",
    "/actuator/health, 200, DEBUG",
    "/actuator/health, 503, ERROR",
    "/swagger-ui/index.html, 200, DEBUG"
  })
  void logsAtTheLevelOfTheStatus(String path, int status, String level) throws Exception {
    filter.doFilter(
        new MockHttpServletRequest("GET", path),
        new MockHttpServletResponse(),
        (req, res) -> ((HttpServletResponse) res).setStatus(status));

    assertThat(appender.list)
        .extracting(ILoggingEvent::getLevel)
        .containsExactly(Level.toLevel(level));
  }

  @Test
  void keepsTheRequestIdInTheMdcOnlyWhileTheRequestRuns() throws Exception {
    AtomicReference<String> requestId = new AtomicReference<>();

    filter.doFilter(
        new MockHttpServletRequest("GET", "/api/v1/vsums"),
        new MockHttpServletResponse(),
        (req, res) -> requestId.set(MDC.get(RequestResponseLoggingFilter.REQUEST_ID)));

    assertThat(requestId.get()).isNotBlank();
    assertThat(appender.list.getFirst().getMDCPropertyMap())
        .containsEntry(RequestResponseLoggingFilter.REQUEST_ID, requestId.get());
    assertThat(MDC.get(RequestResponseLoggingFilter.REQUEST_ID)).isNull();
  }
}
