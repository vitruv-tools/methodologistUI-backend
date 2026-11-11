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
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import tools.vitruv.methodologist.apihandler.dto.response.GenModelInspectionResponse;
import tools.vitruv.methodologist.apihandler.dto.response.VsumValidationResult;
import tools.vitruv.methodologist.exception.BuildProblem;
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
  void generateVsumProjectOrThrow_isNotARejection_whenTheSetupServiceFails() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(500).setBody("boom"));

    assertThatThrownBy(
            () ->
                setupServiceApiHandler.generateVsumProjectOrThrow(
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
  void buildVsumJarOrThrow_keepsTheErrorCodeAndProblems_andResolvesTheirFiles() {
    FileStorage ecore = fileStorage(11L, "model2.ecore", "e".getBytes());
    FileStorage reactions = fileStorage(12L, "my.reactions", "r".getBytes());
    FileStorage composite = fileStorage(null, "compositeReaction1.reactions", "c".getBytes());
    mockWebServer.enqueue(
        jsonError(
            400,
            "{\"errorCode\":\"VSUM_REACTIONS_ERROR\",\"status\":400,\"message\":\"bad\","
                + "\"timestamp\":1730000000000,\"path\":\"/api/vsum/jar\",\"problems\":["
                + "{\"source\":\"REACTIONS\",\"file\":\"my.reactions\",\"line\":51,"
                + "\"column\":30,\"message\":\"'Entitiy' is not a class\","
                + "\"excerpt\":\"  new model2::Entitiy\",\"hint\":\"Did you mean 'Entity'?\","
                + "\"hiddenFollowUpErrors\":3,\"severity\":\"ERROR\"},"
                + "{\"source\":\"REACTIONS\",\"file\":\"compositeReaction1.reactions\","
                + "\"message\":\"generated\"},"
                + "{\"source\":\"METAMODEL\",\"file\":\"model2.ecore\",\"message\":\"m\"},"
                + "{\"source\":\"REACTIONS\",\"file\":\"unknown.reactions\","
                + "\"message\":\"u\"},"
                + "{\"source\":\"INFRASTRUCTURE\",\"message\":\"offline\"},"
                + "{\"source\":\"REACTIONS\",\"line\":\"not a number\",\"message\":\"x\"},"
                + "{\"file\":\"my.reactions\"},"
                + "\"not a problem\"]}"));

    assertThatThrownBy(
            () ->
                setupServiceApiHandler.buildVsumJarOrThrow(
                    List.of(ecore), List.of(composite, reactions), List.of(), List.of()))
        .isInstanceOfSatisfying(
            SetupServiceException.class,
            e -> {
              assertThat(e.isRejectedBySetupService()).isTrue();
              assertThat(e.getErrorCode()).isEqualTo("VSUM_REACTIONS_ERROR");
              assertThat(e)
                  .hasMessage(
                      "Setup-service request to '/api/vsum/jar' failed with status 400:"
                          + " [VSUM_REACTIONS_ERROR] bad");
              assertThat(e.getProblems())
                  .containsExactly(
                      new BuildProblem(
                          "REACTIONS",
                          "my.reactions",
                          12L,
                          51,
                          30,
                          "'Entitiy' is not a class",
                          "  new model2::Entitiy",
                          "Did you mean 'Entity'?",
                          3),
                      new BuildProblem(
                          "REACTIONS",
                          "compositeReaction1.reactions",
                          null,
                          null,
                          null,
                          "generated",
                          null,
                          null,
                          null),
                      new BuildProblem(
                          "METAMODEL", "model2.ecore", 11L, null, null, "m", null, null, null),
                      new BuildProblem(
                          "REACTIONS",
                          "unknown.reactions",
                          null,
                          null,
                          null,
                          "u",
                          null,
                          null,
                          null),
                      new BuildProblem(
                          "INFRASTRUCTURE", null, null, null, null, "offline", null, null, null));
            });
  }

  @Test
  void buildVsumJarOrThrow_resolvesTheFileSentUnderACompletedName() {
    mockWebServer.enqueue(
        jsonError(
            422,
            "{\"errorCode\":\"VSUM_BUILD_FAILED\",\"message\":\"m\",\"problems\":["
                + "{\"source\":\"REACTIONS\",\"file\":\"rules-2.reactions\","
                + "\"message\":\"p\"}]}"));

    assertThatThrownBy(
            () ->
                setupServiceApiHandler.buildVsumJarOrThrow(
                    metamodelFiles,
                    List.of(
                        fileStorage(21L, "rules.reactions", "a".getBytes()),
                        fileStorage(22L, "rules", "b".getBytes())),
                    List.of(),
                    List.of()))
        .isInstanceOfSatisfying(
            SetupServiceException.class,
            e -> assertThat(e.getProblems()).extracting(BuildProblem::fileId).containsExactly(22L));
  }

  @Test
  void generateVsumProjectOrThrow_keepsTheErrorCode_whenThereAreNoProblems() {
    mockWebServer.enqueue(
        jsonError(503, "{\"errorCode\":\"VSUM_BUILD_UNAVAILABLE\",\"message\":\"later\"}"));

    assertThatThrownBy(
            () ->
                setupServiceApiHandler.generateVsumProjectOrThrow(
                    metamodelFiles, reactionFiles, constraintFiles, viewFiles))
        .isInstanceOfSatisfying(
            SetupServiceException.class,
            e -> {
              assertThat(e.isRejectedBySetupService()).isFalse();
              assertThat(e.getErrorCode()).isEqualTo("VSUM_BUILD_UNAVAILABLE");
              assertThat(e.getProblems()).isNull();
            });
  }

  @Test
  void buildVsumJarOrThrow_hasNoErrorCodeOrProblems_forANonJsonBody() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(502).setBody("<html>bad</html>"));

    assertThatThrownBy(
            () ->
                setupServiceApiHandler.buildVsumJarOrThrow(
                    metamodelFiles, reactionFiles, constraintFiles, viewFiles))
        .isInstanceOfSatisfying(
            SetupServiceException.class,
            e -> {
              assertThat(e)
                  .hasMessage(
                      "Setup-service request to '/api/vsum/jar' failed with status 502:"
                          + " <html>bad</html>");
              assertThat(e.getErrorCode()).isNull();
              assertThat(e.getProblems()).isNull();
            });
  }

  @Test
  void setupServiceError_ignoresProblemsThatAreNotAList() {
    SetupServiceException e =
        SetupServiceApiHandler.setupServiceError(
            SetupServiceApiHandler.JAR_URL,
            HttpStatus.UNPROCESSABLE_ENTITY,
            "{\"errorCode\":7,\"message\":\"m\",\"problems\":{\"source\":\"REACTIONS\"}}",
            new UploadedFiles());

    assertThat(e.getErrorCode()).isNull();
    assertThat(e.getProblems()).isNull();
    assertThat(
            SetupServiceApiHandler.setupServiceError(
                    SetupServiceApiHandler.JAR_URL,
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "[1]",
                    new UploadedFiles())
                .getErrorCode())
        .isNull();
  }

  @Test
  void describeError_fallsBackToTheRawBody() {
    assertThat(SetupServiceApiHandler.describeError("{\"message\":\"m\"}")).isEqualTo("m");
    assertThat(SetupServiceApiHandler.describeError("not json")).isEqualTo("not json");
    assertThat(SetupServiceApiHandler.describeError("")).isEqualTo("(no response body)");
  }

  private static MockResponse jsonError(int status, String body) {
    return new MockResponse()
        .setResponseCode(status)
        .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
        .setBody(body);
  }

  @Test
  void validateVsum_parsesTheResult_andResolvesTheFileIdsOfTheProblems() throws Exception {
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
            .setBody(
                "{\"valid\":false,\"problems\":["
                    + "{\"source\":\"REACTIONS\",\"file\":\"rules.reactions\",\"line\":3,"
                    + "\"message\":\"unknown class\"},"
                    + "{\"source\":\"INFRASTRUCTURE\",\"message\":\"warning\"},"
                    + "{\"source\":\"REACTIONS\"}]}"));

    VsumValidationResult result =
        setupServiceApiHandler.validateVsum(
            metamodelFiles,
            List.of(fileStorage(21L, "rules.reactions", "a".getBytes())),
            List.of(),
            List.of());

    assertThat(result.valid()).isFalse();
    assertThat(result.problems())
        .containsExactly(
            new BuildProblem(
                "REACTIONS", "rules.reactions", 21L, 3, null, "unknown class", null, null, null),
            new BuildProblem(
                "INFRASTRUCTURE", null, null, null, null, "warning", null, null, null));
    RecordedRequest request = mockWebServer.takeRequest();
    assertThat(request.getPath()).isEqualTo(SetupServiceApiHandler.VALIDATE_URL);
    assertThat(request.getHeader(HttpHeaders.ACCEPT)).contains("application/json");
  }

  @Test
  void validateVsum_returnsAValidResultWithoutProblems() {
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
            .setBody("{\"valid\":true,\"problems\":[]}"));

    VsumValidationResult result =
        setupServiceApiHandler.validateVsum(
            metamodelFiles, reactionFiles, constraintFiles, viewFiles);

    assertThat(result.valid()).isTrue();
    assertThat(result.problems()).isEmpty();
  }

  @Test
  void validateVsum_throws_whenTheResponseIsMalformed() {
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
            .setBody("{\"valid\":\"yes\",\"problems\":[]}"));

    assertThatThrownBy(
            () ->
                setupServiceApiHandler.validateVsum(
                    metamodelFiles, reactionFiles, constraintFiles, viewFiles))
        .isInstanceOf(SetupServiceException.class)
        .hasMessage("Invalid validation response");
  }

  @Test
  void validateVsum_keepsTheErrorCodeAndProblems_whenTheSetupServiceFails() {
    mockWebServer.enqueue(
        jsonError(
            422,
            "{\"errorCode\":\"VSUM_REACTIONS_ERROR\",\"message\":\"m\",\"problems\":["
                + "{\"source\":\"REACTIONS\",\"file\":\"rules.reactions\","
                + "\"message\":\"p\"}]}"));

    assertThatThrownBy(
            () ->
                setupServiceApiHandler.validateVsum(
                    metamodelFiles,
                    List.of(fileStorage(21L, "rules.reactions", "a".getBytes())),
                    List.of(),
                    List.of()))
        .isInstanceOfSatisfying(
            SetupServiceException.class,
            e -> {
              assertThat(e.getErrorCode()).isEqualTo("VSUM_REACTIONS_ERROR");
              assertThat(e.getProblems()).extracting(BuildProblem::fileId).containsExactly(21L);
            });
  }

  @Test
  void validateVsum_throws_whenTheSetupServiceIsUnreachable() throws Exception {
    mockWebServer.shutdown();

    assertThatThrownBy(
            () ->
                setupServiceApiHandler.validateVsum(
                    metamodelFiles, reactionFiles, constraintFiles, viewFiles))
        .isInstanceOf(SetupServiceException.class)
        .hasMessageStartingWith("Failed to call setup-service validation")
        .hasCauseInstanceOf(WebClientRequestException.class);
  }

  private static FileStorage fileStorage(Long id, String filename, byte[] data) {
    FileStorage fileStorage = fileStorage(filename, data);
    fileStorage.setId(id);
    return fileStorage;
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
  void generateVsumProjectOrThrow_returnsArtifactBytes_andSendsMultipartRequest() throws Exception {
    byte[] expected = {1, 2, 3, 4, 5};
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setHeader(HttpHeaders.CONTENT_TYPE, "application/zip")
            .setBody(new Buffer().write(expected)));

    byte[] result =
        setupServiceApiHandler.generateVsumProjectOrThrow(
            metamodelFiles, reactionFiles, constraintFiles, viewFiles);

    assertThat(result).containsExactly(expected);

    RecordedRequest recordedRequest = mockWebServer.takeRequest();
    assertThat(recordedRequest.getMethod()).isEqualTo("POST");
    assertThat(recordedRequest.getPath()).isEqualTo(SetupServiceApiHandler.PROJECT_URL);
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
  void generateVsumProjectOrThrow_throwsSetupServiceException_onErrorStatus() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(500).setBody("boom"));

    assertThatThrownBy(
            () ->
                setupServiceApiHandler.generateVsumProjectOrThrow(
                    metamodelFiles, reactionFiles, constraintFiles, viewFiles))
        .isInstanceOf(SetupServiceException.class)
        .hasMessageContaining("boom");
  }

  @Test
  void generateVsumProjectOrThrow_throwsSetupServiceException_onEmptyArtifact() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(200));

    assertThatThrownBy(
            () ->
                setupServiceApiHandler.generateVsumProjectOrThrow(
                    metamodelFiles, reactionFiles, constraintFiles, viewFiles))
        .isInstanceOf(SetupServiceException.class)
        .hasMessageContaining("empty artifact");
  }

  @Test
  void generateVsumProjectOrThrow_throwsSetupServiceException_whenServiceUnreachable()
      throws Exception {
    mockWebServer.shutdown();

    assertThatThrownBy(
            () ->
                setupServiceApiHandler.generateVsumProjectOrThrow(
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
