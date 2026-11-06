package tools.vitruv.methodologist.vsum.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.vitruv.methodologist.ResponseTemplateDto;
import tools.vitruv.methodologist.config.KeycloakAuthentication;
import tools.vitruv.methodologist.vsum.controller.dto.response.VsumValidationResponse;
import tools.vitruv.methodologist.vsum.service.VsumValidationService;

/**
 * Latest validation result of a VSUM. Validations run in the background after changes to the VSUM
 * have been saved.
 */
@RestController
@RequestMapping("/api/")
@RequiredArgsConstructor
public class VsumValidationController {

  private final VsumValidationService vsumValidationService;

  /**
   * Returns the latest validation result of the VSUM.
   *
   * @param authentication the authenticated Keycloak principal
   * @param id the VSUM identifier
   * @return the latest result, or {@code null} data if the VSUM has not been validated yet
   * @throws org.springframework.security.access.AccessDeniedException if the caller is not an owner
   *     or member of the VSUM
   */
  @GetMapping("/v1/vsums/{id}/validation")
  @PreAuthorize("hasRole('user')")
  public ResponseTemplateDto<VsumValidationResponse> findLatest(
      KeycloakAuthentication authentication, @PathVariable Long id) {
    String callerEmail = authentication.getParsedToken().getEmail();
    return ResponseTemplateDto.<VsumValidationResponse>builder()
        .data(vsumValidationService.findLatestResult(callerEmail, id).orElse(null))
        .build();
  }
}
