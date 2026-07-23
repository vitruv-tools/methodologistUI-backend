package tools.vitruv.methodologist.apihandler;

import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import tools.vitruv.methodologist.apihandler.dto.response.GenModelInspectionResponse;
import tools.vitruv.methodologist.exception.SetupServiceException;
import tools.vitruv.methodologist.general.model.FileStorage;

/**
 * Component responsible for handling API calls to the external setup-service. Provides
 * functionality to build a VSUM from the supplied metamodel, genmodel and reaction files and
 * download the generated artifact (ZIP or JAR) as raw bytes.
 */
@Component
public class SetupServiceApiHandler {
  public static final int DEFAULT_RESPONSE_TIMEOUT_IN_SECONDS = 300;
  public static final int DEFAULT_MAX_RESPONSE_SIZE_IN_BYTES = 524_288_000;
  public static final String BUILD_URL = "/api/vsum/build";
  public static final String JAR_URL = "/api/vsum/jar";
  public static final String PROCESS_GENMODEL_URL = "/api/genmodel/process";
  public static final String INSPECT_GENMODEL_URL = "/api/genmodel/inspect";
  public static final String METAMODEL_FILES_PART = "metamodelFiles";
  public static final String GENMODEL_FILES_PART = "genmodelFiles";
  public static final String REACTION_FILES_PART = "reactionFiles";
  public static final String FILE_PART = "file";

  private static final MediaType APPLICATION_ZIP = MediaType.parseMediaType("application/zip");
  private static final MediaType APPLICATION_JAR =
      MediaType.parseMediaType("application/java-archive");

  private final WebClient webClient;

  /**
   * Constructs a SetupServiceApiHandler with the specified base URL. Configures WebClient with a
   * response timeout and the maximum in-memory payload size so large build artifacts can be
   * buffered.
   *
   * @param baseUrl the base URL of the setup-service
   * @param maxResponseSize maximum size in bytes of the build artifact buffered in memory
   * @param responseTimeoutSeconds the response timeout in seconds for build requests
   */
  public SetupServiceApiHandler(
      @Value("${third_api.setup_service.base_url}") String baseUrl,
      @Value(
              "${third_api.setup_service.max_response_size:"
                  + DEFAULT_MAX_RESPONSE_SIZE_IN_BYTES
                  + "}")
          int maxResponseSize,
      @Value(
              "${third_api.setup_service.timeout-seconds:"
                  + DEFAULT_RESPONSE_TIMEOUT_IN_SECONDS
                  + "}")
          int responseTimeoutSeconds) {
    HttpClient httpClient =
        HttpClient.create().responseTimeout(Duration.ofSeconds(responseTimeoutSeconds));

    this.webClient =
        WebClient.builder()
            .baseUrl(baseUrl)
            .exchangeStrategies(
                ExchangeStrategies.builder()
                    .codecs(
                        configurer -> configurer.defaultCodecs().maxInMemorySize(maxResponseSize))
                    .build())
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .build();
  }

  /**
   * Builds a VSUM and downloads the result as a ZIP archive.
   *
   * @param metamodelFiles the metamodel (.ecore) files
   * @param genmodelFiles the genmodel (.genmodel) files
   * @param reactionFiles the reaction (.reactions) files
   * @return the generated ZIP artifact as a byte array
   * @throws SetupServiceException if the setup-service call fails or returns an empty artifact
   */
  public byte[] buildVsumZipOrThrow(
      List<FileStorage> metamodelFiles,
      List<FileStorage> genmodelFiles,
      List<FileStorage> reactionFiles) {
    return requestArtifactOrThrow(
        BUILD_URL, APPLICATION_ZIP, metamodelFiles, genmodelFiles, reactionFiles);
  }

  /**
   * Builds a VSUM and downloads the result as a JAR file.
   *
   * @param metamodelFiles the metamodel (.ecore) files
   * @param genmodelFiles the genmodel (.genmodel) files
   * @param reactionFiles the reaction (.reactions) files
   * @return the generated JAR artifact as a byte array
   * @throws SetupServiceException if the setup-service call fails or returns an empty artifact
   */
  public byte[] buildVsumJarOrThrow(
      List<FileStorage> metamodelFiles,
      List<FileStorage> genmodelFiles,
      List<FileStorage> reactionFiles) {
    return requestArtifactOrThrow(
        JAR_URL, APPLICATION_JAR, metamodelFiles, genmodelFiles, reactionFiles);
  }

  private byte[] requestArtifactOrThrow(
      String uri,
      MediaType acceptType,
      List<FileStorage> metamodelFiles,
      List<FileStorage> genmodelFiles,
      List<FileStorage> reactionFiles) {
    MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
    addFileParts(bodyBuilder, METAMODEL_FILES_PART, metamodelFiles);
    addFileParts(bodyBuilder, GENMODEL_FILES_PART, genmodelFiles);
    addFileParts(bodyBuilder, REACTION_FILES_PART, reactionFiles);

    byte[] artifact;
    try {
      artifact =
          webClient
              .post()
              .uri(uri)
              .contentType(MediaType.MULTIPART_FORM_DATA)
              .accept(acceptType, MediaType.APPLICATION_OCTET_STREAM)
              .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
              .retrieve()
              .onStatus(
                  HttpStatusCode::isError,
                  response ->
                      response
                          .bodyToMono(String.class)
                          .defaultIfEmpty("")
                          .flatMap(
                              body ->
                                  Mono.error(
                                      new SetupServiceException(
                                          requestFailedMessage(uri, response.statusCode(), body)))))
              .bodyToMono(byte[].class)
              .block();
    } catch (SetupServiceException e) {
      throw e;
    } catch (Exception e) {
      throw new SetupServiceException(
          "Failed to call setup-service '" + uri + "': " + e.getMessage());
    }

    if (artifact == null || artifact.length == 0) {
      throw new SetupServiceException(
          "Setup-service returned an empty artifact for '" + uri + "'.");
    }
    return artifact;
  }

