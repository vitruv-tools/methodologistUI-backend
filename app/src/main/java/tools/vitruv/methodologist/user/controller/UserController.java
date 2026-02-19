package tools.vitruv.methodologist.user.controller;

import static tools.vitruv.methodologist.messages.Message.NEW_PASSWORD_SENT_SUCCESSFULLY;
import static tools.vitruv.methodologist.messages.Message.RESEND_OTP_WAS_SUCCESSFULLY;
import static tools.vitruv.methodologist.messages.Message.SIGNUP_USER_SUCCESSFULLY;
import static tools.vitruv.methodologist.messages.Message.USER_REMOVED_SUCCESSFULLY;
import static tools.vitruv.methodologist.messages.Message.USER_UPDATED_SUCCESSFULLY;
import static tools.vitruv.methodologist.messages.Message.VERIFIED_USER_SUCCESSFULLY;
import static tools.vitruv.methodologist.messages.Message.YOUR_PASSWORD_CHANGE_WAS_SUCCESSFUL;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
import tools.vitruv.methodologist.ResponseTemplateDto;
import tools.vitruv.methodologist.config.KeycloakAuthentication;
import tools.vitruv.methodologist.user.controller.dto.request.PostAccessTokenByRefreshTokenRequest;
import tools.vitruv.methodologist.user.controller.dto.request.PostAccessTokenRequest;
import tools.vitruv.methodologist.user.controller.dto.request.UserPostForgotPasswordRequest;
import tools.vitruv.methodologist.user.controller.dto.request.UserPostRequest;
import tools.vitruv.methodologist.user.controller.dto.request.UserPutChangePasswordRequest;
import tools.vitruv.methodologist.user.controller.dto.request.UserPutRequest;
import tools.vitruv.methodologist.user.controller.dto.request.UserPutVerifyRequest;
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

  @GetMapping("/login/success")
  Map<String, Object> success(@AuthenticationPrincipal org.springframework.security.oauth2.core.oidc.user.OidcUser user) {
    return Map.of(
            "status", "ok",
            "sub", user.getSubject(),
            "email", user.getEmail(),
            "name", user.getFullName()
    );
  }

  /**
   * Verifies a one-time password (OTP) for the authenticated caller.
   *
   * <p>Endpoint requires the caller to have the `user` role. On success it returns a {@link
   * ResponseTemplateDto} with no data and a success message ({@code VERIFIED_USER_SUCCESSFULLY}).
   *
   * @param authentication the {@link KeycloakAuthentication} containing the caller's parsed token
   *     and email
   * @param userPutVerifyRequest the request payload containing OTP/verification data
   * @return a {@link ResponseTemplateDto} with a success message and no payload
   */
  @PutMapping("/v1/users/verify-otp")
  @PreAuthorize("hasRole('user')")
  public ResponseTemplateDto<Void> verifyOtp(
      KeycloakAuthentication authentication,
      @Valid @RequestBody UserPutVerifyRequest userPutVerifyRequest) {
    String callerEmail = authentication.getParsedToken().getEmail();
    userService.verifyOtp(callerEmail, userPutVerifyRequest);
    return ResponseTemplateDto.<Void>builder().message(VERIFIED_USER_SUCCESSFULLY).build();
  }

  /**
   * Sends a new one-time password (OTP) to the authenticated caller's email.
   *
   * <p>Accessible only to callers with the user role. On success returns a {@link
   * ResponseTemplateDto} with no data and a success message ({@code RESEND_OTP_WAS_SUCCESSFULLY}).
   *
   * @param authentication the {@link KeycloakAuthentication} containing the caller's parsed token
   *     and email
   * @return a {@link ResponseTemplateDto} with no payload and a success message
   */
  @GetMapping("/v1/users/resend-otp")
  @PreAuthorize("hasRole('user')")
  public ResponseTemplateDto<Void> resendOtp(KeycloakAuthentication authentication) {
    String callerEmail = authentication.getParsedToken().getEmail();
    userService.resendOtp(callerEmail);
    return ResponseTemplateDto.<Void>builder().message(RESEND_OTP_WAS_SUCCESSFULLY).build();
  }

  /**
   * Initiates a password reset for the specified email address.
   *
   * <p>Validates the incoming {@link UserPostForgotPasswordRequest}, delegates to {@code
   * userService.forgotPassword(...)} to generate and send a new password, and returns a response
   * with a success message when the email has been sent.
   *
   * @param userPostForgotPasswordRequest the validated request containing the email to reset; must
   *     not be null or blank
   * @return a {@link ResponseTemplateDto} with no payload and a success message ({@code
   *     NEW_PASSWORD_SENT_SUCCESSFULLY})
   * @throws jakarta.validation.ConstraintViolationException if validation of the request fails
   * @throws RuntimeException if the password reset process fails (underlying exceptions will
   *     propagate)
   */
  @PostMapping("/v1/users/forgot-password")
  public ResponseTemplateDto<Void> forgotPassword(
      @Valid @RequestBody UserPostForgotPasswordRequest userPostForgotPasswordRequest) {
    userService.forgotPassword(userPostForgotPasswordRequest);
    return ResponseTemplateDto.<Void>builder().message(NEW_PASSWORD_SENT_SUCCESSFULLY).build();
  }

  /**
   * Changes the authenticated caller's password.
   *
   * <p>Requires the caller to have the `user` role. Extracts the caller's email from the provided
   * {@link KeycloakAuthentication}, delegates validation and update to {@code userService}, and
   * returns a response containing a success message.
   *
   * @param authentication the {@link KeycloakAuthentication} containing the caller's parsed token
   *     and email
   * @param userPutChangePasswordRequest validated request DTO containing the new password
   * @return a {@link ResponseTemplateDto} with no payload and a success message
   * @throws RuntimeException if the service fails to change the password (underlying exceptions
   *     will propagate)
   */
  @PutMapping("/v1/users/change-password")
  @PreAuthorize("hasRole('user')")
  public ResponseTemplateDto<Void> changePassword(
      KeycloakAuthentication authentication,
      @Valid @RequestBody UserPutChangePasswordRequest userPutChangePasswordRequest) {
    String callerEmail = authentication.getParsedToken().getEmail();
    userService.changePassword(callerEmail, userPutChangePasswordRequest);
    return ResponseTemplateDto.<Void>builder().message(YOUR_PASSWORD_CHANGE_WAS_SUCCESSFUL).build();
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
        .data(userService.findByEmail(callerEmail))
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

  /**
   * Searches for users by name or email, excluding the authenticated caller.
   *
   * <p>Accessible only to users with the 'user' role. Results are paginated and can be filtered by
   * a query parameter matching email, first name, or last name (case-insensitive, partial match).
   *
   * @param authentication the Keycloak authentication object containing caller details
   * @param queryParam the search term to filter users by name or email (optional)
   * @param pageNumber the page number for pagination (default is 0)
   * @param pageSize the number of results per page (default is 50)
   * @return a response template containing the paginated list of matching users
   */
  @GetMapping("/v1/users/search")
  @PreAuthorize("hasRole('user')")
  public ResponseTemplateDto<List<UserResponse>> searchUserByNameAndEmail(
      KeycloakAuthentication authentication,
      @RequestParam(defaultValue = "") String queryParam,
      @RequestParam(defaultValue = "0") int pageNumber,
      @RequestParam(defaultValue = "50") int pageSize) {
    String callerEmail = authentication.getParsedToken().getEmail();
    Pageable pageable = PageRequest.of(pageNumber, pageSize);
    return ResponseTemplateDto.<List<UserResponse>>builder()
        .data(userService.searchUserByNameAndEmail(callerEmail, queryParam, pageable))
        .build();
  }
}
