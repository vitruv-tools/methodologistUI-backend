package tools.vitruv.methodologist.vsum.controller;

import static tools.vitruv.methodologist.messages.Message.META_MODEL_CREATED_SUCCESSFULLY;
import static tools.vitruv.methodologist.messages.Message.META_MODEL_REMOVED_SUCCESSFULLY;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.vitruv.methodologist.ResponseTemplateDto;
import tools.vitruv.methodologist.config.KeycloakAuthentication;
import tools.vitruv.methodologist.vsum.controller.dto.request.MetaModelFilterRequest;
import tools.vitruv.methodologist.vsum.controller.dto.request.MetaModelPostRequest;
import tools.vitruv.methodologist.vsum.controller.dto.request.MetaModelPutRequest;
import tools.vitruv.methodologist.vsum.controller.dto.response.MetaModelResponse;
import tools.vitruv.methodologist.vsum.service.MetaModelService;

/**
 * Controller class for handling metamodel-related endpoints. Provides functionality for creating
 * and retrieving metamodels specific to an authenticated user.
 */
@RestController
@RequestMapping("/api/")
@Validated
public class MetaModelController {
  private final MetaModelService metaModelService;

  /**
   * Constructs a new MetaModelController with the specified service.
   *
   * @param metaModelService the service for metamodel operations
   */
  public MetaModelController(MetaModelService metaModelService) {
    this.metaModelService = metaModelService;
  }

  /**
   * Creates a new metamodel with the authenticated user as owner.
   *
   * @param authentication the Keycloak authentication object containing user details
   * @param metaModelPostRequest the request body containing metamodel details
   * @return ResponseTemplateDto with success message
   */
  @PostMapping("/v1/meta-models")
  @PreAuthorize("hasRole('user')")
  public ResponseTemplateDto<Void> create(
      KeycloakAuthentication authentication,
      @Valid @RequestBody MetaModelPostRequest metaModelPostRequest) {
    String callerEmail = authentication.getParsedToken().getEmail();
    metaModelService.create(callerEmail, metaModelPostRequest);
    return ResponseTemplateDto.<Void>builder().message(META_MODEL_CREATED_SUCCESSFULLY).build();
  }

  /**
   * Retrieves a paginated list of metamodels owned by the authenticated user, with optional
   * filtering criteria.
   *
   * <p>The caller's email is extracted from the {@link KeycloakAuthentication} token, and the
   * provided {@link MetaModelFilterRequest} is used to filter results. Results are returned in
   * descending order of metamodel ID.
   *
   * @param authentication the authentication object containing user identity information
   * @param metaModelFilterRequest request body with filter criteria for narrowing results
   * @param pageNumber zero-based page index for pagination (defaults to 0)
   * @param pageSize number of items per page for pagination (defaults to 50)
   * @return a {@link ResponseTemplateDto} containing the list of matching metamodel responses
   */
  @PostMapping("/v1/meta-models/find-all")
  @PreAuthorize("hasRole('user')")
  public ResponseTemplateDto<List<MetaModelResponse>> findAll(
      KeycloakAuthentication authentication,
      @Valid @RequestBody MetaModelFilterRequest metaModelFilterRequest,
      @RequestParam(defaultValue = "0") int pageNumber,
      @RequestParam(defaultValue = "50") int pageSize) {
    String callerEmail = authentication.getParsedToken().getEmail();
    Pageable pageable = PageRequest.of(pageNumber, pageSize, Sort.by("id").descending());
    return ResponseTemplateDto.<List<MetaModelResponse>>builder()
        .data(metaModelService.findAll(callerEmail, metaModelFilterRequest, pageable))
        .build();
  }

  /**
   * Deletes a metamodel owned by the authenticated user.
   *
   * @param authentication the Keycloak authentication object containing user details
   * @param id the unique identifier of the metamodel to delete
   * @return ResponseTemplateDto with success message
   */
  @DeleteMapping("/v1/meta-models/{id}")
  @PreAuthorize("hasRole('user')")
  public ResponseTemplateDto<Void> delete(
      KeycloakAuthentication authentication, @PathVariable Long id) {
    String callerEmail = authentication.getParsedToken().getEmail();
    metaModelService.delete(callerEmail, id);
    return ResponseTemplateDto.<Void>builder().message(META_MODEL_REMOVED_SUCCESSFULLY).build();
  }

  /**
   * Updates an existing MetaModel owned by the authenticated user.
   *
   * <p>The caller must have the {@code user} role and must be authorized to modify the specified
   * MetaModel. Authorization rules are enforced at the service layer and may involve ownership
   * checks or controlled cloning of shared MetaModels.
   *
   * <p>The request body is validated using {@link jakarta.validation.Valid}. If validation fails, a
   * {@code 400 Bad Request} response is returned automatically by Spring.
   *
   * <p>On successful update, this endpoint returns a standard response wrapper with a success
   * message and no payload.
   *
   * @param authentication the Keycloak authentication containing the caller's identity
   * @param id the identifier of the MetaModel to update
   * @param metaModelPutRequest the validated request containing the MetaModel updates
   * @return a {@link ResponseTemplateDto} with a success message and no data
   * @throws org.springframework.security.access.AccessDeniedException if the caller is not
   *     authorized to update the specified MetaModel
   * @throws tools.vitruv.methodologist.exception.NotFoundException if the MetaModel does not exist
   */
  @PutMapping("/v1/meta-models/{id}")
  @PreAuthorize("hasRole('user')")
  public ResponseTemplateDto<Void> update(
      KeycloakAuthentication authentication,
      @PathVariable Long id,
      @Valid @RequestBody MetaModelPutRequest metaModelPutRequest) {
    String callerEmail = authentication.getParsedToken().getEmail();
    metaModelService.update(callerEmail, id, metaModelPutRequest);
    return ResponseTemplateDto.<Void>builder().message(META_MODEL_REMOVED_SUCCESSFULLY).build();
  }
}
