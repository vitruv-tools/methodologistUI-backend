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
import org.springframework.web.bind.annotation.RestController;
import tools.vitruv.methodologist.config.KeycloakAuthentication;
import tools.vitruv.methodologist.vsum.controller.dto.request.RuleSetPostRequest;
import tools.vitruv.methodologist.vsum.controller.dto.request.RuleSetPutRequest;
import tools.vitruv.methodologist.vsum.controller.dto.response.RuleSetResponse;
import tools.vitruv.methodologist.vsum.service.ConstraintRuleSetService;

/** REST controller for OCL constraint rule sets per VSUM. */
@RestController
@RequestMapping("/api/v1/vsums/{vsumId}/rule-sets")
@Validated
public class ConstraintRuleSetController {

  private final ConstraintRuleSetService service;

  /**
   * Constructs a new {@code ConstraintRuleSetController}.
   *
   * @param service the rule set service
   */
  public ConstraintRuleSetController(ConstraintRuleSetService service) {
    this.service = service;
  }

  /**
   * Returns all rule sets for the given VSUM.
   *
   * @param authentication the current user's authentication
   * @param vsumId the VSUM ID
   * @return list of rule set responses
   */
  @GetMapping
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<List<RuleSetResponse>> getAll(
      KeycloakAuthentication authentication, @PathVariable Long vsumId) {
    String email = authentication.getParsedToken().getEmail();
    return ResponseEntity.ok(service.findAll(email, vsumId));
  }

  /**
   * Creates a new rule set for the given VSUM.
   *
   * @param authentication the current user's authentication
   * @param vsumId the VSUM ID
   * @param request the creation request
   * @return the created rule set with HTTP 201
   */
  @PostMapping
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<RuleSetResponse> create(
      KeycloakAuthentication authentication,
      @PathVariable Long vsumId,
      @Valid @RequestBody RuleSetPostRequest request) {
    String email = authentication.getParsedToken().getEmail();
    return ResponseEntity.status(HttpStatus.CREATED).body(service.create(email, vsumId, request));
  }

  /**
   * Updates an existing rule set.
   *
   * @param authentication the current user's authentication
   * @param vsumId the VSUM ID
   * @param ruleSetId the rule set ID
   * @param request the update request
   * @return the updated rule set response
   */
  @PutMapping("/{ruleSetId}")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<RuleSetResponse> update(
      KeycloakAuthentication authentication,
      @PathVariable Long vsumId,
      @PathVariable Long ruleSetId,
      @Valid @RequestBody RuleSetPutRequest request) {
    String email = authentication.getParsedToken().getEmail();
    return ResponseEntity.ok(service.update(email, vsumId, ruleSetId, request));
  }

  /**
   * Deletes a rule set by ID.
   *
   * @param authentication the current user's authentication
   * @param vsumId the VSUM ID
   * @param ruleSetId the rule set ID to delete
   * @return HTTP 204 No Content
   */
  @DeleteMapping("/{ruleSetId}")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<Void> delete(
      KeycloakAuthentication authentication,
      @PathVariable Long vsumId,
      @PathVariable Long ruleSetId) {
    String email = authentication.getParsedToken().getEmail();
    service.delete(email, vsumId, ruleSetId);
    return ResponseEntity.noContent().build();
  }
}
