package tools.vitruv.methodologist.config;

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
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.vitruv.methodologist.vsum.model.MetaModel;
import tools.vitruv.methodologist.vsum.service.MetaModelService;

/** Websocket handler for LSP communication with automatic session cleanup. */
@Component
public class LspWebSocketHandler extends TextWebSocketHandler {

  @Value("${reactions.ide.jar.path}")
  private Resource jarResource;

  private static final Logger logger = LoggerFactory.getLogger(LspWebSocketHandler.class);
  private final ConcurrentHashMap<String, LspServerProcess> sessions = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Long> lastActivity = new ConcurrentHashMap<>();
  private final ScheduledExecutorService cleanupScheduler = Executors.newScheduledThreadPool(1);
  private final MetaModelService metaModelService;

  // Configuration
  private static final long TIMEOUT_MS = 5 * 60 * 1000; // 5 minutes inactivity timeout
  private static final long CLEANUP_INTERVAL_SECONDS = 60; // Check every 60 seconds

  /**
   * Constructs a new LspWebSocketHandler with the required metamodel service.
   *
   * @param metaModelService the service for metamodel operations
   */
  public LspWebSocketHandler(MetaModelService metaModelService) {
    this.metaModelService = metaModelService;

    // Start periodic cleanup task
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

  /** Loads JarPath from properties File. */
  private String getJarPath() throws IOException {
    return jarResource.getFile().getAbsolutePath();
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession session) throws Exception {
    String sessionId = session.getId();
    logger.info("🔌 WebSocket connection established: {}", sessionId);

    // Track activity
    lastActivity.put(sessionId, System.currentTimeMillis());

    Long vsumId = extractProjectId(session);

    FileAttribute<Set<PosixFilePermission>> attr =
        PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"));
    Path sessionDir;
    try {
      sessionDir = Files.createTempDirectory("lsp-session-" + sessionId, attr);
    } catch (UnsupportedOperationException e) {
      // Windows: Fall back to default temp directory security
      sessionDir = Files.createTempDirectory("lsp-session-" + sessionId);
      logger.debug("POSIX permissions not supported, using system defaults");
    }

    Path userProject = sessionDir.resolve("UserProject");
    Path modelDir = userProject.resolve("model");
    Files.createDirectories(modelDir);
    List<MetaModel> metamodels = metaModelService.findAccessibleByProject(vsumId);

    for (MetaModel mm : metamodels) {
      byte[] ecoreData = mm.getEcoreFile().getData();
      String fileName = mm.getEcoreFile().getFilename();
      Path ecoreFile = modelDir.resolve(fileName);
      Files.write(ecoreFile, ecoreData);
    }

    String jarPath = getJarPath();

    String javaHome = System.getProperty("java.home");
    String javaExecutable = javaHome + File.separator + "bin" + File.separator + "java";
    ProcessBuilder pb = new ProcessBuilder(javaExecutable, "-jar", jarPath, "-log", "-trace");
    pb.directory(userProject.toFile());
    pb.redirectErrorStream(true);

    Process process = pb.start();

    BufferedWriter writer =
        new BufferedWriter(
            new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
    BufferedReader reader =
        new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));

    LspServerProcess lspProcess =
        new LspServerProcess(session, process, writer, reader, sessionDir);
    sessions.put(sessionId, lspProcess);

    new Thread(lspProcess::readFromLsp).start();

    new Thread(
            () -> {
              try {
                Thread.sleep(500);

                String rootUriMessage =
                    String.format(
                        "{\"type\":\"workspaceReady\",\"rootUri\":\"%s\"}",
                        userProject.toUri().toString());
                session.sendMessage(new TextMessage(rootUriMessage));
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error(
                    "💥 Thread interrupted while sending workspaceReady: {}", e.getMessage());
              } catch (Exception e) {
                logger.error("💥 Failed to send workspaceReady: {}", e.getMessage());
              }
            })
        .start();
  }

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
    String sessionId = session.getId();

    // Update activity timestamp
    lastActivity.put(sessionId, System.currentTimeMillis());

