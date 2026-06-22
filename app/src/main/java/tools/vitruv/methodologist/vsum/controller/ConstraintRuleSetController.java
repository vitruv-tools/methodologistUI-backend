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

  public ConstraintRuleSetController(ConstraintRuleSetService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<List<RuleSetResponse>> getAll(@PathVariable Long vsumId) {
    return ResponseEntity.ok(service.findAll(vsumId));
  }

  @PostMapping
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<RuleSetResponse> create(
      KeycloakAuthentication authentication,
      @PathVariable Long vsumId,
      @Valid @RequestBody RuleSetPostRequest request) {
    String email = authentication.getParsedToken().getEmail();
    return ResponseEntity.status(HttpStatus.CREATED).body(service.create(email, vsumId, request));
  }

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

  @DeleteMapping("/{ruleSetId}")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<Void> delete(
      @PathVariable Long vsumId, @PathVariable Long ruleSetId) {
    service.delete(vsumId, ruleSetId);
    return ResponseEntity.noContent().build();
  }
}
