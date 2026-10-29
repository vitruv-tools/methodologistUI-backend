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
import tools.vitruv.methodologist.vsum.controller.dto.request.UtilityMethodRequest;
import tools.vitruv.methodologist.vsum.controller.dto.response.UtilityMethodClassResponse;
import tools.vitruv.methodologist.vsum.controller.dto.response.UtilityMethodNameAvailabilityResponse;
import tools.vitruv.methodologist.vsum.controller.dto.response.UtilityMethodPreviewResponse;
import tools.vitruv.methodologist.vsum.controller.dto.response.UtilityMethodResponse;
import tools.vitruv.methodologist.vsum.controller.dto.response.UtilityMethodTypeResponse;
import tools.vitruv.methodologist.vsum.service.UtilityMethodService;

/**
 * REST controller for the utility methods of a VSUM, which are translated into one Java utility
 * class that the VSUM's reactions can call.
 */
@RestController
@RequestMapping("/api/v1/vsums/{vsumId}/utility-methods")
@Validated
public class UtilityMethodController {

  private final UtilityMethodService service;

  /**
   * Constructs a new {@code UtilityMethodController}.
   *
   * @param service the utility method service
   */
  public UtilityMethodController(UtilityMethodService service) {
    this.service = service;
  }

  /**
   * Returns all utility methods of the given VSUM.
   *
   * @param authentication the current user's authentication
   * @param vsumId the VSUM ID
   * @return the utility methods with their generated Java methods or problems
   */
  @GetMapping
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<List<UtilityMethodResponse>> getAll(
      KeycloakAuthentication authentication, @PathVariable Long vsumId) {
    String email = authentication.getParsedToken().getEmail();
    return ResponseEntity.ok(service.findAll(email, vsumId));
  }

  /**
   * Creates a utility method.
   *
   * @param authentication the current user's authentication
   * @param vsumId the VSUM ID
   * @param request the utility method
   * @return the created utility method with HTTP 201
   */
  @PostMapping
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<UtilityMethodResponse> create(
      KeycloakAuthentication authentication,
      @PathVariable Long vsumId,
      @Valid @RequestBody UtilityMethodRequest request) {
    String email = authentication.getParsedToken().getEmail();
    return ResponseEntity.status(HttpStatus.CREATED).body(service.create(email, vsumId, request));
  }

  /**
   * Updates a utility method.
   *
   * @param authentication the current user's authentication
   * @param vsumId the VSUM ID
   * @param utilityMethodId the utility method ID
   * @param request the new utility method definition
   * @return the updated utility method
   */
  @PutMapping("/{utilityMethodId}")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<UtilityMethodResponse> update(
      KeycloakAuthentication authentication,
      @PathVariable Long vsumId,
      @PathVariable Long utilityMethodId,
      @Valid @RequestBody UtilityMethodRequest request) {
    String email = authentication.getParsedToken().getEmail();
    return ResponseEntity.ok(service.update(email, vsumId, utilityMethodId, request));
  }

  /**
   * Deletes a utility method and thereby its generated Java method.
   *
   * @param authentication the current user's authentication
   * @param vsumId the VSUM ID
   * @param utilityMethodId the utility method ID
   * @return HTTP 204 No Content
   */
  @DeleteMapping("/{utilityMethodId}")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<Void> delete(
      KeycloakAuthentication authentication,
      @PathVariable Long vsumId,
      @PathVariable Long utilityMethodId) {
    String email = authentication.getParsedToken().getEmail();
    service.delete(email, vsumId, utilityMethodId);
    return ResponseEntity.noContent().build();
  }

  /**
   * Translates an unsaved utility method for live feedback in the editor. Problems are part of the
   * response rather than an error status.
   *
   * @param authentication the current user's authentication
   * @param vsumId the VSUM ID
   * @param utilityMethodId the ID of the utility method being edited; omitted for a new utility
   *     method
   * @param request the utility method
   * @return the OCL definition, the generated Java method and the problems found
   */
  @PostMapping("/preview")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<UtilityMethodPreviewResponse> preview(
      KeycloakAuthentication authentication,
      @PathVariable Long vsumId,
      @RequestParam(required = false) Long utilityMethodId,
      @RequestBody UtilityMethodRequest request) {
    String email = authentication.getParsedToken().getEmail();
    return ResponseEntity.ok(service.preview(email, vsumId, utilityMethodId, request));
  }

  /**
   * Checks whether a utility method name is valid and not yet used in the VSUM.
   *
   * @param authentication the current user's authentication
   * @param vsumId the VSUM ID
   * @param name the name to check
   * @param utilityMethodId the ID of the utility method being renamed; omitted for a new utility
   *     method
   * @return whether the name is available
   */
  @GetMapping("/name-availability")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<UtilityMethodNameAvailabilityResponse> checkName(
      KeycloakAuthentication authentication,
      @PathVariable Long vsumId,
      @RequestParam String name,
      @RequestParam(required = false) Long utilityMethodId) {
    String email = authentication.getParsedToken().getEmail();
    return ResponseEntity.ok(service.checkName(email, vsumId, name, utilityMethodId));
  }

  /**
   * Returns the Java utility class generated from all utility methods of the VSUM.
   *
   * @param authentication the current user's authentication
   * @param vsumId the VSUM ID
   * @return the class source and the problems of utility methods that are left out
   */
  @GetMapping("/java")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<UtilityMethodClassResponse> generatedClass(
      KeycloakAuthentication authentication, @PathVariable Long vsumId) {
    String email = authentication.getParsedToken().getEmail();
    return ResponseEntity.ok(service.generatedClass(email, vsumId));
  }

  /**
   * Returns the metamodel types utility methods of the VSUM can use.
   *
   * @param authentication the current user's authentication
   * @param vsumId the VSUM ID
   * @return the classes, enumerations and data types of the VSUM's metamodels
   */
  @GetMapping("/types")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<List<UtilityMethodTypeResponse>> availableTypes(
      KeycloakAuthentication authentication, @PathVariable Long vsumId) {
    String email = authentication.getParsedToken().getEmail();
    return ResponseEntity.ok(service.availableTypes(email, vsumId));
  }
}
