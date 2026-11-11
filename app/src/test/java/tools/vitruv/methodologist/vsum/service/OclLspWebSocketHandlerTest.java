package tools.vitruv.methodologist.vsum.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tools.vitruv.methodologist.lsp.AbstractLspWebSocketHandler.VSUM_ID_ATTRIBUTE;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Stream;
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
import tools.vitruv.methodologist.general.model.FileStorage;
import tools.vitruv.methodologist.lsp.OclLspWebSocketHandler;
import tools.vitruv.methodologist.vsum.model.MetaModel;

/**
 * Unit tests for {@link OclLspWebSocketHandler}.
 *
 * <p>Tests WebSocket connection lifecycle, vsumId extraction, and OCL LSP process management.
 */
@ExtendWith(SpringExtension.class)
@TestPropertySource(
    properties = {
      "vitruvocl.lsp.jar.path=src/test/resources/lsp/tools.vitruv.dsls.reactions.ide.jar"
    })
class OclLspWebSocketHandlerTest {

  private VsumMetaModelService vsumMetaModelService;
  private OclLspWebSocketHandler handler;
  private WebSocketSession session;

  @Value("${vitruvocl.lsp.jar.path}")
  private String testJarPath;

  @BeforeEach
  void setUp() throws Exception {
    vsumMetaModelService = mock(VsumMetaModelService.class);

    handler = new OclLspWebSocketHandler(vsumMetaModelService);
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
    session.getAttributes().put(VSUM_ID_ATTRIBUTE, 1L);

    MetaModel metamodel = createMetaModel(1L, "test.ecore", "test content".getBytes());
    when(vsumMetaModelService.findAccessibleByProject(1L)).thenReturn(List.of(metamodel));

    Path fakeJar = Files.createTempFile("vitruvocl-ls-", ".jar");
    Files.write(fakeJar, "not-a-real-jar".getBytes());
    ReflectionTestUtils.setField(handler, "jarResource", new FileSystemResource(fakeJar.toFile()));

    handler.afterConnectionEstablished(session);

    verify(vsumMetaModelService).findAccessibleByProject(1L);
    verify(session, never()).close(any(CloseStatus.class));
  }

  @Test
  void afterConnectionEstablished_ignoresVsumIdInTheUrl_withoutAuthorizedVsum() throws Exception {
    when(session.getUri()).thenReturn(new URI("ws://localhost/ocl-lsp?vsumId=1"));
    Path fakeJar = Files.createTempFile("vitruvocl-ls-", ".jar");
    Files.write(fakeJar, "not-a-real-jar".getBytes());
    ReflectionTestUtils.setField(handler, "jarResource", new FileSystemResource(fakeJar.toFile()));

    handler.afterConnectionEstablished(session);

    verify(vsumMetaModelService, never()).findAccessibleByProject(any());
  }

  @Test
  void afterConnectionEstablished_withoutVsumId_doesNotLoadMetamodels() throws Exception {
    URI uri = new URI("ws://localhost/ocl-lsp");
    when(session.getUri()).thenReturn(uri);

    Path fakeJar = Files.createTempFile("vitruvocl-ls-", ".jar");
    Files.write(fakeJar, "not-a-real-jar".getBytes());
    ReflectionTestUtils.setField(handler, "jarResource", new FileSystemResource(fakeJar.toFile()));

    handler.afterConnectionEstablished(session);

    verify(vsumMetaModelService, never()).findAccessibleByProject(any());
    verify(session, never()).close(any(CloseStatus.class));
  }

  @Test
  void afterConnectionEstablished_withMultipleMetamodels_writesAllEcoreFiles() throws Exception {
    URI uri = new URI("ws://localhost/ocl-lsp?vsumId=10");
    when(session.getUri()).thenReturn(uri);
    session.getAttributes().put(VSUM_ID_ATTRIBUTE, 10L);

    MetaModel mm1 = createMetaModel(1L, "model1.ecore", "content1".getBytes());
    MetaModel mm2 = createMetaModel(2L, "model2.ecore", "content2".getBytes());
    when(vsumMetaModelService.findAccessibleByProject(10L)).thenReturn(List.of(mm1, mm2));

    Path fakeJar = Files.createTempFile("vitruvocl-ls-", ".jar");
    Files.write(fakeJar, "not-a-real-jar".getBytes());
    ReflectionTestUtils.setField(handler, "jarResource", new FileSystemResource(fakeJar.toFile()));

    handler.afterConnectionEstablished(session);

    verify(vsumMetaModelService).findAccessibleByProject(10L);
  }

