package tools.vitruv.methodologist.vsum.controller;

import static tools.vitruv.methodologist.messages.Message.VSUM_UPDATED_SUCCESSFULLY;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.vitruv.methodologist.ResponseTemplateDto;
import tools.vitruv.methodologist.vsum.controller.dto.request.VsumSyncChangesPutRequest;
import tools.vitruv.methodologist.vsum.controller.dto.response.VsumMemberAccessResponse;
import tools.vitruv.methodologist.vsum.controller.dto.response.VsumMetaModelResponse;
import tools.vitruv.methodologist.vsum.service.VsumService;

/**
 * Internal REST endpoints for the collaboration server, which loads projects and writes the changes
 * of their users back, also when no user is connected anymore. It also lists a project's members
 * when it checks its open connections again, after the backend told it that access to the project
 * was taken away.
 *
 * <p>Only the collaboration server's service account (realm role {@code collaboration-service}) may
 * call them, and it may do so for any project, without a membership check. User tokens get 403.
 */
@RestController
@RequestMapping("/api/internal/")
@Validated
public class InternalVsumController {

  private final VsumService vsumService;

  /**
   * Constructs a new internal VSUM controller.
   *
   * @param vsumService the service component for VSUM operations
   */
  public InternalVsumController(VsumService vsumService) {
    this.vsumService = vsumService;
  }

  /**
   * Retrieves the details of any VSUM. The response is the same as for {@code GET
   * /api/v1/vsums/{id}/details}, except that it has no role and no user ID, because the service
   * account is not a member.
   *
   * @param id the identifier of the VSUM to retrieve
   * @return a {@link ResponseTemplateDto} wrapping the detailed {@link VsumMetaModelResponse}
   */
  @GetMapping("/v1/vsums/{id}/details")
  @PreAuthorize("hasRole('collaboration-service')")
  public ResponseTemplateDto<VsumMetaModelResponse> findVsumWithDetails(@PathVariable Long id) {
    return ResponseTemplateDto.<VsumMetaModelResponse>builder()
        .data(vsumService.findVsumWithDetailsForService(id))
        .build();
  }

  /**
   * Lists the active users of any VSUM with their roles, the owner included. Users who removed
   * their account are left out. Every user is listed once, with the role {@code GET
   * /api/v1/vsums/{id}/details} returns to them. Answers {@code 404} if the VSUM is missing or in
   * the trash.
   *
   * @param id the identifier of the VSUM
   * @return a {@link ResponseTemplateDto} wrapping the user ID and role of every active user
   */
  @GetMapping("/v1/vsums/{id}/members")
  @PreAuthorize("hasRole('collaboration-service')")
  public ResponseTemplateDto<List<VsumMemberAccessResponse>> findMembers(@PathVariable Long id) {
    return ResponseTemplateDto.<List<VsumMemberAccessResponse>>builder()
        .data(vsumService.findMembersForService(id))
        .build();
  }

  /**
   * Synchronizes any VSUM with the desired state. Takes the same request as {@code PUT
   * /api/v1/vsums/{id}/sync-changes}; the VSUM's owner is the acting user and no history entry is
   * created.
   *
   * @param id the identifier of the VSUM to update
   * @param vsumSyncChangesPutRequest the request containing the desired state of the VSUM
   * @return response indicating successful VSUM update
   */
  @PutMapping("/v1/vsums/{id}/sync-changes")
  @PreAuthorize("hasRole('collaboration-service')")
  public ResponseTemplateDto<Void> syncChanges(
      @PathVariable Long id,
      @Valid @RequestBody VsumSyncChangesPutRequest vsumSyncChangesPutRequest) {
    vsumService.updateForService(id, vsumSyncChangesPutRequest);
    return ResponseTemplateDto.<Void>builder().message(VSUM_UPDATED_SUCCESSFULLY).build();
  }
}
