package com.vitruv.methodologist.vsum.controller;

import static com.vitruv.methodologist.messages.Message.META_MODEL_CREATED_SUCCESSFULLY;

import com.vitruv.methodologist.ResponseTemplateDto;
import com.vitruv.methodologist.config.KeycloakAuthentication;
import com.vitruv.methodologist.vsum.controller.dto.request.MetaModelPostRequest;
import com.vitruv.methodologist.vsum.controller.dto.response.MetaModelResponse;
import com.vitruv.methodologist.vsum.service.MetaModelService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * REST controller for managing metamodel operations. Provides endpoints for creating and retrieving
 * metamodels with user authentication.
 *
 * @see MetaModelService
 * @see MetaModelPostRequest
 * @see MetaModelResponse
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
   * Retrieves all metamodels owned by the authenticated user.
   *
   * @param authentication the Keycloak authentication object containing user details
   * @return ResponseTemplateDto containing list of MetaModelResponse objects
   */
  @GetMapping("/v1/meta-models")
  @PreAuthorize("hasRole('user')")
  public ResponseTemplateDto<List<MetaModelResponse>> findById(
      KeycloakAuthentication authentication) {
    var callerEmail = authentication.getParsedToken().getEmail();
    return ResponseTemplateDto.<List<MetaModelResponse>>builder()
        .data(metaModelService.findAllByUser(callerEmail))
        .build();
  }
}
