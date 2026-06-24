package tools.vitruv.methodologist.apihandler;

import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import tools.vitruv.methodologist.exception.SetupServiceException;

/**
 * Component responsible for handling API calls to the external setup-service. Provides
 * functionality to build a VSUM from the supplied metamodel, genmodel and reaction files and
 * download the generated artifact (ZIP or JAR) as raw bytes.
 */
@Component
public class SetupServiceApiHandler {
  public static final int DEFAULT_RESPONSE_TIMEOUT_IN_SECONDS = 300;
  public static final String BUILD_URL = "/setup-service/api/vsum/build";
  public static final String JAR_URL = "/setup-service/api/vsum/jar";
  public static final String METAMODEL_FILES_PART = "metamodelFiles";
  public static final String GENMODEL_FILES_PART = "genmodelFiles";
  public static final String REACTION_FILES_PART = "reactionFiles";

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
   * @param payloadSize maximum size in bytes for request/response payloads
   * @param responseTimeoutSeconds the response timeout in seconds for build requests
   */
  public SetupServiceApiHandler(
      @Value("${third_api.setup_service.base_url}") String baseUrl,
      @Value("${http.client.payload_size}") int payloadSize,
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
                    .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(payloadSize))
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
      List<MultipartFile> metamodelFiles,
      List<MultipartFile> genmodelFiles,
      List<MultipartFile> reactionFiles) {
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
      List<MultipartFile> metamodelFiles,
      List<MultipartFile> genmodelFiles,
      List<MultipartFile> reactionFiles) {
    return requestArtifactOrThrow(
        JAR_URL, APPLICATION_JAR, metamodelFiles, genmodelFiles, reactionFiles);
  }

  private byte[] requestArtifactOrThrow(
      String uri,
      MediaType acceptType,
      List<MultipartFile> metamodelFiles,
      List<MultipartFile> genmodelFiles,
      List<MultipartFile> reactionFiles) {
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
                                          "Setup-service request to '"
                                              + uri
                                              + "' failed with status "
                                              + response.statusCode()
                                              + ": "
                                              + body))))
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

  private void addFileParts(
      MultipartBodyBuilder bodyBuilder, String partName, List<MultipartFile> files) {
    if (CollectionUtils.isEmpty(files)) {
      return;
    }
    for (MultipartFile file : files) {
      if (file == null || file.isEmpty()) {
        continue;
      }
      String filename = file.getOriginalFilename() == null ? partName : file.getOriginalFilename();
      bodyBuilder.part(partName, file.getResource()).filename(filename);
    }
  }
}
