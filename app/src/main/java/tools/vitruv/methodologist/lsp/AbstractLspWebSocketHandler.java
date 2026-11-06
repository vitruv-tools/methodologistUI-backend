package tools.vitruv.methodologist.lsp;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Base class for WebSocket handlers that bridge a client to a language server process per session.
 * Handles session tracking, process startup, inactivity cleanup, temporary-directory cleanup and
 * shutdown, while subclasses prepare the workspace, choose the launch command and implement the
 * process type.
 *
 * @param <P> the per-session language server process type
 */
public abstract class AbstractLspWebSocketHandler<P extends LspSessionProcess>
    extends TextWebSocketHandler {

  protected static final long TIMEOUT_MS = 10L * 60 * 1000; // 10 minutes inactivity timeout
  protected static final long CLEANUP_INTERVAL_SECONDS = 60;
  protected final Logger logger = LoggerFactory.getLogger(getClass());
  protected final ConcurrentHashMap<String, P> sessions = new ConcurrentHashMap<>();
  protected final ConcurrentHashMap<String, Long> lastActivity = new ConcurrentHashMap<>();
  private final ScheduledExecutorService cleanupScheduler = Executors.newScheduledThreadPool(1);

  /**
   * Describes how to start the language server for one session.
   *
   * @param sessionDir the session's temporary directory, deleted on cleanup
   * @param projectDir the workspace root reported to the client and used as working directory
   * @param command the command that starts the language server
   */
  protected record LspLaunch(Path sessionDir, Path projectDir, List<String> command) {}

  /**
   * Starts the periodic task that cleans up inactive sessions. Runs after construction, so a
   * failing subclass constructor never leaves a running scheduler thread behind.
   */
  @PostConstruct
  public void startCleanupScheduler() {
    cleanupScheduler.scheduleAtFixedRate(
        this::cleanupInactiveSessions,
        CLEANUP_INTERVAL_SECONDS,
        CLEANUP_INTERVAL_SECONDS,
        TimeUnit.SECONDS);

    logger.info(
        "🧹 LSP Cleanup scheduler started (checking every {}s, timeout: {}min)",
        CLEANUP_INTERVAL_SECONDS,
        TIMEOUT_MS / 60000);
  }

  /**
   * Returns the server name used in log messages.
   *
   * @return the server name, e.g. {@code LSP} or {@code OCL-LSP}
   */
  protected abstract String serverName();

  /**
   * Prepares the session workspace and determines how to start the language server.
   *
   * @param session the WebSocket session being established
   * @return the launch description
   * @throws IOException if the workspace cannot be prepared
   */
  protected abstract LspLaunch prepareLaunch(WebSocketSession session) throws IOException;

  /**
   * Wraps a started language server process for the given session.
   *
   * @param session the WebSocket session
   * @param process the started process
   * @param writer the writer to the process's standard input
   * @param reader the reader of the process's standard output
   * @param sessionDir the session's temporary directory
   * @return the session process
   */
  protected abstract P createProcess(
      WebSocketSession session,
      Process process,
      BufferedWriter writer,
      BufferedReader reader,
      Path sessionDir);

  /**
   * Returns the {@code java} executable of the running JVM.
   *
   * @return the path to the {@code java} executable
   */
  protected static String javaExecutable() {
    String javaHome = System.getProperty("java.home");
    return javaHome + File.separator + "bin" + File.separator + "java";
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession session) throws Exception {
    String sessionId = session.getId();
    logger.info("{} WebSocket connection established: {}", serverName(), sessionId);

    lastActivity.put(sessionId, System.currentTimeMillis());

    LspLaunch launch = prepareLaunch(session);

    ProcessBuilder pb = new ProcessBuilder(launch.command());
    pb.directory(launch.projectDir().toFile());
    pb.redirectErrorStream(true);

    Process process = pb.start();
    watchProcessExit(sessionId, process);

    BufferedWriter writer =
        new BufferedWriter(
            new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
    BufferedReader reader =
        new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));

    P lspProcess = createProcess(session, process, writer, reader, launch.sessionDir());
    sessions.put(sessionId, lspProcess);

    new Thread(lspProcess::readFromLsp).start();

    sendWorkspaceReady(session, launch.projectDir());
  }

  private void watchProcessExit(String sessionId, Process process) {
    new Thread(
            () -> {
              try {
                int code = process.waitFor();
                logger.error(
                    "{} process exited for session {} with code {}", serverName(), sessionId, code);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            })
        .start();
  }

  private void sendWorkspaceReady(WebSocketSession session, Path projectDir) {
    CompletableFuture.delayedExecutor(500, TimeUnit.MILLISECONDS)
        .execute(
            () -> {
              try {
                String rootUriMessage =
                    String.format(
                        "{\"type\":\"workspaceReady\",\"rootUri\":\"%s\"}",
                        projectDir.toUri().toString());
                session.sendMessage(new TextMessage(rootUriMessage));
              } catch (Exception e) {
                logger.error(
                    "Failed to send workspaceReady for {}: {}", serverName(), e.getMessage());
              }
            });
  }

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
    String sessionId = session.getId();
    lastActivity.put(sessionId, System.currentTimeMillis());
    P proc = sessions.get(sessionId);
    if (proc != null) {
      try {
        proc.sendToLsp(message.getPayload());
      } catch (IOException e) {
        logger.error("Failed to forward message to LSP for session {}", sessionId, e);
      }
    }
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
    String sessionId = session.getId();
    logger.info(
        "🔌 Connection closed: {} - Status: {} (code: {}, reason: '{}')",
        sessionId,
        status,
        status.getCode(),
        status.getReason());

    cleanupSession(sessionId, "connection closed: " + status.getReason());
  }

  @Override
  public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
    String sessionId = session.getId();
    logger.error("WebSocket transport error for session: {}", sessionId, exception);

    cleanupSession(sessionId, "transport error");
  }

  /**
   * Removes a session and destroys its process and temporary directory. Safe to call more than
   * once, e.g. from both the close callback and the inactivity cleanup.
   *
   * @param sessionId the session ID to clean up
   * @param reason the reason for cleanup (for logging)
   */
  protected void cleanupSession(String sessionId, String reason) {
    P serverProcess = sessions.remove(sessionId);
    lastActivity.remove(sessionId);

    if (serverProcess != null) {
      logger.info("🧹 Cleaning up session: {} - Reason: {}", sessionId, reason);
      try {
        serverProcess.destroy();
        cleanupTempDir(serverProcess.tempDir());
        logger.info("✅ Session cleanup completed for: {}", sessionId);
      } catch (Exception e) {
        logger.error("❌ Error during session cleanup: {}", e.getMessage());
      }
    } else {
      logger.debug("⚠️ No LSP process found for session: {} (already cleaned up)", sessionId);
    }
  }

  /** Cleans up sessions that have been inactive for longer than {@link #TIMEOUT_MS}. */
  protected void cleanupInactiveSessions() {
    long now = System.currentTimeMillis();
    List<String> toCleanup = new ArrayList<>();
    for (Map.Entry<String, Long> entry : lastActivity.entrySet()) {
      if (entry.getValue() == null || (now - entry.getValue()) > TIMEOUT_MS) {
        toCleanup.add(entry.getKey());
      }
    }
    toCleanup.forEach(id -> cleanupSession(id, "timeout"));
  }

  /**
   * Recursively deletes a temporary directory.
   *
   * @param tempDir the directory to delete
   */
  protected void cleanupTempDir(Path tempDir) {
    if (tempDir == null || !Files.exists(tempDir)) {
      return;
    }

    try (Stream<Path> paths = Files.walk(tempDir)) {
      paths
          .sorted(Comparator.reverseOrder())
          .forEach(
              path -> {
                try {
                  Files.delete(path);
                } catch (IOException e) {
                  logger.warn("⚠️ Failed to delete: {}", path);
                }
              });
      logger.info("🗑️ Temp directory cleaned: {}", tempDir);
    } catch (IOException e) {
      logger.warn("⚠️ Failed to walk directory {}: {}", tempDir, e.getMessage());
    }
  }

  /** Stops the cleanup scheduler and cleans up all remaining sessions. */
  @PreDestroy
  public void shutdown() {
    logger.info("🛑 Shutting down LSP WebSocket handler...");

    cleanupScheduler.shutdown();
    try {
      if (!cleanupScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
        cleanupScheduler.shutdownNow();
      }
    } catch (InterruptedException e) {
      cleanupScheduler.shutdownNow();
      Thread.currentThread().interrupt();
    }

    logger.info("🧹 Cleaning up {} remaining sessions...", sessions.size());

    List<String> remainingSessions = new ArrayList<>(sessions.keySet());
    for (String sessionId : remainingSessions) {
      cleanupSession(sessionId, "application shutdown");
    }

    logger.info("✅ LSP WebSocket handler shutdown complete");
  }

  /**
   * Reads a numeric query parameter from the session's handshake URI.
   *
   * @param session the WebSocket session
   * @param param the query parameter name
   * @return the parsed value, or {@code null} if absent or not a number
   */
  protected Long extractQueryParam(WebSocketSession session, String param) {
    try {
      var uri = session.getUri();
      if (uri == null) {
        return null;
      }
      String query = uri.getQuery();
      if (query == null) {
        return null;
      }
      for (String part : query.split("&")) {
        String[] kv = part.split("=", 2);
        if (kv.length == 2 && kv[0].equals(param)) {
          return Long.parseLong(kv[1]);
        }
      }
    } catch (NumberFormatException e) {
      logger.warn("Could not extract {} from LSP WebSocket URI", param);
    }
    return null;
  }
}
