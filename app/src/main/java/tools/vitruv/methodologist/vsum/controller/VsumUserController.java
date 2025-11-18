package tools.vitruv.methodologist.vsum.controller;

import static tools.vitruv.methodologist.messages.Message.VSUM_USER_CREATED_SUCCESSFULLY;
import static tools.vitruv.methodologist.messages.Message.VSUM_USER_DELETED_SUCCESSFULLY;

import jakarta.validation.Valid;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.vitruv.methodologist.ResponseTemplateDto;
import tools.vitruv.methodologist.config.KeycloakAuthentication;
import tools.vitruv.methodologist.vsum.controller.dto.request.VsumUserPostRequest;
import tools.vitruv.methodologist.vsum.controller.dto.response.VsumUserResponse;
import tools.vitruv.methodologist.vsum.service.VsumUserService;

/**
 * REST controller for managing VSUM (Virtual Single Underlying Model) resources. Provides endpoints
 * for CRUD operations on VSUMs.
 */
@RestController
@RequestMapping("/api/")
@Validated
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class VsumUserController {
  VsumUserService vsumUserService;

  /**
   * Adds a new member to a VSUM. Only authenticated users with the 'user' role can access this
   * endpoint. Delegates the member addition logic to the service layer.
   *
   * @param authentication the Keycloak authentication token containing caller information
   * @param vsumUserPostRequest the request payload with VSUM and user details
   * @return a response template with a success message if the member is added successfully
   * @throws NotFoundException if the VSUM or user is not found
   * @throws OwnerRequiredException if the caller is not the owner of the VSUM
   * @throws IllegalArgumentException if the owner tries to add themselves as a member
   * @throws VsumUserAlreadyMemberException if the user is already a member of the VSUM
   */
  @PostMapping("/v1/vsum-users/add-member")
  @PreAuthorize("hasRole('user')")
  public ResponseTemplateDto<Void> addMember(
      KeycloakAuthentication authentication,
      @Valid @RequestBody VsumUserPostRequest vsumUserPostRequest) {
    String callerEmail = authentication.getParsedToken().getEmail();
    vsumUserService.addMember(callerEmail, vsumUserPostRequest);
    return ResponseTemplateDto.<Void>builder().message(VSUM_USER_CREATED_SUCCESSFULLY).build();
  }

  /**
   * Retrieves all members of a specified VSUM. Only authenticated users with the 'user' role can
   * access this endpoint. Delegates the member retrieval logic to the service layer.
   *
   * @param authentication the Keycloak authentication token containing caller information
   * @param vsumId the ID of the VSUM to retrieve members for
   * @return a response template containing a list of VSUM user responses
   */
  @GetMapping("/v1/vsum-users/vsumId={vsumId}")
  @PreAuthorize("hasRole('user')")
  public ResponseTemplateDto<List<VsumUserResponse>> findAllMemberByVsum(
      KeycloakAuthentication authentication, @PathVariable Long vsumId) {
    String callerEmail = authentication.getParsedToken().getEmail();
    List<VsumUserResponse> response = vsumUserService.findAllMemberByVsum(callerEmail, vsumId);
    return ResponseTemplateDto.<List<VsumUserResponse>>builder().data(response).build();
  }

  /**
   * Removes a member from a VSUM. Only authenticated users with the 'user' role can access this
   * endpoint. Delegates the member removal logic to the service layer.
   *
   * @param authentication the Keycloak authentication token containing caller information
   * @param id the ID of the VSUM user relationship to delete
   * @return a response template with a success message if the member is removed successfully
   * @throws NotFoundException if the member to be removed is not found
   * @throws OwnerRequiredException if the caller is not the owner of the VSUM
   * @throws IllegalArgumentException if attempting to remove the owner role
   */
  @DeleteMapping("/v1/vsum-users/{id}/remove-member")
  @PreAuthorize("hasRole('user')")
  public ResponseTemplateDto<Void> deleteMember(
      KeycloakAuthentication authentication, @PathVariable Long id) {
    String callerEmail = authentication.getParsedToken().getEmail();
    vsumUserService.deleteMember(callerEmail, id);
    return ResponseTemplateDto.<Void>builder().message(VSUM_USER_DELETED_SUCCESSFULLY).build();
  }
}
