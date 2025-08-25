package tools.vitruv.methodologist.vsum.controller;

import static tools.vitruv.methodologist.messages.Message.VSUM_CREATED_SUCCESSFULLY;
import static tools.vitruv.methodologist.messages.Message.VSUM_REMOVED_SUCCESSFULLY;
import static tools.vitruv.methodologist.messages.Message.VSUM_UPDATED_SUCCESSFULLY;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.vitruv.methodologist.ResponseTemplateDto;
import tools.vitruv.methodologist.config.KeycloakAuthentication;
import tools.vitruv.methodologist.vsum.controller.dto.request.VsumPostRequest;
import tools.vitruv.methodologist.vsum.controller.dto.request.VsumPutRequest;
import tools.vitruv.methodologist.vsum.controller.dto.response.VsumResponse;
import tools.vitruv.methodologist.vsum.service.VsumService;

/**
 * REST controller for managing VSUM (Virtual Single Underlying Model) resources. Provides endpoints
 * for CRUD operations on VSUMs.
 */
@RestController
@RequestMapping("/api/")
@Validated
public class VsumController {
  private final VsumService vsumService;

  /**
   * Constructs a new VSUM controller with the required service dependency.
   *
   * @param vsumService the service component for VSUM operations
   */
  public VsumController(VsumService vsumService) {
    this.vsumService = vsumService;
  }

  /**
   * Creates a new VSUM resource.
   *
   * @param authentication the Keycloak authentication object for the current user
   * @param vsumPostRequest the request containing VSUM creation data
   * @return response indicating successful VSUM creation
   */
  @PostMapping("/v1/vsums")
  public ResponseTemplateDto<Void> create(
      KeycloakAuthentication authentication, @Valid @RequestBody VsumPostRequest vsumPostRequest) {
    vsumService.create(vsumPostRequest);
    return ResponseTemplateDto.<Void>builder().message(VSUM_CREATED_SUCCESSFULLY).build();
  }

  /**
   * Retrieves a VSUM by its ID.
   *
   * @param authentication the Keycloak authentication object for the current user
   * @param id the ID of the VSUM to retrieve
   * @return response containing the requested VSUM data
   */
  @GetMapping("/v1/vsums/{id}")
  @PreAuthorize("hasRole('user')")
  public ResponseTemplateDto<VsumResponse> findById(
      KeycloakAuthentication authentication, @PathVariable Long id) {
    return ResponseTemplateDto.<VsumResponse>builder().data(vsumService.findById(id)).build();
  }

  /**
   * Updates an existing VSUM resource.
   *
   * @param authentication the Keycloak authentication object for the current user
   * @param id the ID of the VSUM to update
   * @param vsumPutRequest the request containing VSUM update data
   * @return response indicating successful VSUM update
   */
  @PutMapping("/v1/vsums/{id}")
  public ResponseTemplateDto<Void> update(
      KeycloakAuthentication authentication,
      @PathVariable Long id,
      @Valid @RequestBody VsumPutRequest vsumPutRequest) {
    vsumService.update(id, vsumPutRequest);
    return ResponseTemplateDto.<Void>builder().message(VSUM_UPDATED_SUCCESSFULLY).build();
  }

  /**
   * Removes a VSUM resource.
   *
   * @param authentication the Keycloak authentication object for the current user
   * @param id the ID of the VSUM to remove
   * @return response indicating successful VSUM removal
   */
  @DeleteMapping("/v1/vsums/{id}")
  public ResponseTemplateDto<Void> remove(
      KeycloakAuthentication authentication, @PathVariable Long id) {
    vsumService.remove(id);
    return ResponseTemplateDto.<Void>builder().message(VSUM_REMOVED_SUCCESSFULLY).build();
  }
}
