package tools.vitruv.methodologist.vsum.controller;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
import tools.vitruv.methodologist.config.KeycloakAuthentication;
import tools.vitruv.methodologist.vsum.controller.dto.request.HelperRequest;
import tools.vitruv.methodologist.vsum.controller.dto.response.HelperClassResponse;
import tools.vitruv.methodologist.vsum.controller.dto.response.HelperNameAvailabilityResponse;
import tools.vitruv.methodologist.vsum.controller.dto.response.HelperPreviewResponse;
import tools.vitruv.methodologist.vsum.controller.dto.response.HelperResponse;
import tools.vitruv.methodologist.vsum.controller.dto.response.HelperTypeResponse;
import tools.vitruv.methodologist.vsum.service.HelperService;

/**
 * REST controller for the helpers of a VSUM, which are translated into one Java utility class that
 * the VSUM's reactions can call.
 */
@RestController
@RequestMapping("/api/v1/vsums/{vsumId}/helpers")
@Validated
public class HelperController {

  private final HelperService service;

  /**
   * Constructs a new {@code HelperController}.
   *
   * @param service the helper service
   */
  public HelperController(HelperService service) {
    this.service = service;
  }

  /**
   * Returns all helpers of the given VSUM.
   *
   * @param authentication the current user's authentication
   * @param vsumId the VSUM ID
   * @return the helpers with their generated Java methods or problems
   */
  @GetMapping
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<List<HelperResponse>> getAll(
      KeycloakAuthentication authentication, @PathVariable Long vsumId) {
    String email = authentication.getParsedToken().getEmail();
    return ResponseEntity.ok(service.findAll(email, vsumId));
  }

  /**
   * Creates a helper.
   *
   * @param authentication the current user's authentication
   * @param vsumId the VSUM ID
   * @param request the helper
   * @return the created helper with HTTP 201
   */
  @PostMapping
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<HelperResponse> create(
      KeycloakAuthentication authentication,
      @PathVariable Long vsumId,
      @Valid @RequestBody HelperRequest request) {
    String email = authentication.getParsedToken().getEmail();
    return ResponseEntity.status(HttpStatus.CREATED).body(service.create(email, vsumId, request));
  }

  /**
   * Updates a helper.
   *
   * @param authentication the current user's authentication
   * @param vsumId the VSUM ID
   * @param helperId the helper ID
   * @param request the new helper definition
   * @return the updated helper
   */
  @PutMapping("/{helperId}")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<HelperResponse> update(
      KeycloakAuthentication authentication,
      @PathVariable Long vsumId,
      @PathVariable Long helperId,
      @Valid @RequestBody HelperRequest request) {
    String email = authentication.getParsedToken().getEmail();
    return ResponseEntity.ok(service.update(email, vsumId, helperId, request));
  }

  /**
   * Deletes a helper and thereby its generated Java method.
   *
   * @param authentication the current user's authentication
   * @param vsumId the VSUM ID
   * @param helperId the helper ID
   * @return HTTP 204 No Content
   */
  @DeleteMapping("/{helperId}")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<Void> delete(
      KeycloakAuthentication authentication,
      @PathVariable Long vsumId,
      @PathVariable Long helperId) {
    String email = authentication.getParsedToken().getEmail();
    service.delete(email, vsumId, helperId);
    return ResponseEntity.noContent().build();
  }

  /**
   * Translates an unsaved helper for live feedback in the editor. Problems are part of the response
   * rather than an error status.
   *
   * @param authentication the current user's authentication
   * @param vsumId the VSUM ID
   * @param helperId the ID of the helper being edited; omitted for a new helper
   * @param request the helper
   * @return the OCL definition, the generated Java method and the problems found
   */
  @PostMapping("/preview")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<HelperPreviewResponse> preview(
      KeycloakAuthentication authentication,
      @PathVariable Long vsumId,
      @RequestParam(required = false) Long helperId,
      @RequestBody HelperRequest request) {
    String email = authentication.getParsedToken().getEmail();
    return ResponseEntity.ok(service.preview(email, vsumId, helperId, request));
  }

  /**
   * Checks whether a helper name is valid and not yet used in the VSUM.
   *
   * @param authentication the current user's authentication
   * @param vsumId the VSUM ID
   * @param name the name to check
   * @param helperId the ID of the helper being renamed; omitted for a new helper
   * @return whether the name is available
   */
  @GetMapping("/name-availability")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<HelperNameAvailabilityResponse> checkName(
      KeycloakAuthentication authentication,
      @PathVariable Long vsumId,
      @RequestParam String name,
      @RequestParam(required = false) Long helperId) {
    String email = authentication.getParsedToken().getEmail();
    return ResponseEntity.ok(service.checkName(email, vsumId, name, helperId));
  }

  /**
   * Returns the Java utility class generated from all helpers of the VSUM.
   *
   * @param authentication the current user's authentication
   * @param vsumId the VSUM ID
   * @return the class source and the problems of helpers that are left out
   */
  @GetMapping("/java")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<HelperClassResponse> generatedClass(
      KeycloakAuthentication authentication, @PathVariable Long vsumId) {
    String email = authentication.getParsedToken().getEmail();
    return ResponseEntity.ok(service.generatedClass(email, vsumId));
  }

  /**
   * Returns the metamodel types helpers of the VSUM can use.
   *
   * @param authentication the current user's authentication
   * @param vsumId the VSUM ID
   * @return the classes, enumerations and data types of the VSUM's metamodels
   */
  @GetMapping("/types")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<List<HelperTypeResponse>> availableTypes(
      KeycloakAuthentication authentication, @PathVariable Long vsumId) {
    String email = authentication.getParsedToken().getEmail();
    return ResponseEntity.ok(service.availableTypes(email, vsumId));
  }
}
