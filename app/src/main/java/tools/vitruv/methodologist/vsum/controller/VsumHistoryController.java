package tools.vitruv.methodologist.vsum.controller;

import static tools.vitruv.methodologist.messages.Message.VSUM_HISTORY_REVERT_WAS_SUCCESSFULLY;

import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.vitruv.methodologist.ResponseTemplateDto;
import tools.vitruv.methodologist.config.KeycloakAuthentication;
import tools.vitruv.methodologist.vsum.controller.dto.response.VsumHistoryResponse;
import tools.vitruv.methodologist.vsum.service.VsumHistoryService;

/**
 * REST controller for managing VSUM history snapshots.
 *
 * <p>Exposes endpoints for creating and retrieving VSUM history records via {@link
 * VsumHistoryService}.
 */
@RestController
@RequestMapping("/api/")
@Validated
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class VsumHistoryController {
  VsumHistoryService vsumHistoryService;

  /**
   * Retrieves VSUM history snapshots for the authenticated caller and the specified VSUM id.
   *
   * <p>The caller's email is extracted from the provided {@code KeycloakAuthentication} and used to
   * filter results. This method delegates to {@link VsumHistoryService#findAllByVsumId(String,
   * String)} which returns a list of {@link
   * tools.vitruv.methodologist.vsum.controller.dto.response.VsumHistoryResponse} DTOs ordered by
   * creation time.
   *
   * @param authentication the authenticated caller's Keycloak token; used to obtain the caller
   *     email
   * @param vsumId the VSUM identifier to filter history entries by
   * @return a {@link ResponseTemplateDto} containing a list of {@link
   *     tools.vitruv.methodologist.vsum.controller.dto.response.VsumHistoryResponse}; never {@code
   *     null}
   */
  @GetMapping("/v1/vsum-histories/find-all/vsumId={vsumId}")
  @PreAuthorize("hasRole('user')")
  public ResponseTemplateDto<List<VsumHistoryResponse>> findAllByUser(
      KeycloakAuthentication authentication, @PathVariable Long vsumId) {
    String callerEmail = authentication.getParsedToken().getEmail();
    return ResponseTemplateDto.<List<VsumHistoryResponse>>builder()
        .data(vsumHistoryService.findAllByVsumId(callerEmail, vsumId))
        .build();
  }

  /**
   * Revert the VSUM to the state captured by the specified history entry.
   *
   * <p>Requires the caller to have role `user`. The caller's email is extracted from the provided
   * {@code KeycloakAuthentication} and used to authorize the revert operation. The work is
   * delegated to {@link VsumHistoryService#revert(String, Long)} which may throw runtime exceptions
   * (for example when the history entry or permission is not found).
   *
   * @param authentication the caller's Keycloak authentication token; used to obtain the caller
   *     email
   * @param id the identifier of the VSUM history entry to revert to
   * @return a {@link ResponseTemplateDto} containing a success message when the revert completes
   */
  @PutMapping("/v1/vsum-histories/{id}/revert")
  @PreAuthorize("hasRole('user')")
  public ResponseTemplateDto<List<VsumHistoryResponse>> revert(
      KeycloakAuthentication authentication, @PathVariable Long id) {
    String callerEmail = authentication.getParsedToken().getEmail();
    vsumHistoryService.revert(callerEmail, id);
    return ResponseTemplateDto.<List<VsumHistoryResponse>>builder()
        .message(VSUM_HISTORY_REVERT_WAS_SUCCESSFULLY)
        .build();
  }
}
