package tools.vitruv.methodologist.apihandler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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
 * functionality to build a VSUM from the supplied metamodel, reaction, constraint and view files
 * and download the generated artifact (ZIP or JAR) as raw bytes. The setup-service generates the
 * genmodels itself.
 */
@Component
public class SetupServiceApiHandler {
  /**
   * Must exceed the setup-service's own build timeout (15 minutes by default), since a build runs a
   * full Maven build and may wait for a free build slot.
   */
  public static final int DEFAULT_RESPONSE_TIMEOUT_IN_SECONDS = 960;

  public static final int DEFAULT_MAX_RESPONSE_SIZE_IN_BYTES = 524_288_000;
  public static final String PROJECT_URL = "/api/vsum/project";
  public static final String JAR_URL = "/api/vsum/jar";
  public static final String PROCESS_GENMODEL_URL = "/api/genmodel/process";
  public static final String INSPECT_GENMODEL_URL = "/api/genmodel/inspect";
  public static final String GENERATE_GENMODEL_URL = "/api/genmodel/generate";
  public static final String METAMODEL_FILES_PART = "metamodelFiles";
  public static final String REACTION_FILES_PART = "reactionFiles";
  public static final String CONSTRAINT_FILES_PART = "constraintFiles";
  public static final String VIEW_FILES_PART = "viewFiles";
  public static final String FILE_PART = "file";

  private static final MediaType APPLICATION_ZIP = MediaType.parseMediaType("application/zip");
  private static final MediaType APPLICATION_JAR =
      MediaType.parseMediaType("application/java-archive");

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
   * Generates the VSUM project via the setup-service and returns it as a ZIP archive. The project
   * follows the Methodologist Template and is not built; the user builds it with Maven.
   *
   * @param metamodelFiles the metamodel (.ecore) files
   * @param reactionFiles the reaction (.reactions) files, may be empty
   * @param constraintFiles the VitruvOCL (.ocl) constraint files, may be empty
   * @param viewFiles the NeoJoin view definition files, may be empty
   * @return the ZIP archive bytes
   * @throws SetupServiceException if the setup-service call fails or returns an empty artifact
   */
  public byte[] generateVsumProjectOrThrow(
      List<FileStorage> metamodelFiles,
      List<FileStorage> reactionFiles,
      List<FileStorage> constraintFiles,
      List<FileStorage> viewFiles) {
    return requestArtifactOrThrow(
        PROJECT_URL, APPLICATION_ZIP, metamodelFiles, reactionFiles, constraintFiles, viewFiles);
  }

  /**
   * Builds a VSUM via the setup-service and returns its executable jar-with-dependencies.
   *
   * @param metamodelFiles the metamodel (.ecore) files
   * @param reactionFiles the reaction (.reactions) files, may be empty
   * @param constraintFiles the VitruvOCL (.ocl) constraint files, may be empty
   * @param viewFiles the NeoJoin view definition files, may be empty
   * @return the JAR bytes
   * @throws SetupServiceException if the setup-service call fails or returns an empty artifact
   */
  public byte[] buildVsumJarOrThrow(
      List<FileStorage> metamodelFiles,
      List<FileStorage> reactionFiles,
      List<FileStorage> constraintFiles,
      List<FileStorage> viewFiles) {
    return requestArtifactOrThrow(
        JAR_URL, APPLICATION_JAR, metamodelFiles, reactionFiles, constraintFiles, viewFiles);
  }

