package tools.vitruv.methodologist.log;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.vitruv.methodologist.exception.GlobalExceptionHandlerController;
import tools.vitruv.methodologist.exception.NotFoundException;

/**
 * Sends requests through the request log filter and the exception handler and checks everything
 * logged at INFO or above, as the application logs it.
 */
class RequestLoggingTest {
  private static final String EMAIL = "alice@example.com";

  private final Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new TestController())
            .setControllerAdvice(new GlobalExceptionHandlerController())
            .addFilters(new RequestResponseLoggingFilter())
            .build();
    appender.start();
    root.addAppender(appender);
  }

  @AfterEach
  void tearDown() {
    root.detachAppender(appender);
  }

  @Test
  void normalRequestProducesExactlyOneLine_withoutBodyAndEmail() throws Exception {
    mockMvc
        .perform(
            post("/test/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + EMAIL + "\",\"password\":\"Secret#123\"}"))
        .andExpect(status().isOk());

    List<ILoggingEvent> lines = loggedAtInfoOrAbove();
    assertThat(lines).hasSize(1);
    assertThat(lines.getFirst().getLevel()).isEqualTo(Level.INFO);
    assertThat(lines.getFirst().getFormattedMessage())
        .startsWith("POST /test/users -> 200")
        .doesNotContain(EMAIL, "Secret#123");
  }

  @Test
  void notFoundProducesNoErrorAndNoStackTrace() throws Exception {
    mockMvc.perform(get("/test/missing")).andExpect(status().isNotFound());

    List<ILoggingEvent> lines = loggedAtInfoOrAbove();
    assertThat(lines).extracting(ILoggingEvent::getLevel).containsExactly(Level.WARN);
    assertThat(lines.getFirst().getThrowableProxy()).isNull();
  }

  @Test
  void crashIsLoggedAsErrorWithItsStackTrace() throws Exception {
    mockMvc.perform(get("/test/crash")).andExpect(status().isInternalServerError());

    List<ILoggingEvent> lines = loggedAtInfoOrAbove();
    assertThat(lines).extracting(ILoggingEvent::getLevel).containsOnly(Level.ERROR);
    assertThat(lines).anySatisfy(line -> assertThat(line.getThrowableProxy()).isNotNull());
  }

  private List<ILoggingEvent> loggedAtInfoOrAbove() {
    return appender.list.stream()
        .filter(event -> event.getLevel().isGreaterOrEqual(Level.INFO))
        .toList();
  }

  @RestController
  static class TestController {
    @PostMapping("/test/users")
    String create(@RequestBody String body) {
      return "{\"email\":\"" + EMAIL + "\"}";
    }

    @GetMapping("/test/missing")
    void missing() {
      throw new NotFoundException("Vsum id");
    }

    @GetMapping("/test/crash")
    void crash() {
      throw new IllegalStateException("Unexpected failure");
    }
  }
}
