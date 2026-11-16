package tools.vitruv.methodologist.lsp;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.vitruv.methodologist.general.SafePaths;
import tools.vitruv.methodologist.vsum.model.MetaModel;
import tools.vitruv.methodologist.vsum.service.VsumMetaModelService;

/**
 * WebSocket handler for VitruvOCL LSP connections. Sets up a temp workspace with the project's
 * ecore metamodels and the .ocl file, then bridges WebSocket messages to/from the VitruvOCL
 * language-server.jar.
 */
@Component
public class OclLspWebSocketHandler
    extends AbstractLspWebSocketHandler<OclLspWebSocketHandler.OclLspProcess> {

  private final VsumMetaModelService vsumMetaModelService;

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
   * @param vsumMetaModelService service for accessing metamodel data
   * @throws IOException if the private base temp directory cannot be created
   */
  public OclLspWebSocketHandler(VsumMetaModelService vsumMetaModelService) throws IOException {
    this.vsumMetaModelService = vsumMetaModelService;
    Path vitruvoclHome = Path.of(System.getProperty("user.home")).resolve(".vitruvocl");
    Files.createDirectories(vitruvoclHome);
    this.appTempBase = vitruvoclHome.resolve("app-" + UUID.randomUUID());
    Files.createDirectories(this.appTempBase);
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
  protected String serverName() {
    return "OCL-LSP";
  }

  @Override
  protected LspLaunch prepareLaunch(WebSocketSession session) throws IOException {
    String sessionId = session.getId();
    Long vsumId = authorizedVsumId(session);

    // Session dir lives inside the private app base — never directly in /tmp
    Path sessionDir = createPrivateDirectory(appTempBase, "ocl-lsp-" + sessionId + "-");
    Path userProject = sessionDir.resolve("OclProject");
    Path ecoreDir = userProject.resolve("ecore");
    Files.createDirectories(ecoreDir);

    // Write metamodel ecore files so the LSP can resolve types
    if (vsumId != null) {
      List<MetaModel> metamodels = vsumMetaModelService.findAccessibleByProject(vsumId);
      for (MetaModel mm : metamodels) {
        byte[] ecoreData = mm.getEcoreFile().getData();
        String fileName = mm.getEcoreFile().getFilename();
        Optional<Path> ecoreFile = SafePaths.resolveInside(ecoreDir, fileName);
        if (ecoreFile.isPresent()) {
          Files.write(ecoreFile.get(), ecoreData);
        } else {
          logger.warn("Skipping ecore file of metamodel {}: unusable file name", mm.getId());
        }
      }
    }

    String jarPath = getJarPath();
    return new LspLaunch(sessionDir, userProject, List.of(javaExecutable(), "-jar", jarPath));
  }

  @Override
  protected OclLspProcess createProcess(
      WebSocketSession session,
      Process process,
      BufferedWriter writer,
      BufferedReader reader,
      Path sessionDir) {
    return new OclLspProcess(session, process, writer, reader, sessionDir);
  }

  /**
   * Shuts down the cleanup scheduler, destroys all active OCL LSP sessions, and removes the private
   * application base directory.
   */
  @Override
  public void shutdown() {
    super.shutdown();
    cleanupTempDir(appTempBase);
  }

  class OclLspProcess implements LspSessionProcess {
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

    @Override
    public Path tempDir() {
      return tempDir;
    }

    @Override
    public void readFromLsp() {
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
      } catch (NumberFormatException e) {
        logger.warn("Invalid Content-Length header from OCL-LSP for session {}", session.getId());
      } catch (Exception e) {
        // usually the client has disconnected
        logger.debug("Could not relay an OCL-LSP message to session {}", session.getId(), e);
      }
    }

    @Override
    public void sendToLsp(String json) throws IOException {
      String msg =
          "Content-Length: " + json.getBytes(StandardCharsets.UTF_8).length + "\r\n\r\n" + json;
      writer.write(msg);
      writer.flush();
    }

    @Override
    public void destroy() {
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
