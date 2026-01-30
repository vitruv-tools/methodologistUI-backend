package tools.vitruv.methodologist.vsum.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import tools.vitruv.methodologist.config.LspWebSocketHandler;
import tools.vitruv.methodologist.general.model.FileStorage;
import tools.vitruv.methodologist.vsum.model.MetaModel;

@ExtendWith(SpringExtension.class)
@TestPropertySource(
    properties = {
      "reactions.ide.jar.path=src/test/resources/lsp/tools.vitruv.dsls.reactions.ide.jar"
    })

/**
 * Unit tests for {@link LspWebSocketHandler}.
 *
 * <p>Tests WebSocket connection lifecycle, user authentication extraction, and LSP server process
 * management. Uses Mockito to mock WebSocketSession and MetaModelService dependencies.
 */
class LspWebSocketHandlerTest {

  private MetaModelService metaModelService;
  private LspWebSocketHandler handler;
  private WebSocketSession session;

  @Value("${reactions.ide.jar.path}")
  private String testJarPath; // <-- Spring injiziert das

  /**
   * Sets up test fixtures before each test.
   *
   * <p>Initializes mocked MetaModelService and WebSocketSession, and injects the service into the
   * handler using reflection.
   */
  @BeforeEach
  void setUp() {
    metaModelService = mock(MetaModelService.class);

    // Nutze die injizierte Property
    Resource resource = new FileSystemResource(testJarPath);

    handler = new LspWebSocketHandler(metaModelService);
    ReflectionTestUtils.setField(handler, "jarResource", resource);
    setField(handler, "metaModelService", metaModelService);

    session = mock(WebSocketSession.class);
    when(session.getId()).thenReturn("test-session-123");
    when(session.getAttributes()).thenReturn(new HashMap<>());
    when(session.getHandshakeHeaders()).thenReturn(new HttpHeaders());
  }

  /**
   * Verifies that a WebSocket connection with valid userId creates an LSP server process.
   *
   * <p>Expects the handler to extract userId and vsumId from query parameters, retrieve accessible
   * metamodels, and keep the session open.
   */
  @Test
  void afterConnectionEstablished_withValidUserId_createsLspProcess() throws Exception {
    URI uri = new URI("ws://localhost/lsp?userId=42&vsumId=1");
    when(session.getUri()).thenReturn(uri);

    MetaModel metamodel = createMetaModel(1L, "test.ecore", "test content".getBytes());
    when(metaModelService.findAccessibleByProject(1L)).thenReturn(List.of(metamodel));

    handler.afterConnectionEstablished(session);

    verify(metaModelService).findAccessibleByProject(1L);
    verify(session, never()).close(any(CloseStatus.class));
  }

  /**
   * Verifies that userId can be extracted from URL query parameters.
   *
   * <p>Tests the primary authentication mechanism where userId is passed as a query parameter in
   * the WebSocket connection URL.
   */
  @Test
  void afterConnectionEstablished_extractsUserIdFromQueryParam() throws Exception {
    URI uri = new URI("ws://localhost/lsp?userId=99&vsumId=5");
    when(session.getUri()).thenReturn(uri);
    when(metaModelService.findAccessibleByProject(5L)).thenReturn(Collections.emptyList());

    handler.afterConnectionEstablished(session);

    verify(metaModelService).findAccessibleByProject(5L);
    verify(session, never()).close(any(CloseStatus.class));
  }

  /**
   * Verifies that userId can be extracted from JWT token's 'sub' claim.
   *
   * <p>Tests the fallback authentication mechanism when userId is not in query parameters but
   * available in the JWT token principal.
   */
  @Test
  void afterConnectionEstablished_extractsUserIdFromJwtToken() throws Exception {
    URI uri = new URI("ws://localhost/lsp?vsumId=3");
    when(session.getUri()).thenReturn(uri);

    Jwt jwt = mock(Jwt.class);
    when(jwt.getClaim("sub")).thenReturn("77");
    JwtAuthenticationToken jwtToken = new JwtAuthenticationToken(jwt);
    when(session.getPrincipal()).thenReturn(jwtToken);

    when(metaModelService.findAccessibleByProject(3L)).thenReturn(Collections.emptyList());

    handler.afterConnectionEstablished(session);

    verify(metaModelService).findAccessibleByProject(3L);
    verify(session, never()).close(any(CloseStatus.class));
  }

  /**
   * Verifies that userId can be extracted from WebSocket session attributes.
   *
   * <p>Tests the secondary fallback authentication mechanism when userId is stored in session
   * attributes by the WebSocket handshake interceptor.
   */
  @Test
  void afterConnectionEstablished_extractsUserIdFromSessionAttributes() throws Exception {
    URI uri = new URI("ws://localhost/lsp?vsumId=2");
    when(session.getUri()).thenReturn(uri);

    Map<String, Object> attributes = new HashMap<>();
    attributes.put("userId", "88");
    when(session.getAttributes()).thenReturn(attributes);

    when(metaModelService.findAccessibleByProject(2L)).thenReturn(Collections.emptyList());

    handler.afterConnectionEstablished(session);

    verify(metaModelService).findAccessibleByProject(2L);
    verify(session, never()).close(any(CloseStatus.class));
  }

  /**
   * Verifies that metamodel files are retrieved when establishing connection.
   *
   * <p>Tests that the handler correctly fetches all accessible metamodels for the given project and
   * prepares them for the LSP server to use.
   */
  @Test
  void afterConnectionEstablished_createsMetamodelFiles() throws Exception {
    URI uri = new URI("ws://localhost/lsp?userId=1&vsumId=10");
    when(session.getUri()).thenReturn(uri);

    MetaModel mm1 = createMetaModel(1L, "model1.ecore", "content1".getBytes());
    MetaModel mm2 = createMetaModel(2L, "model2.ecore", "content2".getBytes());

    when(metaModelService.findAccessibleByProject(10L)).thenReturn(List.of(mm1, mm2));

    handler.afterConnectionEstablished(session);

    verify(metaModelService).findAccessibleByProject(10L);
  }

  /**
   * Creates a test MetaModel with mocked FileStorage containing ecore file data.
   *
   * @param id the metamodel ID
   * @param filename the ecore filename
   * @param content the file content as byte array
   * @return a MetaModel instance with mocked FileStorage
   */
  private MetaModel createMetaModel(Long id, String filename, byte[] content) {
    FileStorage ecoreFile = mock(FileStorage.class);
    when(ecoreFile.getFilename()).thenReturn(filename);
    when(ecoreFile.getData()).thenReturn(content);

    MetaModel mm = new MetaModel();
    mm.setId(id);
    mm.setEcoreFile(ecoreFile);

    return mm;
  }

  /**
   * Uses reflection to inject a mocked dependency into a private field.
   *
   * <p>Required because the handler uses @Autowired which doesn't work in unit tests without Spring
   * context.
   *
   * @param target the object to inject into
   * @param fieldName the name of the field to set
   * @param value the value to inject
   * @throws RuntimeException if field access fails
   */
  private void setField(Object target, String fieldName, Object value) {
    try {
      java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(target, value);
    } catch (Exception e) {
      throw new RuntimeException("Failed to set field: " + fieldName, e);
    }
  }
}
