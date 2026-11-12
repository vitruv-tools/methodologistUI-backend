package tools.vitruv.methodologist.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.vitruv.methodologist.vsum.service.VsumMetaModelService;
import tools.vitruv.methodologist.vsum.service.VsumMetaModelService.EcoreFile;
import tools.vitruv.methodologist.vsum.service.VsumUserService;

/**
 * Runs {@link NeoJoinLspWebSocketHandler} against the bundled NeoJoin language server.
 *
 * <p>The VSUM holds two metamodels where {@code lib.ecore} extends a class of {@code base.ecore}
 * through a relative reference, and the client tries to point the server at a directory outside its
 * workspace.
 */
class NeoJoinLspServerTest {

  private static final String EMAIL = "member@example.com";
  private static final long RESPONSE_TIMEOUT_SECONDS = 60;

  private static final String BASE_ECORE =
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <ecore:EPackage xmi:version="2.0" xmlns:xmi="http://www.omg.org/XMI"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xmlns:ecore="http://www.eclipse.org/emf/2002/Ecore"
          name="base" nsURI="http://example.org/base" nsPrefix="base">
        <eClassifiers xsi:type="ecore:EClass" name="Named">
          <eStructuralFeatures xsi:type="ecore:EAttribute" name="name"
              eType="ecore:EDataType http://www.eclipse.org/emf/2002/Ecore#//EString"/>
        </eClassifiers>
      </ecore:EPackage>
      """;

  private static final String LIB_ECORE =
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <ecore:EPackage xmi:version="2.0" xmlns:xmi="http://www.omg.org/XMI"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xmlns:ecore="http://www.eclipse.org/emf/2002/Ecore"
          name="lib" nsURI="http://example.org/lib" nsPrefix="lib">
        <eClassifiers xsi:type="ecore:EClass" name="Book" eSuperTypes="base.ecore#//Named">
          <eStructuralFeatures xsi:type="ecore:EAttribute" name="title"
              eType="ecore:EDataType http://www.eclipse.org/emf/2002/Ecore#//EString"/>
        </eClassifiers>
      </ecore:EPackage>
      """;

  /** Uses {@code name}, which {@code Book} only inherits from {@code base.ecore}. */
  private static final String QUERY =
      """
      export out to "http://example.org/out"
      import "http://example.org/lib" as lib
      from lib.Book b create {
        b.title
        b.name
      }
      """;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final BlockingQueue<JsonNode> received = new LinkedBlockingQueue<>();
  private final AtomicBoolean open = new AtomicBoolean(true);

  @TempDir Path tempDir;

  private NeoJoinLspWebSocketHandler handler;
  private WebSocketSession session;

  @BeforeEach
  void setUp() throws IOException {
    VsumMetaModelService vsumMetaModelService = mock(VsumMetaModelService.class);
    VsumUserService vsumUserService = mock(VsumUserService.class);
    when(vsumUserService.isMember(5L, EMAIL)).thenReturn(true);
    when(vsumMetaModelService.findEcoreFiles(5L))
        .thenReturn(
            List.of(
                new EcoreFile("lib.ecore", LIB_ECORE.getBytes(StandardCharsets.UTF_8)),
                new EcoreFile("base.ecore", BASE_ECORE.getBytes(StandardCharsets.UTF_8))));

    handler = new NeoJoinLspWebSocketHandler(vsumMetaModelService, vsumUserService, 1, 60, "512m");
    ReflectionTestUtils.setField(
        handler, "jarResource", new ClassPathResource("lsp/tools.vitruv.neojoin.frontend.ide.jar"));

    session = mock(WebSocketSession.class);
    when(session.getId()).thenReturn("neojoin-server-test");
    when(session.getUri()).thenReturn(URI.create("ws://localhost/neojoin-lsp?vsumId=5"));
    when(session.getPrincipal()).thenReturn(authentication());
    when(session.isOpen()).thenAnswer(invocation -> open.get());
    doAnswer(
            invocation -> {
              TextMessage message = invocation.getArgument(0);
              received.add(objectMapper.readTree(message.getPayload()));
              return null;
            })
        .when(session)
        .sendMessage(any());
    doAnswer(invocation -> open.getAndSet(false)).when(session).close(any());
  }

  @AfterEach
  void tearDown() {
    open.set(false);
    handler.afterConnectionClosed(session, CloseStatus.NORMAL);
  }

  @Test
  void diagnosesQueryAgainstVsumMetamodels_andStaysInItsWorkspace() throws Exception {
    Path outside = Files.createDirectory(tempDir.resolve("outside"));
    Files.writeString(outside.resolve("secret.nj"), "not a query");
    String outsideUri = outside.toUri().toString();

    String queryUri =
        openQuery(
            Map.of(
                "rootUri", outsideUri,
                "workspaceFolders", List.of(Map.of("uri", outsideUri, "name", "outside")),
                "capabilities", Map.of()));

    // Diagnostics for other files would arrive first, as the workspace is built on "initialized".
    List<String> diagnosedUris = new ArrayList<>();
    JsonNode diagnostics;
    do {
      diagnostics = nextDiagnostics();
      diagnosedUris.add(diagnostics.path("uri").asText());
    } while (!diagnostics.path("uri").asText().equals(queryUri));

    assertThat(diagnostics.path("diagnostics")).isEmpty();
    assertThat(diagnosedUris).noneMatch(uri -> uri.contains("/outside/"));

    // Book has no feature "isbn", so the server must report it.
    send(
        Map.of(
            "jsonrpc", "2.0",
            "method", "textDocument/didChange",
            "params",
                Map.of(
                    "textDocument", Map.of("uri", queryUri, "version", 2),
                    "contentChanges", List.of(Map.of("text", QUERY.replace("b.name", "b.isbn"))))));

    JsonNode errors = nextDiagnostics().path("diagnostics");
    assertThat(errors).hasSize(1);
    assertThat(errors.get(0).path("severity").asInt()).isEqualTo(1);
    assertThat(errors.get(0).path("range").path("start").path("line").asInt()).isEqualTo(4);
  }

