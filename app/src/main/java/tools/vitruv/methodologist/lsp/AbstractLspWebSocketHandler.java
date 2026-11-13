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
import java.util.Optional;
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
import tools.vitruv.methodologist.exception.LspProcessException;

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

  /** Session attribute holding the VSUM ID the handshake interceptor authorized. */
  public static final String VSUM_ID_ATTRIBUTE = "vsumId";

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

    logger.debug(
        "{} cleanup scheduler started (checking every {}s, timeout: {}min)",
        serverName(),
        CLEANUP_INTERVAL_SECONDS,
        inactivityTimeoutMillis() / 60000);
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
    logger.debug("{} WebSocket connection established: {}", serverName(), sessionId);

    Optional<CloseStatus> rejection = rejectConnection(session);
    if (rejection.isPresent()) {
      session.close(rejection.get());
      return;
    }

    lastActivity.put(sessionId, System.currentTimeMillis());

    LspLaunch launch = null;
    Process process = null;
    P lspProcess = null;
    try {
      launch = prepareLaunch(session);

      ProcessBuilder pb = new ProcessBuilder(launch.command());
      pb.directory(launch.projectDir().toFile());
      configureProcess(pb);

      process = pb.start();
      watchProcessExit(sessionId, process);

      BufferedWriter writer =
          new BufferedWriter(
              new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
      BufferedReader reader =
          new BufferedReader(
              new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));

      lspProcess = createProcess(session, process, writer, reader, launch.sessionDir());
      sessions.put(sessionId, lspProcess);
    } catch (IOException | RuntimeException e) {
      logger.error("Failed to start the {} server for session {}", serverName(), sessionId, e);
      lastActivity.remove(sessionId);
      if (process != null) {
        process.destroyForcibly();
      }
      if (launch != null) {
        cleanupTempDir(launch.sessionDir());
      }
      launchFailed(session);
      session.close(CloseStatus.SERVER_ERROR);
      return;
    }

    sessionRegistered(session);

    if (announceWorkspaceBeforeReading()) {
      // Sent before the reader starts, so the session is never written concurrently.
      if (!sendWorkspaceReadyNow(session, launch.projectDir())) {
        cleanupSession(sessionId, "workspaceReady could not be sent");
        closeQuietly(session);
        return;
      }
      // The client may have disconnected while the server was starting.
      if (!session.isOpen()) {
        cleanupSession(sessionId, "client disconnected during startup");
        return;
      }
      startReader(lspProcess);
    } else {
      startReader(lspProcess);
      sendWorkspaceReady(session, launch.projectDir());
    }
  }

  private void startReader(P lspProcess) {
    new Thread(lspProcess::readFromLsp).start();
  }

  /**
   * Decides whether a new connection is accepted before any workspace or process is created. A
   * subclass that admits connections only conditionally, e.g. up to a number of running servers,
   * must undo what it reserved in {@link #launchFailed} and when the session's process is
   * destroyed.
   *
   * @param session the WebSocket session being established
   * @return the status to close the session with, or empty to accept it
   */
  protected Optional<CloseStatus> rejectConnection(WebSocketSession session) {
    return Optional.empty();
  }

  /**
   * Called when preparing or starting the language server failed, after the temporary directory and
   * any started process have been removed. Releases what {@link #rejectConnection} reserved.
   *
   * @param session the WebSocket session whose server could not be started
   */
  protected void launchFailed(WebSocketSession session) {
    // nothing reserved by default
  }

  /**
   * Configures the process before it is started. By default the error output is merged into the
   * standard output.
   *
   * @param builder the builder of the language server process
   */
  protected void configureProcess(ProcessBuilder builder) {
    builder.redirectErrorStream(true);
  }

  /**
   * Called once the session's process is registered and before the client is told that the
   * workspace is ready.
   *
   * @param session the WebSocket session
   */
  protected void sessionRegistered(WebSocketSession session) {
    // nothing to do by default
  }

  /**
   * Tells whether {@code workspaceReady} is sent before the reader of the server output starts. By
   * default it is sent shortly after the reader has started.
   *
   * @return {@code true} to send it first, so that the session is never written concurrently
   */
  protected boolean announceWorkspaceBeforeReading() {
    return false;
  }

  /**
   * Returns how long a session may stay without client messages before its server is stopped.
   *
   * @return the inactivity timeout in milliseconds
   */
  protected long inactivityTimeoutMillis() {
    return TIMEOUT_MS;
  }

  private boolean sendWorkspaceReadyNow(WebSocketSession session, Path projectDir) {
    try {
      session.sendMessage(new TextMessage(workspaceReadyMessage(projectDir)));
      return true;
    } catch (IOException | IllegalStateException e) {
      logger.debug(
          "Could not send workspaceReady to {} session {}", serverName(), session.getId(), e);
      return false;
    }
  }

  private static String workspaceReadyMessage(Path projectDir) {
    return String.format(
        "{\"type\":\"workspaceReady\",\"rootUri\":\"%s\"}", projectDir.toUri().toString());
  }

  /**
   * Closes the session with {@link CloseStatus#SERVER_ERROR}, ignoring a failure to do so.
   *
   * @param session the WebSocket session
   */
  protected void closeQuietly(WebSocketSession session) {
    try {
      session.close(CloseStatus.SERVER_ERROR);
    } catch (IOException e) {
      logger.debug("Could not close {} session {}", serverName(), session.getId(), e);
    }
  }

  private void watchProcessExit(String sessionId, Process process) {
    new Thread(
            () -> {
              try {
                int code = process.waitFor();
                if (sessions.containsKey(sessionId)) {
                  logger.warn(
                      "{} process for session {} stopped unexpectedly with code {}",
                      serverName(),
                      sessionId,
                      code);
                } else {
                  logger.debug(
                      "{} process for session {} exited with code {}",
                      serverName(),
                      sessionId,
                      code);
                }
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
                String rootUriMessage = workspaceReadyMessage(projectDir);
                session.sendMessage(new TextMessage(rootUriMessage));
              } catch (Exception e) {
                // usually the client has disconnected in the meantime
                logger.debug(
                    "Could not send workspaceReady to {} session {}",
                    serverName(),
                    session.getId(),
                    e);
              }
            });
  }

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
    String sessionId = session.getId();
    lastActivity.put(sessionId, System.currentTimeMillis());
    P proc = sessions.get(sessionId);
    if (proc == null) {
      noSessionProcess(session);
      return;
    }
    try {
      proc.sendToLsp(message.getPayload());
    } catch (LspProcessException e) {
      // The message is not logged, as it may contain the user's query.
      logger.debug(
          "Rejected a message on {} session {}: {}", serverName(), sessionId, e.getMessage());
      session.close(CloseStatus.BAD_DATA);
    } catch (IOException e) {
      // the process has stopped, which is logged once when it happens
      logger.debug("Could not forward a message to {} session {}", serverName(), sessionId, e);
    }
  }

  /**
   * Called for a client message of a session without a running server. By default the message is
   * ignored.
   *
   * @param session the WebSocket session
   * @throws IOException if closing the session fails
   */
  protected void noSessionProcess(WebSocketSession session) throws IOException {
    // ignored by default
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
    String sessionId = session.getId();
    logger.debug(
        "{} WebSocket connection closed: {} (code {})", serverName(), sessionId, status.getCode());

    cleanupSession(sessionId, "connection closed");
  }

  @Override
  public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
    String sessionId = session.getId();
    // usually the client has disconnected
    logger.debug("{} transport error for session {}", serverName(), sessionId, exception);

    cleanupSession(sessionId, "transport error");
  }

  /**
   * Removes a session and destroys its process and temporary directory. Safe to call more than
   * once, e.g. from both the close callback and the inactivity cleanup.
   *
   * @param sessionId the session ID to clean up
   * @param reason the reason for cleanup (for logging)
   * @return {@code true} if this call stopped the session, {@code false} if it was already gone
   */
  protected boolean cleanupSession(String sessionId, String reason) {
    P serverProcess = sessions.remove(sessionId);
    lastActivity.remove(sessionId);

    if (serverProcess != null) {
      logger.debug("Cleaning up {} session {} ({})", serverName(), sessionId, reason);
      try {
        serverProcess.destroy();
      } catch (Exception e) {
        logger.warn("Stopping the {} process of session {} failed", serverName(), sessionId, e);
      } finally {
        cleanupTempDir(serverProcess.tempDir());
      }
      return true;
    } else {
      logger.debug("No {} process for session {}, already cleaned up", serverName(), sessionId);
      return false;
    }
  }

  /** Cleans up sessions that have been inactive for longer than {@link #TIMEOUT_MS}. */
  protected void cleanupInactiveSessions() {
    long now = System.currentTimeMillis();
    List<String> toCleanup = new ArrayList<>();
    for (Map.Entry<String, Long> entry : lastActivity.entrySet()) {
      if (entry.getValue() == null || (now - entry.getValue()) > inactivityTimeoutMillis()) {
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
                  logger.debug("Could not delete {}", path, e);
                }
              });
    } catch (IOException e) {
      logger.warn("Could not delete the directory {}", tempDir, e);
      return;
    }
    if (Files.exists(tempDir)) {
      logger.warn("Could not fully delete the directory {}", tempDir);
    }
  }

  /** Stops the cleanup scheduler and cleans up all remaining sessions. */
  @PreDestroy
  public void shutdown() {
    logger.info("Shutting down the {} WebSocket handler", serverName());

    cleanupScheduler.shutdown();
    try {
      if (!cleanupScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
        cleanupScheduler.shutdownNow();
      }
    } catch (InterruptedException e) {
      cleanupScheduler.shutdownNow();
      Thread.currentThread().interrupt();
    }

    logger.debug("Cleaning up {} remaining {} sessions", sessions.size(), serverName());

    List<String> remainingSessions = new ArrayList<>(sessions.keySet());
    for (String sessionId : remainingSessions) {
      cleanupSession(sessionId, "application shutdown");
    }
  }

  /**
   * Returns the VSUM the client was authorized for during the handshake. The value is set by the
   * handshake interceptor after checking that the user is a member; the {@code vsumId} in the URL
   * is never trusted here.
   *
   * @param session the WebSocket session
   * @return the authorized VSUM ID, or {@code null} if the client did not ask for a project
   */
  protected Long authorizedVsumId(WebSocketSession session) {
    return session.getAttributes().get(VSUM_ID_ATTRIBUTE) instanceof Long vsumId ? vsumId : null;
  }
}
