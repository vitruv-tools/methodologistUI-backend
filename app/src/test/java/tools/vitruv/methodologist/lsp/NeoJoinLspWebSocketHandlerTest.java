package tools.vitruv.methodologist.lsp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.adapter.NativeWebSocketSession;
import tools.vitruv.methodologist.exception.LspProcessException;
import tools.vitruv.methodologist.vsum.service.VsumMetaModelService;
import tools.vitruv.methodologist.vsum.service.VsumMetaModelService.EcoreFile;

/** Unit tests for {@link NeoJoinLspWebSocketHandler}. */
class NeoJoinLspWebSocketHandlerTest {

  private static final int MAX_PROCESSES = 2;
  private static final long IDLE_TIMEOUT_SECONDS = 1800;
  private static final Path WORKSPACE = Path.of("/sessions/neojoin-lsp-1/workspace");

  @TempDir Path tempDir;

  private VsumMetaModelService vsumMetaModelService;
  private NeoJoinLspWebSocketHandler handler;
  private WebSocketSession session;

  @BeforeEach
  void setUp() {
    vsumMetaModelService = mock(VsumMetaModelService.class);
    handler = newHandler(MAX_PROCESSES, "512m");
    // A native session, so the idle timeout can be observed.
    session = mock(NativeWebSocketSession.class);
    when(session.getId()).thenReturn("neojoin-test-session");
  }

  @Test
  void afterConnectionEstablished_withoutAuthorizedVsum_closesWithBadData() throws Exception {
    when(session.getUri()).thenReturn(URI.create("ws://localhost/neojoin-lsp"));

    handler.afterConnectionEstablished(session);

    verify(session).close(CloseStatus.BAD_DATA);
    verifyNoInteractions(vsumMetaModelService);
    assertThat(processSlots().availablePermits()).isEqualTo(MAX_PROCESSES);
  }

  @Test
  void afterConnectionEstablished_ignoresVsumIdInTheUrl_withoutAuthorizedVsum() throws Exception {
    // Membership is checked by the handshake interceptor, which stores the checked ID; the
    // handler never trusts the URL.
    when(session.getUri()).thenReturn(URI.create("ws://localhost/neojoin-lsp?vsumId=5"));

    handler.afterConnectionEstablished(session);

    verify(session).close(CloseStatus.BAD_DATA);
    verifyNoInteractions(vsumMetaModelService);
  }

  @Test
  void afterConnectionEstablished_whenJarIsMissing_closesWithServerErrorAndCleansUp()
      throws Exception {
    givenMemberSession();
    ReflectionTestUtils.setField(
        handler, "jarResource", new FileSystemResource(tempDir.resolve("missing.jar")));
    final Set<Path> before = neoJoinTempDirectories();

    handler.afterConnectionEstablished(session);

    verify(session).close(CloseStatus.SERVER_ERROR);
    verify(session, never()).sendMessage(any());
    assertThat(neoJoinTempDirectories()).isEqualTo(before);
    assertThat(processSlots().availablePermits()).isEqualTo(MAX_PROCESSES);
  }

  @Test
  void afterConnectionEstablished_sendsWorkspaceReady_andCleansUpIfClientAlreadyLeft()
      throws Exception {
    givenMemberSession();
    givenFakeJar();
    // The mocked session reports closed, as if the client disconnected during startup.
    when(session.isOpen()).thenReturn(false);
    final Set<Path> before = neoJoinTempDirectories();

    handler.afterConnectionEstablished(session);

    ArgumentCaptor<TextMessage> message = ArgumentCaptor.forClass(TextMessage.class);
    verify(session).sendMessage(message.capture());
    assertThat(message.getValue().getPayload())
        .startsWith("{\"type\":\"workspaceReady\",\"rootUri\":\"file:")
        .endsWith("/workspace/\"}");
    verify(session, never()).close(any());
    assertThat(sessions()).isEmpty();
    assertThat(neoJoinTempDirectories()).isEqualTo(before);
    assertThat(processSlots().availablePermits()).isEqualTo(MAX_PROCESSES);
  }

