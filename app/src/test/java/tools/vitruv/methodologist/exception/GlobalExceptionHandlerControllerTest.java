package tools.vitruv.methodologist.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Checks the status codes and error bodies the {@link GlobalExceptionHandlerController} produces
 * for client mistakes and server failures. A minimal controller and standalone MockMvc keep the
 * test free of a Spring context.
 */
class GlobalExceptionHandlerControllerTest {
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new TestController())
            .setControllerAdvice(new GlobalExceptionHandlerController())
            .build();
  }

  @Test
  void emptyRequestBody_isBadRequest() throws Exception {
    mockMvc
        .perform(put("/test/body").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("FORMAT_ERROR"))
        .andExpect(jsonPath("$.message").value("Request body is missing or malformed"));
  }

  @Test
  void textInsteadOfNumberInPath_isBadRequest() throws Exception {
    mockMvc
        .perform(get("/test/items/abc"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("FORMAT_ERROR"))
        .andExpect(jsonPath("$.message").value("Invalid value for parameter 'id'"));
  }

  @Test
  void missingRequestParameter_isBadRequest() throws Exception {
    mockMvc
        .perform(get("/test/search"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("BAD_REQUEST_ERROR"));
  }

  @Test
  void missingMultipartFile_isBadRequest() throws Exception {
    mockMvc
        .perform(multipart("/test/upload"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("BAD_REQUEST_ERROR"));
  }

  @Test
  void ownerRequired_isForbidden() throws Exception {
    mockMvc
        .perform(get("/test/owner"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value(OwnerRequiredException.MESSAGE_TEMPLATE));
  }

  @Test
  void vsumBuildingFailure_isBadRequest() throws Exception {
    mockMvc
        .perform(get("/test/vsum-building"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value(VsumBuildingException.ERROR_TEMPLATE))
        .andExpect(jsonPath("$.message").value("queue full"));
  }

  @Test
  void publishInProgress_isConflict() throws Exception {
    mockMvc
        .perform(get("/test/publish-in-progress"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("already publishing"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"/test/cli", "/test/build-artifact", "/test/file-hashing"})
  void serverSideFailures_areInternalServerError(String path) throws Exception {
    mockMvc.perform(get(path)).andExpect(status().isInternalServerError());
  }

  @Test
  void unexpectedException_isInternalServerError_andLoggedAsError() throws Exception {
    Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandlerController.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    try {
      mockMvc
          .perform(get("/test/crash"))
          .andExpect(status().isInternalServerError())
          .andExpect(jsonPath("$.error").value("INTERNAL_SERVER_ERROR"));
    } finally {
      logger.detachAppender(appender);
    }

    assertThat(appender.list).extracting(ILoggingEvent::getLevel).containsExactly(Level.ERROR);
  }

  @ParameterizedTest
  @ValueSource(strings = {"/test/cli", "/test/build-artifact", "/test/file-hashing"})
  void serverSideFailures_areLoggedAsErrorWithTheirStackTrace(String path) throws Exception {
    List<ILoggingEvent> events = logsOf(() -> mockMvc.perform(get(path)));

    assertThat(events).extracting(ILoggingEvent::getLevel).containsExactly(Level.ERROR);
    assertThat(events.getFirst().getThrowableProxy()).isNotNull();
  }

  @Test
  void notFound_isLoggedAtDebugOnly_withoutStackTrace() throws Exception {
    List<ILoggingEvent> events =
        logsOf(() -> mockMvc.perform(get("/test/not-found")).andExpect(status().isNotFound()));

    assertThat(events).extracting(ILoggingEvent::getLevel).containsOnly(Level.DEBUG);
    assertThat(events).allSatisfy(event -> assertThat(event.getThrowableProxy()).isNull());
  }

  @Test
  void clientErrors_areLoggedWithoutTheirMessage_whichMayContainPersonalData() throws Exception {
    List<ILoggingEvent> events =
        logsOf(
            () -> {
              mockMvc.perform(get("/test/conflict")).andExpect(status().isConflict());
              mockMvc
                  .perform(
                      put("/test/body")
                          .contentType(MediaType.APPLICATION_JSON)
                          .content("{\"email\": alice@example.com}"))
                  .andExpect(status().isBadRequest());
            });

    assertThat(events).hasSize(2);
    assertThat(events)
        .allSatisfy(
            event -> {
              assertThat(event.getLevel()).isEqualTo(Level.DEBUG);
              assertThat(event.getFormattedMessage()).doesNotContain("alice@example.com");
              assertThat(event.getThrowableProxy()).isNull();
            });
  }

  @Test
  void setupServiceRejection_isLoggedAtDebug_andAnUnavailableSetupServiceAsError()
      throws Exception {
    assertThat(logsOf(() -> mockMvc.perform(get("/test/setup-service-rejected"))))
        .extracting(ILoggingEvent::getLevel)
        .containsExactly(Level.DEBUG);
    assertThat(logsOf(() -> mockMvc.perform(get("/test/setup-service-down"))))
        .extracting(ILoggingEvent::getLevel)
        .containsExactly(Level.ERROR);
  }

  @Test
  void storedBuildFailure_isLoggedAtDebugOnly_andStillBadGateway() throws Exception {
    List<ILoggingEvent> logs =
        logsOf(
            () ->
                mockMvc
                    .perform(get("/test/stored-build-failure"))
                    .andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.errorCode").value("VSUM_BUILD_FAILED")));

    // the build logged the failure already when it happened
    assertThat(logs).extracting(ILoggingEvent::getLevel).containsExactly(Level.DEBUG);
  }

  /** Runs the requests and returns what the handler logged, at any level. */
  private static List<ILoggingEvent> logsOf(ThrowingRunnable requests) throws Exception {
    Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandlerController.class);
    Level previousLevel = logger.getLevel();
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    logger.setLevel(Level.DEBUG);
    try {
      requests.run();
    } finally {
      logger.detachAppender(appender);
      logger.setLevel(previousLevel);
    }
    return appender.list;
  }

  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws Exception;
  }

  @Test
  void setupServiceRejection_isBadRequest() throws Exception {
    mockMvc
        .perform(get("/test/setup-service-rejected"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value(SetupServiceException.ERROR_TEMPLATE))
        .andExpect(jsonPath("$.message").value("invalid metamodel"))
        .andExpect(jsonPath("$.errorCode").doesNotExist())
        .andExpect(jsonPath("$.problems").doesNotExist());
  }

  @Test
  void setupServiceBuildFailure_isBadRequest_withTheErrorCodeAndProblems() throws Exception {
    mockMvc
        .perform(get("/test/setup-service-build-failed"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value(SetupServiceException.ERROR_TEMPLATE))
        .andExpect(jsonPath("$.message").value("does not compile"))
        .andExpect(jsonPath("$.errorCode").value("VSUM_BUILD_FAILED"))
        .andExpect(jsonPath("$.problems.length()").value(2))
        .andExpect(jsonPath("$.problems[0].source").value("REACTIONS"))
        .andExpect(jsonPath("$.problems[0].file").value("my.reactions"))
        .andExpect(jsonPath("$.problems[0].fileId").value(12))
        .andExpect(jsonPath("$.problems[0].line").value(51))
        .andExpect(jsonPath("$.problems[0].column").value(30))
        .andExpect(jsonPath("$.problems[0].message").value("unknown class"))
        .andExpect(jsonPath("$.problems[0].excerpt").value("new model2::Entitiy"))
        .andExpect(jsonPath("$.problems[0].hint").value("Did you mean 'Entity'?"))
        .andExpect(jsonPath("$.problems[0].hiddenFollowUpErrors").value(3))
        .andExpect(jsonPath("$.problems[1].source").value("INFRASTRUCTURE"))
        .andExpect(jsonPath("$.problems[1].message").value("offline"))
        .andExpect(jsonPath("$.problems[1].file").doesNotExist())
        .andExpect(jsonPath("$.problems[1].fileId").doesNotExist())
        .andExpect(jsonPath("$.problems[1].line").doesNotExist())
        .andExpect(jsonPath("$.problems[1].hint").doesNotExist())
        .andExpect(jsonPath("$.problems[1].hiddenFollowUpErrors").doesNotExist());
  }

  @Test
  void setupServiceUnavailable_isBadGateway_withTheErrorCode() throws Exception {
    mockMvc
        .perform(get("/test/setup-service-unavailable"))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.errorCode").value("VSUM_BUILD_UNAVAILABLE"))
        .andExpect(jsonPath("$.problems").doesNotExist());
  }

  @Test
  void setupServiceFailure_isBadGateway() throws Exception {
    mockMvc
        .perform(get("/test/setup-service-down"))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.error").value(SetupServiceException.ERROR_TEMPLATE));
  }

  /** Minimal controller whose endpoints trigger the handled exceptions. */
  @RestController
  static class TestController {
    @PutMapping("/test/body")
    Map<String, String> body(@RequestBody Map<String, String> body) {
      return body;
    }

    @GetMapping("/test/items/{id}")
    Long item(@PathVariable("id") Long id) {
      return id;
    }

    @GetMapping("/test/search")
    String search(@RequestParam("query") String query) {
      return query;
    }

    @PostMapping("/test/upload")
    String upload(@RequestParam("file") MultipartFile file) {
      return file.getOriginalFilename();
    }

    @GetMapping("/test/owner")
    void owner() {
      throw new OwnerRequiredException();
    }

    @GetMapping("/test/vsum-building")
    void vsumBuilding() {
      throw new VsumBuildingException("queue full");
    }

    @GetMapping("/test/publish-in-progress")
    void publishInProgress() {
      throw new PublishInProgressException("already publishing");
    }

    @GetMapping("/test/cli")
    void cli() {
      throw new CLIExecuteException("CLI failed");
    }

    @GetMapping("/test/build-artifact")
    void buildArtifact() {
      throw new BuildArtifactCreationException("Bundle failed");
    }

    @GetMapping("/test/file-hashing")
    void fileHashing() {
      throw new FileHashingException("Hashing failed");
    }

    @GetMapping("/test/setup-service-rejected")
    void setupServiceRejected() {
      throw new SetupServiceException("invalid metamodel", HttpStatus.BAD_REQUEST);
    }

    @GetMapping("/test/setup-service-build-failed")
    void setupServiceBuildFailed() {
      throw new SetupServiceException(
          "does not compile",
          HttpStatus.UNPROCESSABLE_ENTITY,
          "VSUM_BUILD_FAILED",
          List.of(
              new BuildProblem(
                  "REACTIONS",
                  "my.reactions",
                  12L,
                  51,
                  30,
                  "unknown class",
                  "new model2::Entitiy",
                  "Did you mean 'Entity'?",
                  3),
              new BuildProblem(
                  "INFRASTRUCTURE", null, null, null, null, "offline", null, null, null)));
    }

    @GetMapping("/test/setup-service-unavailable")
    void setupServiceUnavailable() {
      throw new SetupServiceException(
          "try again later", HttpStatus.SERVICE_UNAVAILABLE, "VSUM_BUILD_UNAVAILABLE", null);
    }

    @GetMapping("/test/setup-service-down")
    void setupServiceDown() {
      throw new SetupServiceException("connection refused");
    }

    @GetMapping("/test/stored-build-failure")
    void storedBuildFailure() {
      throw new StoredBuildFailureException("does not compile", "VSUM_BUILD_FAILED", null);
    }

    @GetMapping("/test/not-found")
    void notFound() {
      throw new NotFoundException("Vsum id");
    }

    @GetMapping("/test/conflict")
    void conflict() {
      throw new DataIntegrityViolationException(
          "duplicate key: Key (email)=(alice@example.com) already exists");
    }

    @GetMapping("/test/crash")
    void crash() {
      throw new IllegalStateException("Unexpected failure");
    }
  }
}
