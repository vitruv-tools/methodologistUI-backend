package tools.vitruv.methodologist.apihandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import tools.vitruv.methodologist.apihandler.dto.response.GenModelInspectionResponse;
import tools.vitruv.methodologist.exception.SetupServiceException;
import tools.vitruv.methodologist.general.model.FileStorage;

class SetupServiceApiHandlerTest {

  private static final int PAYLOAD_SIZE = 10_000_000;
  private static final int TIMEOUT_SECONDS = 8;

  private MockWebServer mockWebServer;
  private SetupServiceApiHandler setupServiceApiHandler;

  private List<FileStorage> metamodelFiles;
  private List<FileStorage> reactionFiles;
  private List<FileStorage> constraintFiles;
  private List<FileStorage> viewFiles;

  @BeforeEach
  void setUp() throws Exception {
    mockWebServer = new MockWebServer();
    mockWebServer.start();

    setupServiceApiHandler =
        new SetupServiceApiHandler(
            mockWebServer.url("/").toString(), PAYLOAD_SIZE, TIMEOUT_SECONDS);

    metamodelFiles = List.of(fileStorage("model.ecore", "ecore".getBytes()));
    reactionFiles = List.of(fileStorage("templateReactions.reactions", "reactions".getBytes()));
    constraintFiles = List.of(fileStorage("rules.ocl", "context".getBytes()));
    viewFiles = List.of(fileStorage("components.nj", "view".getBytes()));
  }

  @Test
  void buildVsumJarOrThrow_reportsTheMessageOfTheSetupServiceAndItsStatus() {
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(400)
            .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
            .setBody(
                "{\"errorCode\":\"VSUM_REACTIONS_ERROR\",\"message\":\"imports 'x'\","
                    + "\"path\":\"/api/vsum/jar\",\"status\":400}"));

