package tools.vitruv.methodologist.lsp;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.vitruv.methodologist.vsum.service.ReactionsLspWorkspaceService;

/** Websocket handler for LSP communication with automatic session cleanup. */
@Component
public class LspWebSocketHandler
    extends AbstractLspWebSocketHandler<LspWebSocketHandler.LspServerProcess> {

  private final ReactionsLspWorkspaceService workspaceService;

  @Value("${reactions.ide.jar.path}")
  private Resource jarResource;

  /**
   * Constructs a new LspWebSocketHandler with the service that prepares the session workspaces.
   *
   * @param workspaceService the service writing the metamodels and their Java API into a workspace
   */
  public LspWebSocketHandler(ReactionsLspWorkspaceService workspaceService) {
    this.workspaceService = workspaceService;
  }

  /** Loads JarPath from properties File. */
  private String getJarPath() throws IOException {
    try (var in = jarResource.getInputStream()) {
      Path tempFile = Files.createTempFile("reactions-ide-", ".jar");
      Files.copy(in, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
      return tempFile.toAbsolutePath().toString();
    }
  }

  @Override
  protected String serverName() {
    return "LSP";
  }

  @Override
  protected LspLaunch prepareLaunch(WebSocketSession session) throws IOException {
    String sessionId = session.getId();
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

    String jarPath = getJarPath();
    ReactionsLspWorkspaceService.Workspace workspace =
        workspaceService.prepare(sessionDir, vsumId, Path.of(jarPath));
    return new LspLaunch(
        sessionDir,
        workspace.projectDir(),
        workspace.launchCommand(javaExecutable(), Path.of(jarPath)));
  }

  @Override
  protected LspServerProcess createProcess(
      WebSocketSession session,
      Process process,
      BufferedWriter writer,
      BufferedReader reader,
      Path sessionDir) {
    return new LspServerProcess(session, process, writer, reader, sessionDir);
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

  private Long extractProjectId(WebSocketSession session) {
    try {
      Long projectId = extractQueryParam(session, "vsumId");
      if (projectId != null) {
        logger.debug("Extracted vsumId from query parameter: {}", projectId);
        return projectId;
      }

      Object projectIdAttr = session.getAttributes().get("vsumId");
      if (projectIdAttr != null) {
        return Long.parseLong(projectIdAttr.toString());
      }

      logger.debug("No vsumId found in WebSocket session (this is optional)");
      return null;

    } catch (Exception e) {
      logger.warn("Ignoring an invalid vsumId of LSP session {}", session.getId());
      return null;
    }
  }

  class LspServerProcess implements LspSessionProcess {
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
          logger.debug(
              "Unexpected line after the Content-Length header from LSP for session {}",
              session.getId());
        }

        char[] content = new char[contentLength];
        int read = reader.read(content, 0, contentLength);

        String message = new String(content, 0, read);
        session.sendMessage(new TextMessage(message));

        return true;
      } catch (NumberFormatException e) {
        logger.warn("Invalid Content-Length header from LSP for session {}", session.getId());
        return true;
      } catch (IOException e) {
        logger.debug("Could not relay an LSP message to session {}", session.getId(), e);
        return false;
      }
    }

    @Override
    public Path tempDir() {
      return tempDir;
    }

    @Override
    public void readFromLsp() {
      try {
        String line;
        while ((line = reader.readLine()) != null) {
          if (line.startsWith("Content-Length:") && !handleContentLengthLine(line)) {
            break;
          }
        }
      } catch (IOException e) {
        logger.debug("LSP output closed for session {}", session.getId(), e);
      } finally {
        logger.debug("LSP reader thread terminated for session: {}", session.getId());
      }
    }

    @Override
    public void sendToLsp(String jsonMessage) throws IOException {
      String lspMessage =
          "Content-Length: "
              + jsonMessage.getBytes(StandardCharsets.UTF_8).length
              + "\r\n\r\n"
              + jsonMessage;
      writer.write(lspMessage);
      writer.flush();
    }

    @Override
    public void destroy() {
      try {
        writer.close();
      } catch (IOException e) {
        logger.debug("Could not close the LSP input for session {}", session.getId(), e);
      }

      // Kill process
      process.destroy();

      // CRITICAL: Wait for process to actually exit (especially important on Windows). A process
      // that has exited already, e.g. after a crash, needs no waiting.
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
        logger.warn("Interrupted while waiting for the LSP process of session {}", session.getId());
        process.destroyForcibly();
      }
    }
  }
}
