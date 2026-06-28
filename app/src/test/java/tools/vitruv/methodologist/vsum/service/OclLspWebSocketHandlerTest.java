package tools.vitruv.methodologist.vsum.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import tools.vitruv.methodologist.config.OclLspWebSocketHandler;
import tools.vitruv.methodologist.general.model.FileStorage;
import tools.vitruv.methodologist.vsum.model.MetaModel;

/**
 * Unit tests for {@link OclLspWebSocketHandler}.
 *
 * <p>Tests WebSocket connection lifecycle, vsumId extraction, and OCL LSP process management.
 */
@ExtendWith(SpringExtension.class)
@TestPropertySource(
    properties = {"vitruvocl.lsp.jar.path=src/test/resources/lsp/tools.vitruv.dsls.reactions.ide.jar"})
class OclLspWebSocketHandlerTest {

  private MetaModelService metaModelService;
  private OclLspWebSocketHandler handler;
  private WebSocketSession session;

  @Value("${vitruvocl.lsp.jar.path}")
  private String testJarPath;

  @BeforeEach
  void setUp() throws Exception {
    metaModelService = mock(MetaModelService.class);

    handler = new OclLspWebSocketHandler(metaModelService);
    ReflectionTestUtils.setField(handler, "jarResource", new FileSystemResource(testJarPath));

    session = mock(WebSocketSession.class);
    when(session.getId()).thenReturn("test-ocl-session-123");
    when(session.getAttributes()).thenReturn(new HashMap<>());
    when(session.getHandshakeHeaders()).thenReturn(new HttpHeaders());
  }

  @Test
  void afterConnectionEstablished_withVsumId_loadsMetamodels() throws Exception {
    URI uri = new URI("ws://localhost/ocl-lsp?vsumId=1");
    when(session.getUri()).thenReturn(uri);

    MetaModel metamodel = createMetaModel(1L, "test.ecore", "test content".getBytes());
    when(metaModelService.findAccessibleByProject(1L)).thenReturn(List.of(metamodel));

    Path fakeJar = Files.createTempFile("vitruvocl-ls-", ".jar");
    Files.write(fakeJar, "not-a-real-jar".getBytes());
    ReflectionTestUtils.setField(handler, "jarResource", new FileSystemResource(fakeJar.toFile()));

    handler.afterConnectionEstablished(session);

    verify(metaModelService).findAccessibleByProject(1L);
    verify(session, never()).close(any(CloseStatus.class));
  }

  @Test
  void afterConnectionEstablished_withoutVsumId_doesNotLoadMetamodels() throws Exception {
    URI uri = new URI("ws://localhost/ocl-lsp");
    when(session.getUri()).thenReturn(uri);

    Path fakeJar = Files.createTempFile("vitruvocl-ls-", ".jar");
    Files.write(fakeJar, "not-a-real-jar".getBytes());
    ReflectionTestUtils.setField(handler, "jarResource", new FileSystemResource(fakeJar.toFile()));

    handler.afterConnectionEstablished(session);

    verify(metaModelService, never()).findAccessibleByProject(any());
    verify(session, never()).close(any(CloseStatus.class));
  }

  @Test
  void afterConnectionEstablished_withMultipleMetamodels_writesAllEcoreFiles() throws Exception {
    URI uri = new URI("ws://localhost/ocl-lsp?vsumId=10");
    when(session.getUri()).thenReturn(uri);

    MetaModel mm1 = createMetaModel(1L, "model1.ecore", "content1".getBytes());
    MetaModel mm2 = createMetaModel(2L, "model2.ecore", "content2".getBytes());
    when(metaModelService.findAccessibleByProject(10L)).thenReturn(List.of(mm1, mm2));

    Path fakeJar = Files.createTempFile("vitruvocl-ls-", ".jar");
    Files.write(fakeJar, "not-a-real-jar".getBytes());
    ReflectionTestUtils.setField(handler, "jarResource", new FileSystemResource(fakeJar.toFile()));

    handler.afterConnectionEstablished(session);

    verify(metaModelService).findAccessibleByProject(10L);
  }

  @Test
  void afterConnectionEstablished_withEmptyMetamodelList_succeeds() throws Exception {
    URI uri = new URI("ws://localhost/ocl-lsp?vsumId=5");
    when(session.getUri()).thenReturn(uri);

    when(metaModelService.findAccessibleByProject(5L)).thenReturn(Collections.emptyList());

    Path fakeJar = Files.createTempFile("vitruvocl-ls-", ".jar");
    Files.write(fakeJar, "not-a-real-jar".getBytes());
    ReflectionTestUtils.setField(handler, "jarResource", new FileSystemResource(fakeJar.toFile()));

    handler.afterConnectionEstablished(session);

    verify(metaModelService).findAccessibleByProject(5L);
    verify(session, never()).close(any(CloseStatus.class));
  }

  @Test
  void afterConnectionClosed_removesSession() throws Exception {
    URI uri = new URI("ws://localhost/ocl-lsp?vsumId=1");
    when(session.getUri()).thenReturn(uri);
    when(metaModelService.findAccessibleByProject(1L)).thenReturn(Collections.emptyList());

    Path fakeJar = Files.createTempFile("vitruvocl-ls-", ".jar");
    Files.write(fakeJar, "not-a-real-jar".getBytes());
    ReflectionTestUtils.setField(handler, "jarResource", new FileSystemResource(fakeJar.toFile()));

    handler.afterConnectionEstablished(session);
    handler.afterConnectionClosed(session, CloseStatus.NORMAL);

    verify(session, never()).close(any(CloseStatus.class));
  }

  @Test
  void afterConnectionClosed_withoutPriorConnection_doesNotThrow() throws Exception {
    handler.afterConnectionClosed(session, CloseStatus.NORMAL);

    verify(session, never()).close(any(CloseStatus.class));
  }

  private MetaModel createMetaModel(Long id, String filename, byte[] content) {
    FileStorage ecoreFile = mock(FileStorage.class);
    when(ecoreFile.getFilename()).thenReturn(filename);
    when(ecoreFile.getData()).thenReturn(content);

    MetaModel mm = new MetaModel();
    mm.setId(id);
    mm.setEcoreFile(ecoreFile);

    return mm;
  }
}
