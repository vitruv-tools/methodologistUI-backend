package tools.vitruv.methodologist.apihandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import tools.vitruv.methodologist.exception.SetupServiceException;

class SetupServiceApiHandlerTest {

  private static final int PAYLOAD_SIZE = 10_000_000;
  private static final int TIMEOUT_SECONDS = 8;

  private MockWebServer mockWebServer;
  private SetupServiceApiHandler setupServiceApiHandler;

  private List<MultipartFile> metamodelFiles;
  private List<MultipartFile> genmodelFiles;
  private List<MultipartFile> reactionFiles;

  @BeforeEach
  void setUp() throws Exception {
    mockWebServer = new MockWebServer();
    mockWebServer.start();

    setupServiceApiHandler =
        new SetupServiceApiHandler(
            mockWebServer.url("/").toString(), PAYLOAD_SIZE, TIMEOUT_SECONDS);

    metamodelFiles =
        List.of(
            new MockMultipartFile(
                "metamodelFiles", "model.ecore", "application/octet-stream", "ecore".getBytes()));
    genmodelFiles =
        List.of(
            new MockMultipartFile(
                "genmodelFiles",
                "model.genmodel",
                "application/octet-stream",
                "genmodel".getBytes()));
    reactionFiles =
        List.of(
            new MockMultipartFile(
                "reactionFiles",
                "templateReactions.reactions",
                "application/octet-stream",
                "reactions".getBytes()));
  }

  @AfterEach
  void tearDown() throws Exception {
    mockWebServer.shutdown();
  }

  @Test
  void buildVsumZipOrThrow_returnsArtifactBytes_andSendsMultipartRequest() throws Exception {
    byte[] expected = {1, 2, 3, 4, 5};
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setHeader(HttpHeaders.CONTENT_TYPE, "application/zip")
            .setBody(new Buffer().write(expected)));

    byte[] result =
        setupServiceApiHandler.buildVsumZipOrThrow(metamodelFiles, genmodelFiles, reactionFiles);

    assertThat(result).containsExactly(expected);

    RecordedRequest recordedRequest = mockWebServer.takeRequest();
    assertThat(recordedRequest.getMethod()).isEqualTo("POST");
    assertThat(recordedRequest.getPath()).isEqualTo(SetupServiceApiHandler.BUILD_URL);
    assertThat(recordedRequest.getHeader(HttpHeaders.CONTENT_TYPE))
        .startsWith("multipart/form-data");

    String body = recordedRequest.getBody().readUtf8();
    assertThat(body).contains(SetupServiceApiHandler.METAMODEL_FILES_PART);
    assertThat(body).contains(SetupServiceApiHandler.GENMODEL_FILES_PART);
    assertThat(body).contains(SetupServiceApiHandler.REACTION_FILES_PART);
    assertThat(body).contains("model.ecore");
  }

  @Test
  void buildVsumJarOrThrow_returnsArtifactBytes_andCallsJarEndpoint() throws Exception {
    byte[] expected = {10, 20, 30};
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setHeader(HttpHeaders.CONTENT_TYPE, "application/java-archive")
            .setBody(new Buffer().write(expected)));

    byte[] result =
        setupServiceApiHandler.buildVsumJarOrThrow(metamodelFiles, genmodelFiles, reactionFiles);

    assertThat(result).containsExactly(expected);

    RecordedRequest recordedRequest = mockWebServer.takeRequest();
    assertThat(recordedRequest.getMethod()).isEqualTo("POST");
    assertThat(recordedRequest.getPath()).isEqualTo(SetupServiceApiHandler.JAR_URL);
  }

  @Test
  void buildVsumZipOrThrow_throwsSetupServiceException_onErrorStatus() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(500).setBody("boom"));

    assertThatThrownBy(
            () ->
                setupServiceApiHandler.buildVsumZipOrThrow(
                    metamodelFiles, genmodelFiles, reactionFiles))
        .isInstanceOf(SetupServiceException.class)
        .hasMessageContaining("boom");
  }

  @Test
  void buildVsumZipOrThrow_throwsSetupServiceException_onEmptyArtifact() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(200));

    assertThatThrownBy(
            () ->
                setupServiceApiHandler.buildVsumZipOrThrow(
                    metamodelFiles, genmodelFiles, reactionFiles))
        .isInstanceOf(SetupServiceException.class)
        .hasMessageContaining("empty artifact");
  }

  @Test
  void buildVsumZipOrThrow_throwsSetupServiceException_whenServiceUnreachable() throws Exception {
    mockWebServer.shutdown();

    assertThatThrownBy(
            () ->
                setupServiceApiHandler.buildVsumZipOrThrow(
                    metamodelFiles, genmodelFiles, reactionFiles))
        .isInstanceOf(SetupServiceException.class);
  }
}
