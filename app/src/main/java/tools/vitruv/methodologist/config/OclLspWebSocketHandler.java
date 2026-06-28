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
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

/**
 * WebSocket handler for VitruvOCL LSP connections. Sets up a temp workspace with the project's
 * ecore metamodels and the .ocl file, then bridges WebSocket messages to/from the VitruvOCL
 * language-server.jar.
 */
@Component
public class OclLspWebSocketHandler extends TextWebSocketHandler {

  private static final Logger logger = LoggerFactory.getLogger(OclLspWebSocketHandler.class);
  private static final long TIMEOUT_MS = 10L * 60 * 1000;
  private static final long CLEANUP_INTERVAL_SECONDS = 60;

  private final ConcurrentHashMap<String, OclLspProcess> sessions = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Long> lastActivity = new ConcurrentHashMap<>();
  private final ScheduledExecutorService cleanupScheduler = Executors.newScheduledThreadPool(1);
  private final MetaModelService metaModelService;

  /**
   * Private base directory owned exclusively by this process. All temp files and session dirs are
   * created beneath this path, so no content ever lands directly in a publicly writable directory.
   */
  private final Path appTempBase;

  @Value("${vitruvocl.lsp.jar.path}")
  private Resource jarResource;

  /**
   * Constructs a new {@code OclLspWebSocketHandler}.
   *
   * @param metaModelService service for accessing metamodel data
   * @throws IOException if the private base temp directory cannot be created
   */
  public OclLspWebSocketHandler(MetaModelService metaModelService) throws IOException {
    this.metaModelService = metaModelService;
    Path vitruvoclHome = Path.of(System.getProperty("user.home")).resolve(".vitruvocl");
    Files.createDirectories(vitruvoclHome);
    this.appTempBase = vitruvoclHome.resolve("app-" + UUID.randomUUID());
    Files.createDirectories(this.appTempBase);
    cleanupScheduler.scheduleAtFixedRate(
        this::cleanupInactiveSessions,
        CLEANUP_INTERVAL_SECONDS,
        CLEANUP_INTERVAL_SECONDS,
        TimeUnit.SECONDS);
  }

  /**
   * Creates a private subdirectory under {@code parent} with a unique name. On POSIX systems {@code
   * rwx------} permissions are applied after creation; on non-POSIX systems the security relies on
   * {@code parent} being a non-publicly-writable location (e.g. under {@code user.home}).
   */
  private Path createPrivateDirectory(Path parent, String prefix) throws IOException {
    Path dir = parent.resolve(prefix + UUID.randomUUID());
    Files.createDirectories(dir);
    try {
      Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwx------"));
    } catch (UnsupportedOperationException e) {
      // Non-POSIX system (e.g. Windows): parent under user.home is already private
    }
    return dir;
  }

  /**
   * Creates a private file inside {@code parent} with a unique name. On POSIX systems {@code
   * rw-------} permissions are applied after creation; on non-POSIX systems the security relies on
   * {@code parent} being a non-publicly-writable location.
   */
  private Path createPrivateFile(Path parent, String prefix, String suffix) throws IOException {
    Path file = parent.resolve(prefix + UUID.randomUUID() + suffix);
    Files.createFile(file);
    try {
      Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
    } catch (UnsupportedOperationException e) {
      // Non-POSIX system (e.g. Windows): parent under user.home is already private
    }
    return file;
  }