    assertThatThrownBy(
            () ->
                setupServiceApiHandler.buildVsumJarOrThrow(
                    metamodelFiles, reactionFiles, constraintFiles, viewFiles))
        .isInstanceOf(SetupServiceException.class)
        .hasMessage(
            "Setup-service request to '/api/vsum/jar' failed with status 400:"
                + " [VSUM_REACTIONS_ERROR] imports 'x'")
        .matches(e -> ((SetupServiceException) e).isRejectedBySetupService());
  }

  @Test
  void buildVsumZipOrThrow_isNotARejection_whenTheSetupServiceFails() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(500).setBody("boom"));

    assertThatThrownBy(
            () ->
                setupServiceApiHandler.buildVsumZipOrThrow(
                    metamodelFiles, reactionFiles, constraintFiles, viewFiles))
        .matches(e -> !((SetupServiceException) e).isRejectedBySetupService());
  }

  @Test
  void buildVsumJarOrThrow_sendsUniqueFileNamesWithTheExpectedExtension() throws Exception {
    mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("jar"));

    setupServiceApiHandler.buildVsumJarOrThrow(
        List.of(
            fileStorage("model.ecore", "a".getBytes()),
            fileStorage("Model.ecore", "b".getBytes()),
            fileStorage(null, "c".getBytes())),
        List.of(fileStorage("rules", "d".getBytes())),
        List.of(),
        List.of());

    String body = mockWebServer.takeRequest().getBody().readUtf8();
    assertThat(body)
        .contains(
            "filename=\"model.ecore\"",
            "filename=\"Model-2.ecore\"",
            "filename=\"metamodelFiles.ecore\"",
            "filename=\"rules.reactions\"");
  }

  @Test
  void describeError_fallsBackToTheRawBody() {
    assertThat(SetupServiceApiHandler.describeError("{\"message\":\"m\"}")).isEqualTo("m");
    assertThat(SetupServiceApiHandler.describeError("not json")).isEqualTo("not json");
    assertThat(SetupServiceApiHandler.describeError("")).isEqualTo("(no response body)");
  }

  private static FileStorage fileStorage(String filename, byte[] data) {
    FileStorage fileStorage = new FileStorage();
    fileStorage.setFilename(filename);
    fileStorage.setData(data);
    return fileStorage;
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
        setupServiceApiHandler.buildVsumZipOrThrow(
            metamodelFiles, reactionFiles, constraintFiles, viewFiles);

    assertThat(result).containsExactly(expected);

    RecordedRequest recordedRequest = mockWebServer.takeRequest();
    assertThat(recordedRequest.getMethod()).isEqualTo("POST");
    assertThat(recordedRequest.getPath()).isEqualTo(SetupServiceApiHandler.BUILD_URL);
    assertThat(recordedRequest.getHeader(HttpHeaders.CONTENT_TYPE))
        .startsWith("multipart/form-data");

    String body = recordedRequest.getBody().readUtf8();
    assertThat(body).contains(SetupServiceApiHandler.METAMODEL_FILES_PART);
    assertThat(body).contains(SetupServiceApiHandler.REACTION_FILES_PART);
    assertThat(body).contains(SetupServiceApiHandler.CONSTRAINT_FILES_PART);
    assertThat(body).contains(SetupServiceApiHandler.VIEW_FILES_PART);
    assertThat(body).contains("model.ecore", "rules.ocl", "components.nj");
    assertThat(body).doesNotContain("genmodelFiles");
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
        setupServiceApiHandler.buildVsumJarOrThrow(
            metamodelFiles, reactionFiles, constraintFiles, viewFiles);

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
                    metamodelFiles, reactionFiles, constraintFiles, viewFiles))
        .isInstanceOf(SetupServiceException.class)
        .hasMessageContaining("boom");
  }

  @Test
  void buildVsumZipOrThrow_throwsSetupServiceException_onEmptyArtifact() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(200));

    assertThatThrownBy(
            () ->
                setupServiceApiHandler.buildVsumZipOrThrow(
                    metamodelFiles, reactionFiles, constraintFiles, viewFiles))
        .isInstanceOf(SetupServiceException.class)
        .hasMessageContaining("empty artifact");
  }

  @Test
  void buildVsumZipOrThrow_throwsSetupServiceException_whenServiceUnreachable() throws Exception {
    mockWebServer.shutdown();

    assertThatThrownBy(
            () ->
                setupServiceApiHandler.buildVsumZipOrThrow(
                    metamodelFiles, reactionFiles, constraintFiles, viewFiles))
        .isInstanceOf(SetupServiceException.class);
  }

  @Test
  void processGenModelOrThrow_returnsProcessedBytes_andPostsToProcessEndpoint() throws Exception {
    byte[] expected = "fixed-genmodel".getBytes();
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setHeader(HttpHeaders.CONTENT_TYPE, "application/octet-stream")
            .setBody(new Buffer().write(expected)));

    byte[] result =
        setupServiceApiHandler.processGenModelOrThrow(
            fileStorage("model.genmodel", "genmodel".getBytes()));

    assertThat(result).containsExactly(expected);

    RecordedRequest recordedRequest = mockWebServer.takeRequest();
    assertThat(recordedRequest.getMethod()).isEqualTo("POST");
    assertThat(recordedRequest.getPath()).isEqualTo(SetupServiceApiHandler.PROCESS_GENMODEL_URL);
    String body = recordedRequest.getBody().readUtf8();
    assertThat(body).contains(SetupServiceApiHandler.FILE_PART);
    assertThat(body).contains("model.genmodel");
  }

  @Test
  void processGenModelOrThrow_throwsSetupServiceException_onErrorStatus() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(500).setBody("boom"));

    assertThatThrownBy(
            () ->
                setupServiceApiHandler.processGenModelOrThrow(
                    fileStorage("model.genmodel", "genmodel".getBytes())))
        .isInstanceOf(SetupServiceException.class)
        .hasMessageContaining("boom");
  }

  @Test
  void generateGenModelOrThrow_returnsGeneratedBytes_andPostsEcoreToGenerateEndpoint()
      throws Exception {
    byte[] expected = "generated-genmodel".getBytes();
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setHeader(HttpHeaders.CONTENT_TYPE, "application/octet-stream")
            .setBody(new Buffer().write(expected)));

    byte[] result =
        setupServiceApiHandler.generateGenModelOrThrow(
            fileStorage("model.ecore", "ecore".getBytes()));

    assertThat(result).containsExactly(expected);

    RecordedRequest recordedRequest = mockWebServer.takeRequest();
    assertThat(recordedRequest.getMethod()).isEqualTo("POST");
    assertThat(recordedRequest.getPath()).isEqualTo(SetupServiceApiHandler.GENERATE_GENMODEL_URL);
    String body = recordedRequest.getBody().readUtf8();
    assertThat(body).contains(SetupServiceApiHandler.FILE_PART);
    assertThat(body).contains("model.ecore");
  }

  @Test
  void generateGenModelOrThrow_throwsSetupServiceException_onUnprocessableEntity() {
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(422)
            .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
            .setBody(
                "{\"errorCode\":\"ECORE_NS_URI_MISSING\",\"message\":\"nsURI is missing\","
                    + "\"path\":\"/api/genmodel/generate\",\"status\":422}"));

    assertThatThrownBy(
            () ->
                setupServiceApiHandler.generateGenModelOrThrow(
                    fileStorage("model.ecore", "ecore".getBytes())))
        .isInstanceOf(SetupServiceException.class)
        .hasMessageContaining(SetupServiceApiHandler.GENERATE_GENMODEL_URL)
        .hasMessageContaining("nsURI is missing");
  }

  @Test
  void generateGenModelOrThrow_throwsSetupServiceException_onEmptyGenModel() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(200));

    assertThatThrownBy(
            () ->
                setupServiceApiHandler.generateGenModelOrThrow(
                    fileStorage("model.ecore", "ecore".getBytes())))
        .isInstanceOf(SetupServiceException.class)
        .hasMessageContaining("empty GenModel");
  }

  @Test
  void inspectGenModelOrThrow_returnsMessage_onSuccess() throws Exception {
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
            .setBody(
                "{\"data\":[],\"message\":\"GenModel inspected successfully, showing planned"
                    + " changes\"}"));

    GenModelInspectionResponse result =
        setupServiceApiHandler.inspectGenModelOrThrow(
            fileStorage("model.genmodel", "genmodel".getBytes()));

    assertThat(result.getMessage())
        .isEqualTo("GenModel inspected successfully, showing planned changes");

    RecordedRequest recordedRequest = mockWebServer.takeRequest();
    assertThat(recordedRequest.getMethod()).isEqualTo("POST");
    assertThat(recordedRequest.getPath()).isEqualTo(SetupServiceApiHandler.INSPECT_GENMODEL_URL);
  }

  @Test
  void inspectGenModelOrThrow_deserializesDataEntriesAsUntypedJson() {
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
            .setBody(
                "{\"data\":[{\"type\":\"RENAME\",\"path\":\"model.genmodel\"}],"
                    + "\"message\":\"GenModel inspected successfully\"}"));

    GenModelInspectionResponse result =
        setupServiceApiHandler.inspectGenModelOrThrow(
            fileStorage("model.genmodel", "genmodel".getBytes()));

    assertThat(result.getData())
        .singleElement()
        .isEqualTo(Map.of("type", "RENAME", "path", "model.genmodel"));
  }

  @Test
  void inspectGenModelOrThrow_returnsErrorMessage_onUnprocessableEntity() {
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(422)
            .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
            .setBody(
                "{\"errorCode\":\"MISSING_PLUGIN_ID\",\"message\":\"GenModel has missing/blank"
                    + " modelPluginID\",\"path\":\"/api/genmodel/inspect\",\"status\":422,"
                    + "\"timestamp\":1782314329746}"));

    GenModelInspectionResponse result =
        setupServiceApiHandler.inspectGenModelOrThrow(
            fileStorage("model.genmodel", "genmodel".getBytes()));

    assertThat(result.getErrorCode()).isEqualTo("MISSING_PLUGIN_ID");
    assertThat(result.getMessage()).contains("missing/blank modelPluginID");
    assertThat(result.getStatus()).isEqualTo(422);
  }

  @Test
  void inspectGenModelOrThrow_throwsSetupServiceException_onServerError() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(500).setBody("boom"));

    assertThatThrownBy(
            () ->
                setupServiceApiHandler.inspectGenModelOrThrow(
                    fileStorage("model.genmodel", "genmodel".getBytes())))
        .isInstanceOf(SetupServiceException.class)
        .hasMessageContaining("boom");
  }
}
