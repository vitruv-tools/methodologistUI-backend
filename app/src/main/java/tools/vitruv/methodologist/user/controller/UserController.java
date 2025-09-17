package tools.vitruv.methodologist.user.controller;

import static tools.vitruv.methodologist.messages.Message.SIGNUP_USER_SUCCESSFULLY;
import static tools.vitruv.methodologist.messages.Message.USER_REMOVED_SUCCESSFULLY;
import static tools.vitruv.methodologist.messages.Message.USER_UPDATED_SUCCESSFULLY;

import jakarta.validation.Valid;
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
import tools.vitruv.methodologist.ResponseTemplateDto;
import tools.vitruv.methodologist.config.KeycloakAuthentication;
import tools.vitruv.methodologist.user.controller.dto.request.PostAccessTokenByRefreshTokenRequest;
import tools.vitruv.methodologist.user.controller.dto.request.PostAccessTokenRequest;
import tools.vitruv.methodologist.user.controller.dto.request.UserPostRequest;
import tools.vitruv.methodologist.user.controller.dto.request.UserPutRequest;
import tools.vitruv.methodologist.user.controller.dto.response.UserResponse;
import tools.vitruv.methodologist.user.controller.dto.response.UserWebToken;
import tools.vitruv.methodologist.user.service.UserService;

/**
 * REST controller for managing user operations. Provides endpoints for user sign-up, retrieval,
 * update, and deletion.
 */
@RestController
@RequestMapping("/api/")
@Validated
public class UserController {
  private final UserService userService;

  /**
   * Constructs a new UserController with the specified UserService.
   *
   * @param userService the service handling user business logic operations
   */
  public UserController(UserService userService) {
    this.userService = userService;
  }

  /**
   * Handles the login request and returns a user access token. The provided credentials are
   * validated and exchanged for a token that can be used to access secured endpoints.
   */
  @PostMapping("/v1/users/login")
  public UserWebToken getAccessToken(
      @Valid @RequestBody PostAccessTokenRequest postAccessTokenRequest) {
    return userService.getAccessToken(postAccessTokenRequest);
  }

  /**
   * Issues a new access token using a valid refresh token. The refresh token is validated and
   * exchanged for a fresh access token without requiring the user to log in again.
   */
  @PostMapping("/v1/users/access-token/by-refresh-token")
  public UserWebToken getAccessTokenByRefreshToken(
      @Valid @RequestBody
          PostAccessTokenByRefreshTokenRequest postAccessTokenByRefreshTokenRequest) {
    return userService.getAccessTokenByRefreshToken(postAccessTokenByRefreshTokenRequest);
  }

  /**
   * Registers a new user.
   *
   * @param userPostRequest the request body containing user sign-up information
   * @return a response template indicating successful sign-up
   */
  @PostMapping("/v1/users/sign-up")
  public ResponseTemplateDto<Void> create(@Valid @RequestBody UserPostRequest userPostRequest) {
    userService.create(userPostRequest);
    return ResponseTemplateDto.<Void>builder().message(SIGNUP_USER_SUCCESSFULLY).build();
  }

  /**
   * Retrieves the authenticated user's information. This endpoint requires the user to have the
   * 'user' role.
   *
   * @param authentication the Keycloak authentication object containing user details
   * @return ResponseTemplateDto containing the user's information
   */
  @GetMapping("/v1/users")
  @PreAuthorize("hasRole('user')")
  public ResponseTemplateDto<UserResponse> findByCallerEmail(
      KeycloakAuthentication authentication) {
    String callerEmail = authentication.getParsedToken().getEmail();
    return ResponseTemplateDto.<UserResponse>builder()
        .data(userService.findByCallerEmail(callerEmail))
        .build();
  }

  /**
   * Updates an existing user by ID.
   *
   * @param id the ID of the user to update
   * @param userPutRequest the request body containing updated user information
   * @return a response template indicating successful update
   */
  @PutMapping("/v1/users/{id}")
  public ResponseTemplateDto<Void> update(
      @PathVariable Long id, @Valid @RequestBody UserPutRequest userPutRequest) {
    userService.update(id, userPutRequest);
    return ResponseTemplateDto.<Void>builder().message(USER_UPDATED_SUCCESSFULLY).build();
  }

  /**
   * Removes a user by ID.
   *
   * @param id the ID of the user to remove
   * @return a response template indicating successful removal
   */
  @DeleteMapping("/v1/users/{id}")
  public ResponseTemplateDto<Void> remove(@PathVariable Long id) {
    userService.remove(id);
    return ResponseTemplateDto.<Void>builder().message(USER_REMOVED_SUCCESSFULLY).build();
  }
}