  private String getJarPath() throws IOException {
    try (var in = jarResource.getInputStream()) {
      // Place the JAR copy inside the private base directory, not in /tmp directly
      Path tempFile = createPrivateFile(appTempBase, "vitruvocl-ls-", ".jar");
      Files.copy(in, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
      return tempFile.toAbsolutePath().toString();
    }
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession session) throws Exception {
    String sessionId = session.getId();
    logger.info("🔌 OCL-LSP WebSocket connected: {}", sessionId);
    lastActivity.put(sessionId, System.currentTimeMillis());

    Long vsumId = extractQueryParam(session, "vsumId");

    // Session dir lives inside the private app base — never directly in /tmp
    Path sessionDir = createPrivateDirectory(appTempBase, "ocl-lsp-" + sessionId + "-");
    Path userProject = sessionDir.resolve("OclProject");
    Path ecoreDir = userProject.resolve("ecore");
    Files.createDirectories(ecoreDir);

    // Write metamodel ecore files so the LSP can resolve types
    if (vsumId != null) {
      List<MetaModel> metamodels = metaModelService.findAccessibleByProject(vsumId);
      for (MetaModel mm : metamodels) {
        byte[] ecoreData = mm.getEcoreFile().getData();
        String fileName = mm.getEcoreFile().getFilename();
        Files.write(ecoreDir.resolve(fileName), ecoreData);
      }
    }

    String jarPath = getJarPath();
    String javaHome = System.getProperty("java.home");
    String javaExecutable = javaHome + File.separator + "bin" + File.separator + "java";

    ProcessBuilder pb = new ProcessBuilder(javaExecutable, "-jar", jarPath);
    pb.directory(userProject.toFile());
    pb.redirectErrorStream(true);

    Process process = pb.start();

    new Thread(
            () -> {
              try {
                int code = process.waitFor();
                logger.error("OCL-LSP process exited for session {} with code {}", sessionId, code);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            })
        .start();

    BufferedWriter writer =
        new BufferedWriter(
            new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
    BufferedReader reader =
        new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));

    OclLspProcess lspProcess = new OclLspProcess(session, process, writer, reader, sessionDir);
    sessions.put(sessionId, lspProcess);

    new Thread(lspProcess::readFromLsp).start();

    // Notify client that workspace is ready
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
              } catch (Exception e) {
                logger.error("💥 Failed to send workspaceReady for OCL-LSP: {}", e.getMessage());
              }
            })
        .start();
  }

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
    String sessionId = session.getId();
    lastActivity.put(sessionId, System.currentTimeMillis());
    OclLspProcess proc = sessions.get(sessionId);
    if (proc != null) {
      try {
        proc.sendToLsp(message.getPayload());
      } catch (IOException e) {
        logger.error("Failed to forward message to OCL-LSP for session {}", sessionId, e);
      }
    }
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
    cleanupSession(session.getId(), "closed: " + status.getReason());
  }

  @Override
  public void handleTransportError(WebSocketSession session, Throwable ex) throws Exception {
    cleanupSession(session.getId(), "transport error");
  }

  private void cleanupSession(String sessionId, String reason) {
    OclLspProcess proc = sessions.remove(sessionId);
    lastActivity.remove(sessionId);
    if (proc != null) {
      logger.info("🧹 OCL-LSP cleanup: {} — {}", sessionId, reason);
      try {
        proc.destroy();
        cleanupTempDir(proc.tempDir);
      } catch (Exception e) {
        logger.error("❌ Error during OCL-LSP cleanup: {}", e.getMessage());
      }
    }
  }

  private void cleanupInactiveSessions() {
    long now = System.currentTimeMillis();
    List<String> toCleanup = new ArrayList<>();
    for (Map.Entry<String, Long> entry : lastActivity.entrySet()) {
      if (entry.getValue() == null || (now - entry.getValue()) > TIMEOUT_MS) {
        toCleanup.add(entry.getKey());
      }
    }
    toCleanup.forEach(id -> cleanupSession(id, "timeout"));
  }

  private void cleanupTempDir(Path tempDir) {
    if (tempDir == null || !Files.exists(tempDir)) {
      return;
    }
    try (Stream<Path> paths = Files.walk(tempDir)) {
      paths
          .sorted(Comparator.reverseOrder())
          .forEach(
              p -> {
                try {
                  Files.delete(p);
                } catch (IOException e) {
                  /* ignore */
                }
              });
    } catch (IOException e) {
      logger.warn("⚠️ Failed to clean OCL-LSP temp dir {}: {}", tempDir, e.getMessage());
    }
  }

  /**
   * Shuts down the cleanup scheduler, destroys all active OCL LSP sessions, and removes the private
   * application base directory.
   */
  @PreDestroy
  public void shutdown() {
    cleanupScheduler.shutdown();
    new ArrayList<>(sessions.keySet()).forEach(id -> cleanupSession(id, "shutdown"));
    cleanupTempDir(appTempBase);
  }

  private Long extractQueryParam(WebSocketSession session, String param) {
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
    } catch (Exception e) {
      logger.warn("Could not extract {} from OCL-LSP WebSocket URI", param);
    }
    return null;
  }

  private class OclLspProcess {
    final WebSocketSession session;
    final Process process;
    final BufferedWriter writer;
    final BufferedReader reader;
    final Path tempDir;

    OclLspProcess(WebSocketSession s, Process p, BufferedWriter w, BufferedReader r, Path d) {
      session = s;
      process = p;
      writer = w;
      reader = r;
      tempDir = d;
    }

    void readFromLsp() {
      try {
        String line;
        while ((line = reader.readLine()) != null) {
          if (line.startsWith("Content-Length:")) {
            processLspMessage(line);
          }
        }
      } catch (IOException e) {
        logger.debug("OCL-LSP reader closed for session {}", session.getId());
      }
    }

    private void processLspMessage(String headerLine) {
      try {
        int len = Integer.parseInt(headerLine.split(":")[1].trim());
        var separator = reader.readLine(); // empty line between header and body
        if (separator == null) {
          return;
        }
        char[] buf = new char[len];
        int read = reader.read(buf, 0, len);
        session.sendMessage(new TextMessage(new String(buf, 0, read)));
      } catch (Exception e) {
        logger.error("OCL-LSP read error for {}: {}", session.getId(), e.getMessage());
      }
    }

    void sendToLsp(String json) throws IOException {
      String msg =
          "Content-Length: " + json.getBytes(StandardCharsets.UTF_8).length + "\r\n\r\n" + json;
      writer.write(msg);
      writer.flush();
    }

    void destroy() {
      try {
        writer.close();
      } catch (IOException e) {
        /* ignore */
      }
      process.destroy();
      try {
        if (!process.waitFor(5, TimeUnit.SECONDS)) {
          process.destroyForcibly();
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        process.destroyForcibly();
      }
    }
  }
}