  @Test
  void completesClassesAndInheritedFeaturesOfVsumMetamodels() throws Exception {
    String queryUri = openQuery(Map.of("capabilities", Map.of()));

    // After "from " on line 2 and after "b." on line 3 of QUERY.
    assertThat(completionLabels(2, queryUri, 2, 5)).contains("Book", "lib.Book");
    assertThat(completionLabels(3, queryUri, 3, 4)).contains("name", "title");
  }

  @Test
  void shutdown_stopsServerWithoutReportingAnUnexpectedStop() throws Exception {
    handler.afterConnectionEstablished(session);
    Path workspace =
        Path.of(URI.create(next(message -> message.has("rootUri")).get("rootUri").asText()));

    handler.shutdown();

    assertThat(workspace.getParent()).doesNotExist();
    // The reader thread sees the server's output end, but must not treat that as a crash.
    verify(session, after(TimeUnit.SECONDS.toMillis(2)).never()).close(any());
  }

  @Test
  void clientClosingWhileServerReplies_endsSessionWithoutUncaughtException() throws Exception {
    CompletableFuture<Throwable> uncaught = new CompletableFuture<>();
    Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
    Thread.setDefaultUncaughtExceptionHandler((thread, exception) -> uncaught.complete(exception));
    try {
      handler.afterConnectionEstablished(session);
      Path sessionDir =
          Path.of(URI.create(next(message -> message.has("rootUri")).get("rootUri").asText()))
              .getParent();
      // The client leaves after the reader checked isOpen(); Tomcat then rejects the send.
      doAnswer(
              invocation -> {
                open.set(false);
                throw new IllegalStateException("The WebSocket session has been closed");
              })
          .when(session)
          .sendMessage(any());

      send(
          Map.of(
              "jsonrpc",
              "2.0",
              "id",
              1,
              "method",
              "initialize",
              "params",
              Map.of("capabilities", Map.of())));

      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(RESPONSE_TIMEOUT_SECONDS);
      while (Files.exists(sessionDir) && System.nanoTime() < deadline) {
        Thread.sleep(50);
      }
      assertThat(sessionDir).doesNotExist();
      assertThatThrownBy(() -> uncaught.get(2, TimeUnit.SECONDS))
          .isInstanceOf(TimeoutException.class);
    } finally {
      Thread.setDefaultUncaughtExceptionHandler(previous);
    }
  }

  /**
   * Starts the session, initializes the language server and opens {@link #QUERY}.
   *
   * @return the URI of the opened query
   */
  private String openQuery(Map<String, Object> initializeParams) throws Exception {
    handler.afterConnectionEstablished(session);
    final String workspaceUri = next(message -> message.has("rootUri")).get("rootUri").asText();

    send(Map.of("jsonrpc", "2.0", "id", 1, "method", "initialize", "params", initializeParams));
    next(message -> message.path("id").asInt() == 1);
    send(Map.of("jsonrpc", "2.0", "method", "initialized", "params", Map.of()));
    String queryUri = workspaceUri + "query.nj";
    send(
        Map.of(
            "jsonrpc", "2.0",
            "method", "textDocument/didOpen",
            "params",
                Map.of(
                    "textDocument",
                    Map.of("uri", queryUri, "languageId", "nj", "version", 1, "text", QUERY))));
    return queryUri;
  }

  private JsonNode nextDiagnostics() throws InterruptedException {
    return next(message ->
            "textDocument/publishDiagnostics".equals(message.path("method").asText()))
        .path("params");
  }

  private List<String> completionLabels(int id, String uri, int line, int character)
      throws Exception {
    send(
        Map.of(
            "jsonrpc",
            "2.0",
            "id",
            id,
            "method",
            "textDocument/completion",
            "params",
            Map.of(
                "textDocument", Map.of("uri", uri),
                "position", Map.of("line", line, "character", character))));
    JsonNode result = next(message -> message.path("id").asInt() == id).path("result");
    List<String> labels = new ArrayList<>();
    (result.isArray() ? result : result.path("items"))
        .forEach(item -> labels.add(item.path("label").asText()));
    return labels;
  }

  private void send(Object message) throws IOException {
    handler.handleTextMessage(session, new TextMessage(objectMapper.writeValueAsString(message)));
  }

  private JsonNode next(Predicate<JsonNode> condition) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(RESPONSE_TIMEOUT_SECONDS);
    while (System.nanoTime() < deadline) {
      JsonNode message = received.poll(1, TimeUnit.SECONDS);
      if (message != null && condition.test(message)) {
        return message;
      }
    }
    return fail("No matching message from the NeoJoin language server");
  }

  private static KeycloakAuthentication authentication() {
    Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").claim("email", EMAIL).build();
    return new KeycloakAuthentication(jwt, List.of());
  }
}