  /**
   * Sends a GenModel to the setup-service {@code /api/genmodel/process} endpoint, which fixes the
   * GenModel and returns the processed file.
   *
   * @param genModelFile the GenModel (.genmodel) file to process
   * @return the processed GenModel file as a byte array
   * @throws SetupServiceException if the setup-service call fails or returns an empty file
   */
  public byte[] processGenModelOrThrow(FileStorage genModelFile) {
    MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
    addFilePart(bodyBuilder, FILE_PART, genModelFile);

    byte[] processed;
    try {
      processed =
          webClient
              .post()
              .uri(PROCESS_GENMODEL_URL)
              .contentType(MediaType.MULTIPART_FORM_DATA)
              .accept(MediaType.APPLICATION_OCTET_STREAM)
              .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
              .retrieve()
              .onStatus(
                  HttpStatusCode::isError,
                  response ->
                      response
                          .bodyToMono(String.class)
                          .defaultIfEmpty("")
                          .flatMap(
                              body ->
                                  Mono.error(
                                      new SetupServiceException(
                                          requestFailedMessage(
                                              PROCESS_GENMODEL_URL, response.statusCode(), body)))))
              .bodyToMono(byte[].class)
              .block();
    } catch (SetupServiceException e) {
      throw e;
    } catch (Exception e) {
      throw new SetupServiceException(
          "Failed to call setup-service '" + PROCESS_GENMODEL_URL + "': " + e.getMessage());
    }

    if (processed == null || processed.length == 0) {
      throw new SetupServiceException(
          "Setup-service returned an empty GenModel for '" + PROCESS_GENMODEL_URL + "'.");
    }
    return processed;
  }

  /**
   * Sends a GenModel to the setup-service {@code /api/genmodel/inspect} endpoint, which validates
   * the GenModel and returns a message describing the planned changes or the detected problem.
   *
   * <p>Both a successful (2xx) response and a validation failure (HTTP 422) are returned as a
   * {@link GenModelInspectionResponse}; only other error statuses raise a {@link
   * SetupServiceException}.
   *
   * @param genModelFile the GenModel (.genmodel) file to inspect
   * @return the inspection result
   * @throws SetupServiceException if the setup-service call fails for any reason other than a 422
   *     validation failure
   */
  public GenModelInspectionResponse inspectGenModelOrThrow(FileStorage genModelFile) {
    MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
    addFilePart(bodyBuilder, FILE_PART, genModelFile);

    try {
      GenModelInspectionResponse inspection =
          webClient
              .post()
              .uri(INSPECT_GENMODEL_URL)
              .contentType(MediaType.MULTIPART_FORM_DATA)
              .accept(MediaType.APPLICATION_JSON)
              .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
              .exchangeToMono(
                  response -> {
                    if (response.statusCode().is2xxSuccessful()
                        || response.statusCode().value()
                            == HttpStatus.UNPROCESSABLE_ENTITY.value()) {
                      return response.bodyToMono(GenModelInspectionResponse.class);
                    }
                    return response
                        .bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .flatMap(
                            body ->
                                Mono.error(
                                    new SetupServiceException(
                                        requestFailedMessage(
                                            INSPECT_GENMODEL_URL, response.statusCode(), body))));
                  })
              .block();

      if (inspection == null) {
        throw new SetupServiceException(
            "Setup-service returned an empty response for '" + INSPECT_GENMODEL_URL + "'.");
      }
      return inspection;
    } catch (SetupServiceException e) {
      throw e;
    } catch (Exception e) {
      throw new SetupServiceException(
          "Failed to call setup-service '" + INSPECT_GENMODEL_URL + "': " + e.getMessage());
    }
  }

  private static String requestFailedMessage(String uri, HttpStatusCode statusCode, String body) {
    return "Setup-service request to '" + uri + "' failed with status " + statusCode + ": " + body;
  }

  private void addFilePart(MultipartBodyBuilder bodyBuilder, String partName, FileStorage file) {
    if (file == null || file.getData() == null || file.getData().length == 0) {
      throw new SetupServiceException("Cannot send an empty file to the setup-service.");
    }
    String filename = file.getFilename() == null ? partName : file.getFilename();
    bodyBuilder.part(partName, new ByteArrayResource(file.getData())).filename(filename);
  }

  private void addFileParts(
      MultipartBodyBuilder bodyBuilder, String partName, List<FileStorage> files) {
    if (CollectionUtils.isEmpty(files)) {
      return;
    }
    for (FileStorage file : files) {
      if (file == null || file.getData() == null || file.getData().length == 0) {
        continue;
      }
      String filename = file.getFilename() == null ? partName : file.getFilename();
      bodyBuilder.part(partName, new ByteArrayResource(file.getData())).filename(filename);
    }
  }
}