  private byte[] requestArtifactOrThrow(
      String uri,
      MediaType acceptType,
      List<FileStorage> metamodelFiles,
      List<FileStorage> reactionFiles,
      List<FileStorage> constraintFiles,
      List<FileStorage> viewFiles) {
    MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
    addFileParts(bodyBuilder, METAMODEL_FILES_PART, metamodelFiles, ".ecore");
    addFileParts(bodyBuilder, REACTION_FILES_PART, reactionFiles, ".reactions");
    addFileParts(bodyBuilder, CONSTRAINT_FILES_PART, constraintFiles, ".ocl");
    addFileParts(bodyBuilder, VIEW_FILES_PART, viewFiles, null);

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
                                          requestFailedMessage(uri, response.statusCode(), body),
                                          response.statusCode()))))
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
    return requestGenModelOrThrow(PROCESS_GENMODEL_URL, genModelFile);
  }

  /**
   * Sends an Ecore file to the setup-service {@code /api/genmodel/generate} endpoint, which
   * generates the matching GenModel and returns it.
   *
   * @param ecoreFile the Ecore (.ecore) file to generate the GenModel from
   * @return the generated GenModel file as a byte array
   * @throws SetupServiceException if the setup-service call fails or returns an empty file
   */
  public byte[] generateGenModelOrThrow(FileStorage ecoreFile) {
    return requestGenModelOrThrow(GENERATE_GENMODEL_URL, ecoreFile);
  }

  private byte[] requestGenModelOrThrow(String uri, FileStorage file) {
    MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
    addFilePart(bodyBuilder, FILE_PART, file);

    byte[] genModel;
    try {
      genModel =
          webClient
              .post()
              .uri(uri)
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
                                          requestFailedMessage(uri, response.statusCode(), body),
                                          response.statusCode()))))
              .bodyToMono(byte[].class)
              .block();
    } catch (SetupServiceException e) {
      throw e;
    } catch (Exception e) {
      throw new SetupServiceException(
          "Failed to call setup-service '" + uri + "': " + e.getMessage());
    }

    if (genModel == null || genModel.length == 0) {
      throw new SetupServiceException(
          "Setup-service returned an empty GenModel for '" + uri + "'.");
    }
    return genModel;
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
                                            INSPECT_GENMODEL_URL, response.statusCode(), body),
                                        response.statusCode())));
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
    return "Setup-service request to '"
        + uri
        + "' failed with status "
        + statusCode.value()
        + ": "
        + describeError(body);
  }

  /**
   * Extracts the human-readable part of a setup-service error response. The setup-service answers
   * with JSON containing {@code errorCode} and {@code message}; other bodies are returned as is.
   */
  static String describeError(String body) {
    if (body == null || body.isBlank()) {
      return "(no response body)";
    }
    try {
      JsonNode error = OBJECT_MAPPER.readTree(body);
      JsonNode message = error.get("message");
      if (message == null || !message.isTextual()) {
        return body;
      }
      JsonNode errorCode = error.get("errorCode");
      return errorCode != null && errorCode.isTextual()
          ? "[" + errorCode.asText() + "] " + message.asText()
          : message.asText();
    } catch (JsonProcessingException e) {
      return body;
    }
  }

  private void addFilePart(MultipartBodyBuilder bodyBuilder, String partName, FileStorage file) {
    if (file == null || file.getData() == null || file.getData().length == 0) {
      throw new SetupServiceException("Cannot send an empty file to the setup-service.");
    }
    String filename = file.getFilename() == null ? partName : file.getFilename();
    bodyBuilder.part(partName, new ByteArrayResource(file.getData())).filename(filename);
  }

  /**
   * Adds the non-empty files as parts. The setup-service rejects files without the expected
   * extension and duplicate file names, so names are completed and made unique; the setup-service
   * itself does not depend on the original names.
   */
  private void addFileParts(
      MultipartBodyBuilder bodyBuilder,
      String partName,
      List<FileStorage> files,
      String extension) {
    if (CollectionUtils.isEmpty(files)) {
      return;
    }
    Set<String> usedNames = new HashSet<>();
    for (FileStorage file : files) {
      if (file == null || file.getData() == null || file.getData().length == 0) {
        continue;
      }
      String filename = uniqueFilename(file.getFilename(), partName, extension, usedNames);
      bodyBuilder.part(partName, new ByteArrayResource(file.getData())).filename(filename);
    }
  }

  static String uniqueFilename(
      String filename, String fallback, String extension, Set<String> usedNames) {
    String name = filename == null || filename.isBlank() ? fallback : filename;
    if (extension != null && !name.toLowerCase(Locale.ROOT).endsWith(extension)) {
      name = name + extension;
    }
    int dot = extension != null ? name.length() - extension.length() : name.lastIndexOf('.');
    String base = dot > 0 ? name.substring(0, dot) : name;
    String suffix = dot > 0 ? name.substring(dot) : "";
    String candidate = name;
    for (int counter = 2; !usedNames.add(candidate.toLowerCase(Locale.ROOT)); counter++) {
      candidate = base + "-" + counter + suffix;
    }
    return candidate;
  }
}
