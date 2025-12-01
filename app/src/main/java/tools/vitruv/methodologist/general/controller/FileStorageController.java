package tools.vitruv.methodologist.general.controller;

import static tools.vitruv.methodologist.messages.Message.FILE_REMOVED_SUCCESSFULLY;
import static tools.vitruv.methodologist.messages.Message.FILE_UPLOADED_SUCCESSFULLY;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tools.vitruv.methodologist.ResponseTemplateDto;
import tools.vitruv.methodologist.config.KeycloakAuthentication;
import tools.vitruv.methodologist.general.FileEnumType;
import tools.vitruv.methodologist.general.controller.responsedto.FileStorageResponse;
import tools.vitruv.methodologist.general.model.FileStorage;
import tools.vitruv.methodologist.general.service.FileStorageService;

/**
 * REST controller for handling file storage operations. Provides endpoints for uploading,
 * downloading, and managing stored files.
 */
@RestController
@RequestMapping("/api/")
@Validated
public class FileStorageController {
  private final FileStorageService fileStorageService;

  /**
   * Constructs a new FileStorageController with the specified service.
   *
   * @param fileStorageService the service for file storage operations
   */
  public FileStorageController(FileStorageService fileStorageService) {
    this.fileStorageService = fileStorageService;
  }

  /**
   * Uploads a file to the server.
   *
   * @param authentication the Keycloak authentication object containing user details
   * @param file the multipart file to upload
   * @return ResponseTemplateDto containing success message
   * @throws Exception if file upload fails
   */
  @Operation(summary = "Upload a file", description = "Upload a file to the server")
  @PostMapping(
      value = "/upload/type={type}",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @PreAuthorize("hasRole('user')")
  public ResponseTemplateDto<FileStorageResponse> upload(
      KeycloakAuthentication authentication,
      @Parameter(
              description = "File to upload",
              content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE))
          @RequestParam("file")
          MultipartFile file,
      @PathVariable FileEnumType type)
      throws Exception {
    String email = authentication.getParsedToken().getEmail();
    FileStorageResponse response = fileStorageService.storeFile(email, file, type);

    return ResponseTemplateDto.<FileStorageResponse>builder()
        .data(response)
        .message(FILE_UPLOADED_SUCCESSFULLY)
        .build();
  }

  /**
   * Updates an existing stored file by overwriting its contents with a newly uploaded file.
   *
   * <p>The file is identified by its database ID. The new file content replaces the old content,
   * while still enforcing deduplication rules against other files of the same user.
   *
   * @param authentication the Keycloak authentication object containing user details
   * @param file the new multipart file whose content will overwrite the existing file
   * @param id the ID of the existing file to update
   * @return ResponseTemplateDto containing the updated file's information
   * @throws Exception if file update fails
   */
  @Operation(
      summary = "Update an existing file",
      description = "Overwrite an existing stored file with a new uploaded file")
  @PostMapping(
      value = "/upload/{id}/update-reaction",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @PreAuthorize("hasRole('user')")
  public ResponseTemplateDto<FileStorageResponse> update(
      KeycloakAuthentication authentication,
      @Parameter(
              description = "New file content",
              content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE))
          @RequestParam("file")
          MultipartFile file,
      @PathVariable Long id)
      throws Exception {
    String email = authentication.getParsedToken().getEmail();
    FileStorageResponse response = fileStorageService.updateFile(email, id, file);

    return ResponseTemplateDto.<FileStorageResponse>builder()
        .data(response)
        .message(FILE_UPLOADED_SUCCESSFULLY)
        .build();
  }

  /**
   * Downloads a file from the server.
   *
   * @param id the ID of the file to download
   * @return ResponseEntity containing the file as a ByteArrayResource
   */
  @GetMapping(value = "/files/{id}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
  @PreAuthorize("hasRole('user')")
  public ResponseEntity<ByteArrayResource> download(@PathVariable Long id) {
    FileStorage f = fileStorageService.getFile(id);
    byte[] bytes = f.getData();
    return ResponseEntity.ok()
        .contentType(
            MediaType.parseMediaType(
                f.getContentType() == null ? "application/octet-stream" : f.getContentType()))
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + f.getFilename() + "\"")
        .contentLength(bytes == null ? 0 : bytes.length)
        .body(new ByteArrayResource(bytes));
  }

  /**
   * Removes a file resource.
   *
   * @param authentication the Keycloak authentication object for the current user
   * @param id the ID of the File to remove
   * @return response indicating successful File removal
   */
  @DeleteMapping("/v1/files/{id}")
  @PreAuthorize("hasRole('user')")
  public ResponseTemplateDto<Void> remove(
      KeycloakAuthentication authentication, @PathVariable Long id) {
    String callerEmail = authentication.getParsedToken().getEmail();
    fileStorageService.remove(callerEmail, id);
    return ResponseTemplateDto.<Void>builder().message(FILE_REMOVED_SUCCESSFULLY).build();
  }
}
