package tools.vitruv.methodologist.lsp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/** Unit tests for the shared session lifecycle in {@link AbstractLspWebSocketHandler}. */
class AbstractLspWebSocketHandlerTest {

  private static final String SESSION_ID = "session-1";

  @TempDir Path tempRoot;

  private TestHandler handler;
  private WebSocketSession session;

  @BeforeEach
  void setUp() {
    handler = new TestHandler();
    session = mock(WebSocketSession.class);
    when(session.getId()).thenReturn(SESSION_ID);
  }

  @AfterEach
  void tearDown() {
    handler.shutdown();
  }

  @Test
  void constructor_doesNotStartCleanupScheduler() {
    assertThat(scheduler().getQueue()).isEmpty();
    assertThat(scheduler().getPoolSize()).isZero();
  }

  @Test
  void startCleanupScheduler_schedulesCleanupAndShutdownStopsIt() {
    handler.startCleanupScheduler();

    assertThat(scheduler().getQueue()).hasSize(1);

    handler.shutdown();

    assertThat(scheduler().isShutdown()).isTrue();
  }

  @Test
  void afterConnectionEstablished_startsProcessRegistersSessionAndSendsWorkspaceReady()
      throws Exception {
    Path sessionDir = Files.createDirectory(tempRoot.resolve("session"));
    handler.launch =
        new AbstractLspWebSocketHandler.LspLaunch(
            sessionDir,
            sessionDir,
            List.of(AbstractLspWebSocketHandler.javaExecutable(), "-version"));

    handler.afterConnectionEstablished(session);

    TestProcess process = handler.sessions.get(SESSION_ID);
    assertThat(process).isNotNull();
    assertThat(process.tempDir()).isEqualTo(sessionDir);
    assertThat(process.process).isNotNull();
    assertThat(handler.lastActivity).containsKey(SESSION_ID);
    assertThat(process.readStarted.await(5, TimeUnit.SECONDS)).isTrue();
    verify(session, timeout(5000))
        .sendMessage(
            argThat(
                message ->
                    message instanceof TextMessage text
                        && text.getPayload().contains("\"type\":\"workspaceReady\"")
                        && text.getPayload().contains(sessionDir.toUri().toString())));
  }

  @Test
  void handleTextMessage_forwardsPayloadToSessionProcess() throws Exception {
    TestProcess process = register(Files.createDirectory(tempRoot.resolve("ws")));

    handler.handleTextMessage(session, new TextMessage("{\"id\":1}"));

    assertThat(process.sent).containsExactly("{\"id\":1}");
    assertThat(handler.lastActivity).containsKey(SESSION_ID);
  }

  @Test
  void afterConnectionClosed_destroysProcessAndDeletesTempDir() throws Exception {
    Path dir = Files.createDirectory(tempRoot.resolve("ws"));
    Files.writeString(dir.resolve("file.ecore"), "content");
    TestProcess process = register(dir);

    handler.afterConnectionClosed(session, CloseStatus.NORMAL);

    assertThat(process.destroyed).isTrue();
    assertThat(dir).doesNotExist();
    assertThat(handler.sessions).isEmpty();
    assertThat(handler.lastActivity).isEmpty();
  }

  @Test
  void handleTransportError_destroysProcessAndRemovesSession() throws Exception {
    TestProcess process = register(Files.createDirectory(tempRoot.resolve("ws")));

    handler.handleTransportError(session, new IllegalStateException("broken"));

    assertThat(process.destroyed).isTrue();
    assertThat(handler.sessions).isEmpty();
  }

  @Test
  void cleanupInactiveSessions_removesOnlyTimedOutSessions() throws Exception {
    final TestProcess stale = register(Files.createDirectory(tempRoot.resolve("stale")));
    TestProcess active = new TestProcess(Files.createDirectory(tempRoot.resolve("active")));
    handler.sessions.put("active", active);
    handler.lastActivity.put("active", System.currentTimeMillis());

    handler.cleanupInactiveSessions();

    assertThat(stale.destroyed).isTrue();
    assertThat(active.destroyed).isFalse();
    assertThat(handler.sessions).containsOnlyKeys("active");
  }

  @Test
  void shutdown_cleansUpRemainingSessions() throws Exception {
    TestProcess process = register(Files.createDirectory(tempRoot.resolve("ws")));

    handler.shutdown();

    assertThat(process.destroyed).isTrue();
    assertThat(handler.sessions).isEmpty();
  }

  @Test
  void extractQueryParam_parsesNumericValuesOnly() throws Exception {
    when(session.getUri()).thenReturn(new URI("ws://localhost/lsp?foo=bar&vsumId=7&bad=x"));

    assertThat(handler.extractQueryParam(session, "vsumId")).isEqualTo(7L);
    assertThat(handler.extractQueryParam(session, "missing")).isNull();
    assertThat(handler.extractQueryParam(session, "bad")).isNull();
  }

  private ScheduledThreadPoolExecutor scheduler() {
    return (ScheduledThreadPoolExecutor) ReflectionTestUtils.getField(handler, "cleanupScheduler");
  }

  private TestProcess register(Path tempDir) {
    TestProcess process = new TestProcess(tempDir);
    handler.sessions.put(SESSION_ID, process);
    handler.lastActivity.put(SESSION_ID, 0L);
    return process;
  }

  private static final class TestProcess implements LspSessionProcess {
    final Path tempDir;
    final Process process;
    final List<String> sent = new ArrayList<>();
    final CountDownLatch readStarted = new CountDownLatch(1);
    boolean destroyed;

    TestProcess(Path tempDir) {
      this(tempDir, null);
    }

    TestProcess(Path tempDir, Process process) {
      this.tempDir = tempDir;
      this.process = process;
    }

    @Override
    public void sendToLsp(String message) {
      sent.add(message);
    }

    @Override
    public void readFromLsp() {
      readStarted.countDown();
    }

    @Override
    public void destroy() {
      destroyed = true;
      if (process != null) {
        process.destroyForcibly();
      }
    }

    @Override
    public Path tempDir() {
      return tempDir;
    }
  }

  private static final class TestHandler extends AbstractLspWebSocketHandler<TestProcess> {
    LspLaunch launch;

    @Override
    protected String serverName() {
      return "TEST-LSP";
    }

    @Override
    protected LspLaunch prepareLaunch(WebSocketSession session) {
      return launch;
    }

    @Override
    protected TestProcess createProcess(
        WebSocketSession session,
        Process process,
        BufferedWriter writer,
        BufferedReader reader,
        Path sessionDir) {
      return new TestProcess(sessionDir, process);
    }
  }
}
