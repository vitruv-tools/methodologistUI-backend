package tools.vitruv.methodologist.config;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.vitruv.methodologist.vsum.model.MetaModel;
import tools.vitruv.methodologist.vsum.service.MetaModelService;

/** Websocket handler for LSP communication. */
@Component
public class LspWebSocketHandler extends TextWebSocketHandler {

  @Autowired private MetaModelService metaModelService;

  private static final Logger logger = LoggerFactory.getLogger(LspWebSocketHandler.class);
  private final ConcurrentHashMap<String, LspServerProcess> sessions = new ConcurrentHashMap<>();

  @Override
  public void afterConnectionEstablished(WebSocketSession session) throws Exception {
    logger.info("=== WebSocket Connection Established ===");
    logger.info("Session ID: {}", session.getId());

    // Session Attributes
    logger.info("Session Attributes:");
    session.getAttributes().forEach((key, value) -> logger.info("  {} = {}", key, value));

    // Handshake Headers
    logger.info("Handshake Headers:");
    session.getHandshakeHeaders().forEach((key, values) -> logger.info("  {} = {}", key, values));

    // Principal (User info)
    logger.info("Principal: {}", session.getPrincipal());

    // Extrahierte Werte
    Long vsumId = extractProjectId(session);
    logger.info("Extracted projectId: {}", vsumId);
    logger.info("=== End WebSocket Info ===");

    Path sessionDir = Files.createTempDirectory("lsp-session-" + session.getId());
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

    String jarPath =
        new File("src/main/resources/lsp/tools.vitruv.dsls.reactions.ide.jar").getAbsolutePath();

    ProcessBuilder pb = new ProcessBuilder("java", "-jar", jarPath, "-log", "-trace");
    pb.directory(userProject.toFile());
    pb.redirectErrorStream(true);

    Process process = pb.start();

    BufferedWriter writer =
        new BufferedWriter(
            new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
    BufferedReader reader =
        new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));

    LspServerProcess lspProcess =
        new LspServerProcess(session, process, writer, reader, sessionDir, userProject);
    sessions.put(session.getId(), lspProcess);

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
                logger.error(
                    "💥 Thread interrupted while sending workspaceReady: {}", e.getMessage());
                e.printStackTrace();
              }
            })
        .start();
  }

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
    LspServerProcess serverProcess = sessions.get(session.getId());
    if (serverProcess != null) {
      try {
        serverProcess.sendToLsp(message.getPayload());
      } catch (IOException e) {
        logger.error("Failed to send message to LSP", e);
      }
    }
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
    LspServerProcess serverProcess = sessions.remove(session.getId());
    if (serverProcess != null) {
      serverProcess.destroy();

      if (serverProcess.tempDir != null && Files.exists(serverProcess.tempDir)) {
        try (Stream<Path> paths = Files.walk(serverProcess.tempDir)) {
          paths
              .sorted(Comparator.reverseOrder())
              .forEach(
                  path -> {
                    try {
                      Files.delete(path);
                    } catch (IOException e) {
                      logger.warn("Cleanup failed: {}", path);
                    }
                  });
        } catch (IOException e) {
          logger.warn("Failed to walk directory: {}", serverProcess.tempDir, e);
        }
      }
    }
  }

  @Override
  public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
    logger.error("WebSocket transport error for session: {}", session.getId(), exception);
    LspServerProcess serverProcess = sessions.remove(session.getId());
    if (serverProcess != null) {
      serverProcess.destroy();
    }
  }

  private class LspServerProcess {
    final WebSocketSession session;
    final Process process;
    final BufferedWriter writer;
    final BufferedReader reader;
    private final Path tempDir;

    LspServerProcess(
        WebSocketSession session,
        Process process,
        BufferedWriter writer,
        BufferedReader reader,
        Path tempDir,
        Path userProject) {
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

        String separatorLine = reader.readLine(); // Skip empty line
        if (separatorLine == null || !separatorLine.isEmpty()) {
          logger.warn(
              "Expected empty line after Content-Length header for session: {}, but got: '{}'",
              session.getId(),
              separatorLine);
        }

        char[] content = new char[contentLength];
        int read = reader.read(content, 0, contentLength);

        if (read != contentLength) {
          logger.warn(
              "Expected {} bytes but read {} bytes from LSP for session: {}",
              contentLength,
              read,
              session.getId());
        }

        String message = new String(content, 0, read);
        session.sendMessage(new TextMessage(message));

        return true;
      } catch (NumberFormatException e) {
        logger.error("Invalid Content-Length header from LSP for session: {}", session.getId(), e);
        return true; // malformed message, but keep reading
      } catch (IOException e) {
        logger.error("Failed to send LSP message to WebSocket session: {}", session.getId(), e);
        return false; // WebSocket is broken → caller should stop
      }
    }

    void readFromLsp() {
      try {
        String line;
        while ((line = reader.readLine()) != null) {
          if (line.startsWith("Content-Length:")) {
            if (!handleContentLengthLine(line)) {
              break; // stop reading if the WebSocket is broken
            }
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
      process.destroy();
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
        Long projectId = Long.parseLong(projectIdAttr.toString());
        logger.debug("Extracted vsumId from session attributes: {}", projectId);
        return projectId;
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
