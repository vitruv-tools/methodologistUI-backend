package tools.vitruv.methodologist.vsum.controller;

import static tools.vitruv.methodologist.messages.Message.VSUM_CREATED_SUCCESSFULLY;
import static tools.vitruv.methodologist.messages.Message.VSUM_REMOVED_SUCCESSFULLY;
import static tools.vitruv.methodologist.messages.Message.VSUM_UPDATED_SUCCESSFULLY;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.vitruv.methodologist.ResponseTemplateDto;
import tools.vitruv.methodologist.config.KeycloakAuthentication;
import tools.vitruv.methodologist.vsum.controller.dto.request.VsumPostRequest;
import tools.vitruv.methodologist.vsum.controller.dto.request.VsumPutRequest;
import tools.vitruv.methodologist.vsum.controller.dto.request.VsumSyncChangesPutRequest;
import tools.vitruv.methodologist.vsum.controller.dto.response.VsumMetaModelResponse;
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
  @PreAuthorize("hasRole('user')")
  public ResponseTemplateDto<Void> create(
      KeycloakAuthentication authentication, @Valid @RequestBody VsumPostRequest vsumPostRequest) {
    String callerEmail = authentication.getParsedToken().getEmail();
    vsumService.create(callerEmail, vsumPostRequest);
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
    String callerEmail = authentication.getParsedToken().getEmail();
    return ResponseTemplateDto.<VsumResponse>builder()
        .data(vsumService.findById(callerEmail, id))
        .build();
  }

  /**
   * Updates an existing VSUM resource with the provided data.
   *
   * @param authentication the Keycloak authentication for the current user
   * @param id the ID of the VSUM to update
   * @param vsumPutRequest the request containing updated VSUM fields
   * @return response containing the updated VSUM and a success message
   */
  @PutMapping("/v1/vsums/{id}")
  @PreAuthorize("hasRole('user')")
  public ResponseTemplateDto<VsumResponse> update(
      KeycloakAuthentication authentication,
      @PathVariable Long id,
      @Valid @RequestBody VsumPutRequest vsumPutRequest) {
    String callerEmail = authentication.getParsedToken().getEmail();
    VsumResponse vsumResponse = vsumService.update(callerEmail, id, vsumPutRequest);
    return ResponseTemplateDto.<VsumResponse>builder()
        .data(vsumResponse)
        .message(VSUM_UPDATED_SUCCESSFULLY)
        .build();
  }

  /**
   * Updates an existing VSUM resource.
   *
   * @param authentication the Keycloak authentication object for the current user
   * @param id the ID of the VSUM to update
   * @param vsumSyncChangesPutRequest the request containing VSUM update data
   * @return response indicating successful VSUM update
   */
  @PutMapping("/v1/vsums/{id}/sync-changes")
  @PreAuthorize("hasRole('user')")
  public ResponseTemplateDto<Void> update(
      KeycloakAuthentication authentication,
      @PathVariable Long id,
      @Valid @RequestBody VsumSyncChangesPutRequest vsumSyncChangesPutRequest) {
    String callerEmail = authentication.getParsedToken().getEmail();
    vsumService.update(callerEmail, id, vsumSyncChangesPutRequest);
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
  @PreAuthorize("hasRole('user')")
  public ResponseTemplateDto<Void> remove(
      KeycloakAuthentication authentication, @PathVariable Long id) {
    String callerEmail = authentication.getParsedToken().getEmail();
    vsumService.remove(callerEmail, id);
    return ResponseTemplateDto.<Void>builder().message(VSUM_REMOVED_SUCCESSFULLY).build();
  }

  /**
   * Retrieves all {@link tools.vitruv.methodologist.vsum.model.Vsum} records that belong to the
   * currently authenticated user.
   *
   * <p>The user's email is resolved from the provided {@link KeycloakAuthentication} token and used
   * to query associated VSUMs.
   *
   * @param authentication the Keycloak authentication containing the caller's email
   * @return a {@link ResponseTemplateDto} wrapping a list of {@link VsumResponse} objects
   */
  @GetMapping("/v1/vsums/find-all")
  @PreAuthorize("hasRole('user')")
  public ResponseTemplateDto<List<VsumResponse>> findAllByUser(
      KeycloakAuthentication authentication,
      @RequestParam(required = false) String name,
      @RequestParam(defaultValue = "0") int pageNumber,
      @RequestParam(defaultValue = "50") int pageSize) {
    String callerEmail = authentication.getParsedToken().getEmail();
    Pageable pageable = PageRequest.of(pageNumber, pageSize, Sort.by("id").descending());
    return ResponseTemplateDto.<List<VsumResponse>>builder()
        .data(vsumService.findAllByUser(callerEmail, name, pageable))
        .build();
  }

  /**
   * Retrieves removed VSUMs owned by the authenticated user and returns them as a paginated list.
   *
   * <p>The caller's email is resolved from the provided {@link KeycloakAuthentication} token and
   * used to fetch VSUMs whose {@code removedAt} timestamp is not {@code null}.
   *
   * @param authentication the Keycloak authentication containing the caller's identity
   * @param pageNumber zero-based page index (default 0)
   * @param pageSize the size of the page to be returned (default 50)
   * @return a {@link ResponseTemplateDto} wrapping a {@code List<VsumResponse>} representing the
   *     user's removed VSUMs
   */
  @GetMapping("/v1/vsums/find-all-removed")
  @PreAuthorize("hasRole('user')")
  public ResponseTemplateDto<List<VsumResponse>> findAllRemoved(
      KeycloakAuthentication authentication,
      @RequestParam(defaultValue = "0") int pageNumber,
      @RequestParam(defaultValue = "50") int pageSize) {
    String callerEmail = authentication.getParsedToken().getEmail();
    Pageable pageable = PageRequest.of(pageNumber, pageSize, Sort.by("id").descending());
    return ResponseTemplateDto.<List<VsumResponse>>builder()
        .data(vsumService.findAllRemoved(callerEmail, pageable))
        .build();
  }

  /**
   * Retrieves detailed information about a specific {@link
   * tools.vitruv.methodologist.vsum.model.Vsum} owned by the authenticated user. Includes the VSUM
   * metadata and its associated metamodels.
   *
   * <p>The VSUM is looked up by its identifier, ensuring it belongs to the user identified by the
   * provided {@link KeycloakAuthentication}.
   *
   * @param authentication the Keycloak authentication containing the caller's email
   * @param id the identifier of the VSUM to retrieve
   * @return a {@link ResponseTemplateDto} wrapping the detailed {@link VsumMetaModelResponse}
   */
  @GetMapping("/v1/vsums/{id}/details")
  @PreAuthorize("hasRole('user')")
  public ResponseTemplateDto<VsumMetaModelResponse> findVsumWithDetails(
      KeycloakAuthentication authentication, @PathVariable Long id) {
    String callerEmail = authentication.getParsedToken().getEmail();
    return ResponseTemplateDto.<VsumMetaModelResponse>builder()
        .data(vsumService.findVsumWithDetails(callerEmail, id))
        .build();
  }
}
