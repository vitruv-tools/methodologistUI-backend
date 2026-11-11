package tools.vitruv.methodologist.lsp;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.adapter.NativeWebSocketSession;
import tools.vitruv.methodologist.exception.LspProcessException;
import tools.vitruv.methodologist.vsum.service.VsumMetaModelService;
import tools.vitruv.methodologist.vsum.service.VsumMetaModelService.EcoreFile;

/**
 * WebSocket handler for NeoJoin LSP connections.
 *
 * <p>The handshake is authenticated and the VSUM membership checked before this handler is reached,
 * see {@code LspVsumAccessInterceptor}; the handler works only with the VSUM that was authorized
 * there. For each connection it writes the VSUM's metamodels into a private temporary directory,
 * starts the NeoJoin language server on them and bridges LSP messages between the WebSocket and the
 * process's standard streams.
 *
 * <p>On top of the shared lifecycle it limits the number of running language servers and the heap
 * of each, stops idle connections, and checks every client message before it reaches the server.
 */
@Slf4j
@Component
public class NeoJoinLspWebSocketHandler
    extends AbstractLspWebSocketHandler<NeoJoinLspWebSocketHandler.NeoJoinProcess> {

  private static final int MAX_HEADER_BYTES = 8 * 1024;
  private static final int MAX_MESSAGE_BYTES = 4 * 1024 * 1024;
  private static final long STOP_TIMEOUT_SECONDS = 3;
  private static final String WORKSPACE_DIRECTORY = "workspace";
  private static final Pattern HEAP_SIZE = Pattern.compile("[1-9]\\d*[kKmMgG]?");
  private static final String INITIALIZE = "initialize";
  private static final String DID_CHANGE_WORKSPACE_FOLDERS = "workspace/didChangeWorkspaceFolders";
  private static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper()
          .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
          .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
  private static final Pattern SAFE_ECORE_FILENAME = Pattern.compile("[\\w.-]+\\.ecore");
  private static final String CONTENT_LENGTH = "Content-Length:";

  private final VsumMetaModelService vsumMetaModelService;
  private final Semaphore processSlots;
  private final int maxProcesses;
  private final long idleTimeoutMillis;
  private final String maxHeap;

  @Value("${neojoin.ide.jar.path:classpath:lsp/tools.vitruv.neojoin.frontend.ide.jar}")
  private Resource jarResource;

  /**
   * Constructs a new {@code NeoJoinLspWebSocketHandler}.
   *
   * @param vsumMetaModelService service providing the VSUM's Ecore files
   * @param maxProcesses maximum number of language servers running at the same time
   * @param idleTimeoutSeconds time without traffic after which a connection is closed
   * @param maxHeap maximum heap size of each language server JVM, e.g. {@code 512m}
   */
  public NeoJoinLspWebSocketHandler(
      VsumMetaModelService vsumMetaModelService,
      @Value("${neojoin.lsp.max-processes:10}") int maxProcesses,
      @Value("${neojoin.lsp.idle-timeout-seconds:1800}") long idleTimeoutSeconds,
      @Value("${neojoin.lsp.max-heap:512m}") String maxHeap) {
    if (maxProcesses < 1) {
      throw new IllegalArgumentException("neojoin.lsp.max-processes must be positive");
    }
    if (idleTimeoutSeconds < 1) {
      throw new IllegalArgumentException("neojoin.lsp.idle-timeout-seconds must be positive");
    }
    if (maxHeap == null || !HEAP_SIZE.matcher(maxHeap).matches()) {
      throw new IllegalArgumentException("neojoin.lsp.max-heap must be a JVM size such as 512m");
    }
    this.vsumMetaModelService = vsumMetaModelService;
    this.processSlots = new Semaphore(maxProcesses);
    this.maxProcesses = maxProcesses;
    this.idleTimeoutMillis = TimeUnit.SECONDS.toMillis(idleTimeoutSeconds);
    this.maxHeap = maxHeap;
  }

  @Override
  protected String serverName() {
    return "NeoJoin LSP";
  }

  /**
   * Accepts a connection only for a VSUM authorized during the handshake and while a process slot
   * is free; the slot is taken here and given back when the language server has stopped.
   */
  @Override
  protected Optional<CloseStatus> rejectConnection(WebSocketSession session) {
    if (authorizedVsumId(session) == null) {
      return Optional.of(CloseStatus.BAD_DATA);
    }
    if (!processSlots.tryAcquire()) {
      log.warn(
          "Rejected NeoJoin LSP session {}: limit of {} language servers reached",
          session.getId(),
          maxProcesses);
      return Optional.of(CloseStatus.SERVICE_OVERLOAD);
    }
    return Optional.empty();
  }

  @Override
  protected void launchFailed(WebSocketSession session) {
    processSlots.release();
  }

  /** The error output is discarded: it may contain the user's query or metamodel content. */
  @Override
  protected void configureProcess(ProcessBuilder builder) {
    builder.redirectError(ProcessBuilder.Redirect.DISCARD);
  }

  @Override
  protected void sessionRegistered(WebSocketSession session) {
    applyIdleTimeout(session);
  }

  @Override
  protected boolean announceWorkspaceBeforeReading() {
    return true;
  }

  @Override
  protected long inactivityTimeoutMillis() {
    return idleTimeoutMillis;
  }

  @Override
  protected void noSessionProcess(WebSocketSession session) throws IOException {
    session.close(CloseStatus.SERVER_ERROR);
  }

  /**
   * Prepares the session directory: the VSUM's metamodels, an empty workspace and the language
   * server jar. If a step fails, everything created here is removed.
   */
  @Override
  protected LspLaunch prepareLaunch(WebSocketSession session) throws IOException {
    Long vsumId = authorizedVsumId(session);
    Path sessionDir = null;
    try {
      sessionDir = createSessionDirectory();
      Path ecoreDir = Files.createDirectory(sessionDir.resolve("ecore"));
      Path workspaceDir = Files.createDirectory(sessionDir.resolve(WORKSPACE_DIRECTORY));
      writeEcoreFiles(ecoreDir, vsumMetaModelService.findEcoreFiles(vsumId));
      Path jarPath = sessionDir.resolve("neojoin-ide.jar");
      try (InputStream input = jarResource.getInputStream()) {
        Files.copy(input, jarPath);
      }
      return new LspLaunch(sessionDir, workspaceDir, neoJoinCommand(jarPath, ecoreDir));
    } catch (IOException | RuntimeException e) {
      deleteDirectory(sessionDir);
      throw e;
    }
  }

  @Override
  protected NeoJoinProcess createProcess(
      WebSocketSession session,
      Process process,
      BufferedWriter writer,
      BufferedReader reader,
      Path sessionDir) {
    return new NeoJoinProcess(session, process, sessionDir);
  }

  /**
   * Lets the WebSocket container close connections without traffic, so a client that disappears
   * without closing (e.g. a dropped network) does not keep its language server running.
   */
  private void applyIdleTimeout(WebSocketSession session) {
    if (session instanceof NativeWebSocketSession nativeSession
        && nativeSession.getNativeSession() instanceof jakarta.websocket.Session webSocketSession) {
      webSocketSession.setMaxIdleTimeout(idleTimeoutMillis);
    }
  }

  /**
   * Validates a client message and pins it to the session workspace: {@code initialize} gets the
   * workspace as its only root, and workspace folder changes are dropped. The message is forwarded
   * in re-serialized form, so the language server parses exactly what was checked here.
   *
   * @return the message to forward, or empty if it is dropped
   * @throws LspProcessException if the message is not a single, well-formed JSON-RPC object
   */
  static Optional<String> restrictToWorkspace(String payload, Path workspaceDir) {
    JsonNode message;
    try {
      message = OBJECT_MAPPER.readTree(payload);
    } catch (JsonProcessingException e) {
      // Not chained: Jackson's message quotes the payload.
      throw new LspProcessException("Malformed LSP message");
    }
    if (!(message instanceof ObjectNode object)) {
      throw new LspProcessException("LSP message is not a JSON object");
    }
    JsonNode method = object.get("method");
    if (method != null && !method.isTextual()) {
      throw new LspProcessException("Invalid LSP method");
    }
    String methodName = method == null ? null : method.asText();
    if (DID_CHANGE_WORKSPACE_FOLDERS.equals(methodName)) {
      return Optional.empty();
    }
    if (INITIALIZE.equals(methodName)) {
      if (!(object.get("params") instanceof ObjectNode params)) {
        throw new LspProcessException("Invalid initialize parameters");
      }
      String rootUri = workspaceDir.toUri().toString();
      params.put("rootUri", rootUri);
      params.put("rootPath", workspaceDir.toString());
      params
          .putArray("workspaceFolders")
          .addObject()
          .put("uri", rootUri)
          .put("name", WORKSPACE_DIRECTORY);
    }
    try {
      return Optional.of(OBJECT_MAPPER.writeValueAsString(object));
    } catch (JsonProcessingException e) {
      throw new LspProcessException("Could not serialize LSP message", e);
    }
  }

  private static Path createSessionDirectory() throws IOException {
    try {
      return Files.createTempDirectory(
          "neojoin-lsp-",
          PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
    } catch (UnsupportedOperationException e) {
      // Windows: fall back to the default temp directory security
      return Files.createTempDirectory("neojoin-lsp-");
    }
  }

  /**
   * Writes the Ecore files side by side, so relative references between metamodels (e.g. {@code
   * eSuperTypes="other.ecore#//Type"}) resolve. A file whose name is already taken goes into its
   * own numbered subdirectory instead; NeoJoin searches the directory recursively. Unsafe names are
   * replaced.
   */
  static void writeEcoreFiles(Path ecoreDir, List<EcoreFile> ecoreFiles) throws IOException {
    // Compared case-insensitively, as names differing only in case clash on some file systems.
    Set<String> usedFilenames = new HashSet<>();
    for (int i = 0; i < ecoreFiles.size(); i++) {
      EcoreFile ecoreFile = ecoreFiles.get(i);
      String filename = ecoreFile.filename();
      if (filename == null || !SAFE_ECORE_FILENAME.matcher(filename).matches()) {
        filename = "metamodel.ecore";
      }
      Path directory =
          usedFilenames.add(filename.toLowerCase(Locale.ROOT))
              ? ecoreDir
              : Files.createDirectory(ecoreDir.resolve(String.valueOf(i)));
      Files.write(directory.resolve(filename), ecoreFile.data(), StandardOpenOption.CREATE_NEW);
    }
  }

  List<String> neoJoinCommand(Path jarPath, Path ecoreDir) {
    String javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java").toString();
    return List.of(
        javaExecutable,
        "-Xmx" + maxHeap,
        "-jar",
        jarPath.toString(),
        "--meta-model-path",
        ecoreDir.toString());
  }

  /**
   * Reads one LSP header block and returns its {@code Content-Length}.
   *
   * @param input the language server's output
   * @return the length of the following message body, or {@code -1} at end of stream
   * @throws IOException if the header is malformed, too large or truncated
   */
  static int readContentLength(InputStream input) throws IOException {
    ByteArrayOutputStream header = new ByteArrayOutputStream();
    int last = 0;
    int current;
    while ((current = input.read()) != -1) {
      header.write(current);
      if (header.size() > MAX_HEADER_BYTES) {
        throw new IOException("LSP header too large");
      }
      // Keeps the last four bytes, so the header ends at "\r\n\r\n".
      last = (last << 8) | current;
      if (last == 0x0D0A0D0A) {
        break;
      }
    }
    if (current == -1) {
      if (header.size() == 0) {
        return -1;
      }
      throw new EOFException("Incomplete LSP header");
    }

    int contentLength = -1;
    for (String line : header.toString(StandardCharsets.US_ASCII).split("\r\n")) {
      if (line.regionMatches(true, 0, CONTENT_LENGTH, 0, CONTENT_LENGTH.length())) {
        if (contentLength != -1) {
          throw new IOException("Duplicate Content-Length");
        }
        try {
          contentLength = Integer.parseInt(line.substring(CONTENT_LENGTH.length()).trim());
        } catch (NumberFormatException e) {
          throw new IOException("Invalid Content-Length", e);
        }
      }
    }
    if (contentLength <= 0 || contentLength > MAX_MESSAGE_BYTES) {
      throw new IOException("Invalid LSP message size");
    }
    return contentLength;
  }

  /**
   * Terminates the process and deletes the directory once the process has exited. If the process
   * does not exit in time, deletion is deferred until it does.
   *
   * @return completes when the process has exited and the directory is deleted
   */
  static CompletableFuture<Void> stop(Process process, Path directory) {
    if (process == null || terminate(process)) {
      deleteDirectory(directory);
      return CompletableFuture.completedFuture(null);
    }
    log.warn("NeoJoin LSP process {} did not terminate; deferring cleanup", process.pid());
    return process.onExit().thenRun(() -> deleteDirectory(directory));
  }

  /**
   * Asks the process to stop, then kills it if it does not exit in time. An interruption skips the
   * graceful wait but not the forced one; the interrupt flag is restored afterwards.
   *
   * @return {@code true} if the process has exited
   */
  static boolean terminate(Process process) {
    boolean interrupted = false;
    try {
      process.destroy();
      try {
        if (process.waitFor(STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
          return true;
        }
      } catch (InterruptedException e) {
        interrupted = true;
      }
      process.destroyForcibly();
      while (true) {
        try {
          return process.waitFor(STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
          // The flag is now cleared, so the next wait blocks again.
          interrupted = true;
        }
      }
    } finally {
      if (interrupted) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private static void deleteDirectory(Path directory) {
    if (directory == null) {
      return;
    }
    try {
      FileSystemUtils.deleteRecursively(directory);
    } catch (IOException e) {
      log.warn("Failed to delete NeoJoin LSP directory {}", directory, e);
    }
  }

  /**
   * A NeoJoin language server bound to one WebSocket session. Unlike the other servers it is framed
   * byte-exact and its stopping, including deleting the session directory, is deferred until the
   * process has really exited, so it manages its directory itself.
   */
  final class NeoJoinProcess implements LspSessionProcess {
    private final WebSocketSession session;
    private final Process process;
    private final Path directory;

    NeoJoinProcess(WebSocketSession session, Process process, Path directory) {
      this.session = session;
      this.process = process;
      this.directory = directory;
    }

    @Override
    public void sendToLsp(String message) throws IOException {
      Optional<String> forwarded =
          restrictToWorkspace(message, directory.resolve(WORKSPACE_DIRECTORY));
      if (forwarded.isEmpty()) {
        return;
      }

      byte[] payload = forwarded.get().getBytes(StandardCharsets.UTF_8);
      byte[] header =
          (CONTENT_LENGTH + " " + payload.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII);

      OutputStream output = process.getOutputStream();
      synchronized (output) {
        output.write(header);
        output.write(payload);
        output.flush();
      }
    }

    @Override
    public void readFromLsp() {
      try (InputStream input = process.getInputStream()) {
        int contentLength;
        while (session.isOpen() && (contentLength = readContentLength(input)) != -1) {
          byte[] payload = input.readNBytes(contentLength);
          if (payload.length != contentLength) {
            throw new EOFException("Incomplete LSP message");
          }
          session.sendMessage(new TextMessage(new String(payload, StandardCharsets.UTF_8)));
        }
      } catch (IOException | IllegalStateException e) {
        // IllegalStateException: Tomcat refuses to send once the client has closed the session.
        log.debug("NeoJoin LSP output closed for session {}", session.getId(), e);
      } finally {
        boolean clientConnected = session.isOpen();
        // Released here as well, so cleanup does not depend on a WebSocket close callback. If
        // another caller stopped the server first (client close, shutdown), its output ended for
        // that reason.
        if (cleanupSession(session.getId(), "language server output ended") && clientConnected) {
          log.warn(
              "NeoJoin LSP for session {} stopped unexpectedly (exit code {})",
              session.getId(),
              process.isAlive() ? "none" : process.exitValue());
          closeQuietly(session);
        }
      }
    }

    /** Stops the process and deletes the directory, then frees the process slot. */
    @Override
    public void destroy() {
      stop(process, directory).whenComplete((ignored, error) -> processSlots.release());
    }

    /** {@code null}: {@link #destroy()} deletes the directory once the process has exited. */
    @Override
    public Path tempDir() {
      return null;
    }
  }
}
