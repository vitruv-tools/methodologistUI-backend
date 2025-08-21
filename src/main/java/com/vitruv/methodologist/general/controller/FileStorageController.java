package com.vitruv.methodologist.general.controller;

import com.vitruv.methodologist.ResponseTemplateDto;
import com.vitruv.methodologist.config.KeycloakAuthentication;
import com.vitruv.methodologist.general.service.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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
      value = "/upload",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @PreAuthorize("hasRole('user')")
  public ResponseTemplateDto<String> upload(
      KeycloakAuthentication authentication,
      @Parameter(
              description = "File to upload",
              content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE))
          @RequestParam("file")
          MultipartFile file)
      throws Exception {
    var email = authentication.getParsedToken().getEmail();
    fileStorageService.storeFile(email, file);

    return ResponseTemplateDto.<String>builder().message("File uploaded successfully").build();
  }

  /**
   * Downloads a file from the server.
   *
   * @param id the ID of the file to download
   * @return ResponseEntity containing the file as a ByteArrayResource
   */
  @GetMapping(value = "/api/files/{id}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
  @PreAuthorize("hasRole('user')")
  public ResponseEntity<ByteArrayResource> download(@PathVariable Long id) {
    var f = fileStorageService.getFile(id);
    var bytes = f.getData();
    return ResponseEntity.ok()
        .contentType(
            MediaType.parseMediaType(
                f.getContentType() == null ? "application/octet-stream" : f.getContentType()))
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + f.getFilename() + "\"")
        .contentLength(bytes == null ? 0 : bytes.length)
        .body(new ByteArrayResource(bytes));
  }
}
