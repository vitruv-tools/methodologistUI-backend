package tools.vitruv.methodologist.config;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class LspWebSocketHandler extends TextWebSocketHandler {

  private static final Logger logger = LoggerFactory.getLogger(LspWebSocketHandler.class);
  private final ConcurrentHashMap<String, LspServerProcess> sessions = new ConcurrentHashMap<>();

  @Override
  public void afterConnectionEstablished(WebSocketSession session) throws Exception {
    System.out.println("🔵 WebSocket connection established: " + session.getId());

    String jarPath =
        new File("src/main/resources/lsp/tools.vitruv.dsls.reactions.ide.jar").getAbsolutePath();

    System.out.println("🚀 Starting LSP server with JAR: " + jarPath);

    ProcessBuilder pb = new ProcessBuilder("java", "-jar", jarPath);
    pb.redirectErrorStream(true);
    pb.directory(new File(System.getProperty("user.dir")));

    Process process = pb.start();
    System.out.println("✅ LSP process started, PID: " + process.pid());

    BufferedWriter writer =
        new BufferedWriter(
            new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
    BufferedReader reader =
        new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));

    LspServerProcess lspProcess = new LspServerProcess(session, process, writer, reader);
    sessions.put(session.getId(), lspProcess);

    new Thread(() -> lspProcess.readFromLsp()).start();
    System.out.println("✅ LSP reader thread started");
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
    logger.info("WebSocket connection closed: {}, status: {}", session.getId(), status);
    LspServerProcess serverProcess = sessions.remove(session.getId());
    if (serverProcess != null) {
      serverProcess.destroy();
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

    LspServerProcess(
        WebSocketSession session, Process process, BufferedWriter writer, BufferedReader reader) {
      this.session = session;
      this.process = process;
      this.writer = writer;
      this.reader = reader;
    }

    void readFromLsp() {
      try {
        System.out.println("📖 LSP reader thread started");
        String line;
        while ((line = reader.readLine()) != null) {
          System.out.println("📥 LSP line: " + line);

          if (line.startsWith("Content-Length:")) {
            int contentLength = Integer.parseInt(line.split(":")[1].trim());
            System.out.println("📏 Content-Length: " + contentLength);
            
            reader.readLine(); // Skip empty line
            
            char[] content = new char[contentLength];
            int read = reader.read(content, 0, contentLength);
            System.out.println("📖 Read " + read + " chars");
            
            String message = new String(content);
            System.out.println(
                "📤 Sending to frontend: " + message.substring(0, Math.min(200, message.length())));
            
            session.sendMessage(new TextMessage(message));
          }
        }
        System.out.println("⚠️ LSP reader loop ended");
      } catch (IOException e) {
        System.err.println("💥 LSP reader error: " + e.getMessage());
        e.printStackTrace();
      }
    }

    void sendToLsp(String jsonMessage) throws IOException {
      String lspMessage =
          "Content-Length: " + jsonMessage.getBytes(StandardCharsets.UTF_8).length + "\r\n\r\n"
              + jsonMessage;
      System.out.println(
          "📤 Sending to LSP: " + jsonMessage.substring(0, Math.min(200, jsonMessage.length())));
      writer.write(lspMessage);
      writer.flush();
      System.out.println("✅ Sent and flushed");
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
}