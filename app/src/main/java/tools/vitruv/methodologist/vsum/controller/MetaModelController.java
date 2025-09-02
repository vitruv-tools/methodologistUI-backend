package tools.vitruv.methodologist.vsum.controller;

import static tools.vitruv.methodologist.messages.Message.META_MODEL_CREATED_SUCCESSFULLY;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.vitruv.methodologist.ResponseTemplateDto;
import tools.vitruv.methodologist.config.KeycloakAuthentication;
import tools.vitruv.methodologist.vsum.controller.dto.request.MetaModelFilterRequest;
import tools.vitruv.methodologist.vsum.controller.dto.request.MetaModelPostRequest;
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
    var callerEmail = authentication.getParsedToken().getEmail();
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
  public ResponseTemplateDto<List<MetaModelResponse>> findAllByUser(
      KeycloakAuthentication authentication,
      @Valid @RequestBody MetaModelFilterRequest metaModelFilterRequest,
      @RequestParam(defaultValue = "0") int pageNumber,
      @RequestParam(defaultValue = "50") int pageSize) {
    var callerEmail = authentication.getParsedToken().getEmail();
    Pageable pageable = PageRequest.of(pageNumber, pageSize, Sort.by("id").descending());
    return ResponseTemplateDto.<List<MetaModelResponse>>builder()
        .data(metaModelService.findAllByUser(callerEmail, metaModelFilterRequest, pageable))
        .build();
  }
}