  @Test
  void afterConnectionEstablished_registersSessionBeforeSendingWorkspaceReady() throws Exception {
    givenMemberSession();
    givenFakeJar();
    when(session.isOpen()).thenReturn(false);
    AtomicBoolean registered = new AtomicBoolean();
    doAnswer(
            invocation -> {
              registered.set(sessions().containsKey(session.getId()));
              return null;
            })
        .when(session)
        .sendMessage(any());

    handler.afterConnectionEstablished(session);

    assertThat(registered).isTrue();
    assertThat(sessions()).isEmpty();
  }

  @Test
  void afterConnectionEstablished_whenWorkspaceReadyFails_closesWithServerErrorAndCleansUp()
      throws Exception {
    givenMemberSession();
    givenFakeJar();
    when(session.isOpen()).thenReturn(true);
    doThrow(new IOException("connection reset")).when(session).sendMessage(any());
    final Set<Path> before = neoJoinTempDirectories();

    handler.afterConnectionEstablished(session);

    verify(session).close(CloseStatus.SERVER_ERROR);
    assertThat(sessions()).isEmpty();
    assertThat(neoJoinTempDirectories()).isEqualTo(before);
  }

  @Test
  void afterConnectionEstablished_whenServerStopsUnexpectedly_releasesWithoutCloseCallback()
      throws Exception {
    givenMemberSession();
    // The fake jar makes the started JVM exit immediately, as if the server crashed.
    givenFakeJar();
    when(session.isOpen()).thenReturn(true);
    final Set<Path> before = neoJoinTempDirectories();

    handler.afterConnectionEstablished(session);

    // Cleanup happens before the session is closed, without afterConnectionClosed being called.
    verify(session, timeout(TimeUnit.SECONDS.toMillis(30))).close(CloseStatus.SERVER_ERROR);
    assertThat(sessions()).isEmpty();
    assertThat(neoJoinTempDirectories()).isEqualTo(before);
    assertThat(processSlots().availablePermits()).isEqualTo(MAX_PROCESSES);
  }

  @Test
  void terminate_whenProcessExitsGracefully_doesNotKillIt() throws Exception {
    Process process = mock(Process.class);
    when(process.waitFor(anyLong(), any())).thenReturn(true);

    assertThat(NeoJoinLspWebSocketHandler.terminate(process)).isTrue();

    verify(process).destroy();
    verify(process, never()).destroyForcibly();
  }

  @Test
  void terminate_whenProcessIgnoresDestroy_killsItAndWaitsForExit() throws Exception {
    Process process = mock(Process.class);
    when(process.waitFor(anyLong(), any())).thenReturn(false, true);

    assertThat(NeoJoinLspWebSocketHandler.terminate(process)).isTrue();

    verify(process).destroyForcibly();
    verify(process, times(2)).waitFor(anyLong(), any());
  }

  @Test
  void terminate_whenInterrupted_stillKillsAndWaitsAndRestoresInterruptFlag() throws Exception {
    Process process = mock(Process.class);
    when(process.waitFor(anyLong(), any()))
        .thenThrow(new InterruptedException())
        .thenThrow(new InterruptedException())
        .thenReturn(true);

    boolean terminated = NeoJoinLspWebSocketHandler.terminate(process);
    // Thread.interrupted() also clears the flag again for the following tests.
    boolean interruptFlagRestored = Thread.interrupted();

    assertThat(terminated).isTrue();
    assertThat(interruptFlagRestored).isTrue();
    verify(process).destroyForcibly();
    verify(process, times(3)).waitFor(anyLong(), any());
  }