    LspServerProcess serverProcess = sessions.get(sessionId);
    if (serverProcess != null) {
      try {
        serverProcess.sendToLsp(message.getPayload());
      } catch (IOException e) {
        logger.error("Failed to send message to LSP for session {}", sessionId, e);
      }
    }
  }

  /**
   * Centralized cleanup method to prevent race conditions. Can be called from both
   * afterConnectionClosed and cleanupInactiveSessions.
   *
   * @param sessionId the session ID to clean up
   * @param reason the reason for cleanup (for logging)
   */
  private void cleanupSession(String sessionId, String reason) {
    LspServerProcess serverProcess = sessions.remove(sessionId);
    lastActivity.remove(sessionId);

    if (serverProcess != null) {
      logger.info("🧹 Cleaning up session: {} - Reason: {}", sessionId, reason);
      try {
        serverProcess.destroy();
        cleanupTempDir(serverProcess.tempDir);
        logger.info("✅ Session cleanup completed for: {}", sessionId);
      } catch (Exception e) {
        logger.error("❌ Error during session cleanup: {}", e.getMessage());
      }
    } else {
      logger.debug("⚠️ No LSP process found for session: {} (already cleaned up)", sessionId);
    }
  }

  /**
   * Periodic cleanup task that removes inactive sessions. Runs every CLEANUP_INTERVAL_SECONDS and
   * removes sessions inactive for > TIMEOUT_MS.
   */
  private void cleanupInactiveSessions() {
    long now = System.currentTimeMillis();
    int cleaned = 0;

    // Collect sessions to cleanup (avoid ConcurrentModificationException)
    List<String> toCleanup = new ArrayList<>();

    for (Map.Entry<String, Long> entry : lastActivity.entrySet()) {
      String sessionId = entry.getKey();
      Long lastActive = entry.getValue();

      if (lastActive == null || (now - lastActive) > TIMEOUT_MS) {
        long inactiveMinutes = lastActive != null ? (now - lastActive) / 60000 : -1;
        logger.info(
            "🧹 Marking session for cleanup: {} (inactive for {}min)", sessionId, inactiveMinutes);
        toCleanup.add(sessionId);
      }
    }

    // Now cleanup collected sessions
    for (String sessionId : toCleanup) {
      cleanupSession(sessionId, "timeout");
      cleaned++;
    }

    if (cleaned > 0) {
      logger.info("🧹 Cleanup completed: {} sessions removed", cleaned);
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
   * Recursively deletes a temporary directory.
   *
   * @param tempDir the directory to delete
   */
  private void cleanupTempDir(Path tempDir) {
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

  /**
   * Shutdown hook - called when the application is shutting down. Cleans up all remaining sessions
   * and stops the cleanup scheduler.
   */
  @PreDestroy
  public void shutdown() {
    logger.info("🛑 Shutting down LSP WebSocket handler...");

    // Stop cleanup scheduler
    cleanupScheduler.shutdown();
    try {
      if (!cleanupScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
        cleanupScheduler.shutdownNow();
      }
    } catch (InterruptedException e) {
      cleanupScheduler.shutdownNow();
      Thread.currentThread().interrupt();
    }

    // Cleanup all remaining sessions
    logger.info("🧹 Cleaning up {} remaining sessions...", sessions.size());

    // Copy keySet to avoid ConcurrentModificationException
    List<String> remainingSessions = new ArrayList<>(sessions.keySet());
    for (String sessionId : remainingSessions) {
      cleanupSession(sessionId, "application shutdown");
    }

    logger.info("✅ LSP WebSocket handler shutdown complete");
  }

  /**
   * For monitoring/debugging: Returns information about current sessions.
   *
   * @return map of session IDs to their inactivity duration
   */
  public Map<String, String> getSessionInfo() {
    Map<String, String> info = new ConcurrentHashMap<>();
    long now = System.currentTimeMillis();

    sessions.forEach(
        (sessionId, process) -> {
          Long lastActive = lastActivity.get(sessionId);
          long inactiveSeconds = lastActive != null ? (now - lastActive) / 1000 : -1;
          info.put(sessionId, String.format("inactive for %ds", inactiveSeconds));
        });

    return info;
  }

  private class LspServerProcess {
    final WebSocketSession session;
    final Process process;
    final BufferedWriter writer;
    final BufferedReader reader;
    final Path tempDir;

    LspServerProcess(
        WebSocketSession session,
        Process process,
        BufferedWriter writer,
        BufferedReader reader,
        Path tempDir) {
      this.session = session;
      this.process = process;
      this.writer = writer;
      this.reader = reader;
      this.tempDir = tempDir;
    }

    private int parseContentLength(String line) {
      return Integer.parseInt(line.split(":")[1].trim());
    }

    private boolean handleContentLengthLine(String line) {
      try {
        int contentLength = parseContentLength(line);

        String separatorLine = reader.readLine();
        if (separatorLine == null || !separatorLine.isEmpty()) {
          logger.warn(
              "Expected empty line after Content-Length header for session: {}, but got: '{}'",
              session.getId(),
              separatorLine);
        }

        char[] content = new char[contentLength];
        int read = reader.read(content, 0, contentLength);

        String message = new String(content, 0, read);
        session.sendMessage(new TextMessage(message));

        return true;
      } catch (NumberFormatException e) {
        logger.error("Invalid Content-Length header from LSP for session: {}", session.getId(), e);
        return true;
      } catch (IOException e) {
        logger.error("Failed to send LSP message to WebSocket session: {}", session.getId(), e);
        return false;
      }
    }

    void readFromLsp() {
      try {
        String line;
        while ((line = reader.readLine()) != null) {
          if (line.startsWith("Content-Length:") && !handleContentLengthLine(line)) {
            break;
          }
        }
      } catch (IOException e) {
        logger.error("LSP process stream closed for session: {}", session.getId(), e);
      } finally {
        logger.debug("LSP reader thread terminated for session: {}", session.getId());
      }
    }

    void sendToLsp(String jsonMessage) throws IOException {
      String lspMessage =
          "Content-Length: "
              + jsonMessage.getBytes(StandardCharsets.UTF_8).length
              + "\r\n\r\n"
              + jsonMessage;
      writer.write(lspMessage);
      writer.flush();
    }

    void destroy() {
      try {
        writer.close();
      } catch (IOException e) {
        logger.error("Error closing LSP writer", e);
      }

      // Kill process
      process.destroy();

      // CRITICAL: Wait for process to actually exit (especially important on Windows)
      try {
        boolean exited = process.waitFor(5, TimeUnit.SECONDS);
        if (!exited) {
          logger.warn(
              "LSP process for session {} did not exit gracefully, forcing kill", session.getId());
          process.destroyForcibly();
          process.waitFor(2, TimeUnit.SECONDS);
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        logger.warn("Interrupted while waiting for LSP process to exit");
        process.destroyForcibly();
      }
    }
  }

  private Long extractProjectId(WebSocketSession session) {
    try {
      String query = session.getUri().getQuery();
      if (query != null && query.contains("vsumId=")) {
        String projectIdStr = extractQueryParam(query, "vsumId");
        if (projectIdStr != null) {
          Long projectId = Long.parseLong(projectIdStr);
          logger.debug("Extracted vsumId from query parameter: {}", projectId);
          return projectId;
        }
      }

      Object projectIdAttr = session.getAttributes().get("vsumId");
      if (projectIdAttr != null) {
        return Long.parseLong(projectIdAttr.toString());
      }

      logger.debug("No vsumId found in WebSocket session (this is optional)");
      return null;

    } catch (Exception e) {
      logger.error("Error extracting vsumId from WebSocket session", e);
      return null;
    }
  }

  private String extractQueryParam(String query, String paramName) {
    if (query == null || paramName == null) {
      return null;
    }

    String[] params = query.split("&");
    for (String param : params) {
      String[] keyValue = param.split("=", 2);
      if (keyValue.length == 2 && keyValue[0].equals(paramName)) {
        return keyValue[1];
      }
    }
    return null;
  }
}