  @Test
  void afterConnectionEstablished_withPathTraversalFileName_writesOnlyInsideEcoreDir()
      throws Exception {
    URI uri = new URI("ws://localhost/ocl-lsp?vsumId=7");
    when(session.getUri()).thenReturn(uri);
    session.getAttributes().put(VSUM_ID_ATTRIBUTE, 7L);

    MetaModel evil = createMetaModel(1L, "../../../escaped.ecore", "content".getBytes());
    when(vsumMetaModelService.findAccessibleByProject(7L)).thenReturn(List.of(evil));

    Path fakeJar = Files.createTempFile("vitruvocl-ls-", ".jar");
    Files.write(fakeJar, "not-a-real-jar".getBytes());
    ReflectionTestUtils.setField(handler, "jarResource", new FileSystemResource(fakeJar.toFile()));

    handler.afterConnectionEstablished(session);

    Path appTempBase = (Path) ReflectionTestUtils.getField(handler, "appTempBase");
    try (Stream<Path> files = Files.walk(appTempBase)) {
      assertThat(files.filter(path -> path.getFileName().toString().equals("escaped.ecore")))
          .singleElement()
          .satisfies(
              path -> assertThat(path.getParent().getFileName().toString()).isEqualTo("ecore"));
    }
    handler.afterConnectionClosed(session, CloseStatus.NORMAL);
  }

  @Test
  void afterConnectionEstablished_withEmptyMetamodelList_succeeds() throws Exception {
    URI uri = new URI("ws://localhost/ocl-lsp?vsumId=5");
    when(session.getUri()).thenReturn(uri);
    session.getAttributes().put(VSUM_ID_ATTRIBUTE, 5L);

    when(vsumMetaModelService.findAccessibleByProject(5L)).thenReturn(Collections.emptyList());

    Path fakeJar = Files.createTempFile("vitruvocl-ls-", ".jar");
    Files.write(fakeJar, "not-a-real-jar".getBytes());
    ReflectionTestUtils.setField(handler, "jarResource", new FileSystemResource(fakeJar.toFile()));

    handler.afterConnectionEstablished(session);

    verify(vsumMetaModelService).findAccessibleByProject(5L);
    verify(session, never()).close(any(CloseStatus.class));
  }

  @Test
  void afterConnectionClosed_removesSession() throws Exception {
    URI uri = new URI("ws://localhost/ocl-lsp?vsumId=1");
    when(session.getUri()).thenReturn(uri);
    session.getAttributes().put(VSUM_ID_ATTRIBUTE, 1L);
    when(vsumMetaModelService.findAccessibleByProject(1L)).thenReturn(Collections.emptyList());

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

  @Test
  void shutdown_destroysSessionsAndDeletesApplicationTempBase() throws Exception {
    URI uri = new URI("ws://localhost/ocl-lsp?vsumId=1");
    when(session.getUri()).thenReturn(uri);
    session.getAttributes().put(VSUM_ID_ATTRIBUTE, 1L);
    when(vsumMetaModelService.findAccessibleByProject(1L)).thenReturn(Collections.emptyList());

    Path fakeJar = Files.createTempFile("vitruvocl-ls-", ".jar");
    Files.write(fakeJar, "not-a-real-jar".getBytes());
    ReflectionTestUtils.setField(handler, "jarResource", new FileSystemResource(fakeJar.toFile()));

    handler.afterConnectionEstablished(session);
    Path appTempBase = (Path) ReflectionTestUtils.getField(handler, "appTempBase");
    assertThat(appTempBase).isNotEmptyDirectory();

    handler.shutdown();

    assertThat(appTempBase).doesNotExist();
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