  @Test
  void stop_whenProcessDoesNotTerminate_deletesDirectoryOnlyAfterExit() throws Exception {
    Path directory = Files.createDirectory(tempDir.resolve("session"));
    Files.writeString(directory.resolve("file.txt"), "x");
    Process process = mock(Process.class);
    CompletableFuture<Process> exit = new CompletableFuture<>();
    when(process.waitFor(anyLong(), any())).thenReturn(false);
    when(process.onExit()).thenReturn(exit);

    CompletableFuture<Void> stopped = NeoJoinLspWebSocketHandler.stop(process, directory);

    verify(process).destroyForcibly();
    assertThat(directory).exists();
    assertThat(stopped).isNotDone();
    exit.complete(process);
    assertThat(directory).doesNotExist();
    assertThat(stopped).isDone();
  }

  @Test
  void stop_whenProcessTerminates_deletesDirectory() throws Exception {
    Path directory = Files.createDirectory(tempDir.resolve("session"));
    Process process = mock(Process.class);
    when(process.waitFor(anyLong(), any())).thenReturn(true);

    NeoJoinLspWebSocketHandler.stop(process, directory);

    assertThat(directory).doesNotExist();
    verify(process, never()).onExit();
  }

  @Test
  void constructor_rejectsInvalidLimits() {
    assertThatThrownBy(() -> newHandler(0, "512m")).isInstanceOf(IllegalArgumentException.class);
    for (String heap : List.of("", "0m", "512 m", "512m -Dx=y", "-Xmx1g")) {
      assertThatThrownBy(() -> newHandler(1, heap)).isInstanceOf(IllegalArgumentException.class);
    }
    assertThatThrownBy(() -> new NeoJoinLspWebSocketHandler(vsumMetaModelService, 1, 0, "512m"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void neoJoinCommand_limitsTheHeapOfTheLanguageServer() {
    List<String> command =
        newHandler(1, "256m").neoJoinCommand(Path.of("ls.jar"), Path.of("ecore"));

    assertThat(command)
        .containsSubsequence("-Xmx256m", "-jar", "ls.jar", "--meta-model-path", "ecore");
  }

  @Test
  void afterConnectionEstablished_whenProcessLimitIsReached_closesWithServiceOverload()
      throws Exception {
    givenMemberSession();
    givenFakeJar();
    processSlots().acquire(MAX_PROCESSES);

    handler.afterConnectionEstablished(session);

    verify(session).close(CloseStatus.SERVICE_OVERLOAD);
    verify(vsumMetaModelService, never()).findEcoreFiles(anyLong());
    assertThat(sessions()).isEmpty();
  }

  @Test
  void afterConnectionEstablished_setsIdleTimeoutOnTheNativeSession() throws Exception {
    givenMemberSession();
    givenFakeJar();
    when(session.isOpen()).thenReturn(false);
    jakarta.websocket.Session nativeSession = mock(jakarta.websocket.Session.class);
    when(((NativeWebSocketSession) session).getNativeSession()).thenReturn(nativeSession);

    handler.afterConnectionEstablished(session);

    verify(nativeSession).setMaxIdleTimeout(TimeUnit.SECONDS.toMillis(IDLE_TIMEOUT_SECONDS));
  }

  @Test
  void restrictToWorkspace_pinsInitializeToTheSessionWorkspace() throws Exception {
    String initialize =
        """
        {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"processId":null,
         "rootUri":"file:///etc/","rootPath":"/etc",
         "workspaceFolders":[{"uri":"file:///home/","name":"home"}],"capabilities":{}}}""";

    JsonNode params =
        json(NeoJoinLspWebSocketHandler.restrictToWorkspace(initialize, WORKSPACE).orElseThrow())
            .get("params");

    String workspaceUri = WORKSPACE.toUri().toString();
    assertThat(params.get("rootUri").asText()).isEqualTo(workspaceUri);
    assertThat(params.get("rootPath").asText()).isEqualTo(WORKSPACE.toString());
    assertThat(params.get("workspaceFolders")).hasSize(1);
    assertThat(params.get("workspaceFolders").get(0).get("uri").asText()).isEqualTo(workspaceUri);
    assertThat(params.has("capabilities")).isTrue();
  }

  @Test
  void restrictToWorkspace_forwardsOtherMessagesUnchanged() throws Exception {
    String didOpen =
        """
        {"jsonrpc":"2.0","method":"textDocument/didOpen","params":{"textDocument":
         {"uri":"file:///w/q.nj","languageId":"neojoin","version":1,"text":"äöü \\"x\\""}}}""";
    String response = "{\"jsonrpc\":\"2.0\",\"id\":3,\"result\":null}";

    assertThat(
            json(NeoJoinLspWebSocketHandler.restrictToWorkspace(didOpen, WORKSPACE).orElseThrow()))
        .isEqualTo(json(didOpen));
    assertThat(
            json(NeoJoinLspWebSocketHandler.restrictToWorkspace(response, WORKSPACE).orElseThrow()))
        .isEqualTo(json(response));
  }

  @Test
  void restrictToWorkspace_dropsWorkspaceFolderChanges() {
    String change =
        """
        {"jsonrpc":"2.0","method":"workspace/didChangeWorkspaceFolders","params":{"event":
         {"added":[{"uri":"file:///","name":"root"}],"removed":[]}}}""";

    assertThat(NeoJoinLspWebSocketHandler.restrictToWorkspace(change, WORKSPACE)).isEmpty();
  }

  @Test
  void restrictToWorkspace_rejectsMessagesThatCannotBeChecked() {
    for (String message :
        List.of(
            "",
            "not json",
            "[{\"method\":\"initialize\",\"params\":{}}]",
            "{\"method\":\"initialize\",\"method\":\"x\",\"params\":{}}",
            "{\"method\":\"initialize\",\"params\":{}} {\"id\":2}",
            "{\"method\":42}",
            "{\"method\":\"initialize\"}",
            "{\"method\":\"initialize\",\"params\":[]}")) {
      assertThatThrownBy(() -> NeoJoinLspWebSocketHandler.restrictToWorkspace(message, WORKSPACE))
          .as(message)
          .isInstanceOf(LspProcessException.class);
    }
  }

  @Test
  void handleTextMessage_withoutRunningServer_closesWithServerError() throws Exception {
    handler.handleTextMessage(session, new TextMessage("{}"));

    verify(session).close(CloseStatus.SERVER_ERROR);
  }

  @Test
  void closeTransportErrorAndShutdown_withoutRunningServer_doNothing() throws Exception {
    handler.afterConnectionClosed(session, CloseStatus.NORMAL);
    handler.handleTransportError(session, new IOException("connection reset"));
    handler.shutdown();

    verifyNoInteractions(vsumMetaModelService);
  }

  @Test
  void writeEcoreFiles_keepsEqualFilenamesApart_andReplacesUnsafeNames() throws Exception {
    NeoJoinLspWebSocketHandler.writeEcoreFiles(
        tempDir,
        List.of(
            new EcoreFile("model.ecore", new byte[] {1}),
            new EcoreFile("model.ecore", new byte[] {2}),
            new EcoreFile("../escape.ecore", new byte[] {3}),
            new EcoreFile(null, new byte[] {4}),
            new EcoreFile("MODEL.ecore", new byte[] {5})));

    assertThat(Files.readAllBytes(tempDir.resolve("model.ecore"))).containsExactly(1);
    assertThat(Files.readAllBytes(tempDir.resolve("1/model.ecore"))).containsExactly(2);
    assertThat(Files.readAllBytes(tempDir.resolve("metamodel.ecore"))).containsExactly(3);
    assertThat(Files.readAllBytes(tempDir.resolve("3/metamodel.ecore"))).containsExactly(4);
    assertThat(Files.readAllBytes(tempDir.resolve("4/MODEL.ecore"))).containsExactly(5);
    assertThat(tempDir.resolveSibling("escape.ecore")).doesNotExist();
  }

  @Test
  void writeEcoreFiles_placesDistinctFilesSideBySide_soRelativeReferencesResolve()
      throws Exception {
    NeoJoinLspWebSocketHandler.writeEcoreFiles(
        tempDir,
        List.of(
            new EcoreFile("lib.ecore", new byte[] {1}),
            new EcoreFile("base.ecore", new byte[] {2})));

    // lib.ecore refers to "base.ecore#//Named", which EMF resolves next to lib.ecore.
    assertThat(tempDir.resolve("lib.ecore").resolveSibling("base.ecore")).exists();
  }

  @Test
  void readContentLength_readsLengthAndLeavesBodyUnread() throws Exception {
    InputStream input =
        stream("Content-Length: 2\r\nContent-Type: application/vscode-jsonrpc\r\n\r\n{}");

    assertThat(NeoJoinLspWebSocketHandler.readContentLength(input)).isEqualTo(2);
    assertThat(new String(input.readAllBytes(), StandardCharsets.US_ASCII)).isEqualTo("{}");
  }

  @Test
  void readContentLength_atEndOfStream_returnsMinusOne() throws Exception {
    assertThat(NeoJoinLspWebSocketHandler.readContentLength(stream(""))).isEqualTo(-1);
  }

  @Test
  void readContentLength_rejectsMalformedHeaders() {
    assertThatThrownBy(() -> NeoJoinLspWebSocketHandler.readContentLength(stream("Content-Len")))
        .isInstanceOf(EOFException.class);
    for (String header :
        List.of(
            "Content-Type: x\r\n\r\n",
            "Content-Length: abc\r\n\r\n",
            "Content-Length: 0\r\n\r\n",
            "Content-Length: 999999999\r\n\r\n",
            "Content-Length: 1\r\nContent-Length: 1\r\n\r\n",
            "X: " + "a".repeat(9000) + "\r\n\r\n")) {
      assertThatThrownBy(() -> NeoJoinLspWebSocketHandler.readContentLength(stream(header)))
          .isInstanceOf(IOException.class);
    }
  }

  private void givenMemberSession() {
    when(session.getUri()).thenReturn(URI.create("ws://localhost/neojoin-lsp?vsumId=5"));
    // As stored by the handshake interceptor after it checked the membership.
    when(session.getAttributes())
        .thenReturn(new HashMap<>(Map.of(AbstractLspWebSocketHandler.VSUM_ID_ATTRIBUTE, 5L)));
    when(vsumMetaModelService.findEcoreFiles(5L))
        .thenReturn(List.of(new EcoreFile("model.ecore", new byte[] {1})));
  }

  private NeoJoinLspWebSocketHandler newHandler(int maxProcesses, String maxHeap) {
    return new NeoJoinLspWebSocketHandler(
        vsumMetaModelService, maxProcesses, IDLE_TIMEOUT_SECONDS, maxHeap);
  }

  private Semaphore processSlots() {
    return (Semaphore) ReflectionTestUtils.getField(handler, "processSlots");
  }

  private static JsonNode json(String content) throws IOException {
    return new ObjectMapper().readTree(content);
  }

  private void givenFakeJar() throws IOException {
    Path fakeJar = Files.writeString(tempDir.resolve("fake.jar"), "not-a-real-jar");
    ReflectionTestUtils.setField(handler, "jarResource", new FileSystemResource(fakeJar));
  }

  @SuppressWarnings("unchecked") // the field's declared type is Map<String, NeoJoinSession>
  private Map<String, ?> sessions() {
    return (Map<String, ?>) ReflectionTestUtils.getField(handler, "sessions");
  }

  private static InputStream stream(String content) {
    return new ByteArrayInputStream(content.getBytes(StandardCharsets.US_ASCII));
  }

  private static Set<Path> neoJoinTempDirectories() throws IOException {
    try (Stream<Path> paths = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
      return paths
          .filter(path -> path.getFileName().toString().startsWith("neojoin-lsp-"))
          .collect(Collectors.toSet());
    }
  }
}
