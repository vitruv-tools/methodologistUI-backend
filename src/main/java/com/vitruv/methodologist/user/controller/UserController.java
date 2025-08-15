package com.vitruv.methodologist.user.controller;

import com.vitruv.methodologist.ResponseTemplateDto;
import com.vitruv.methodologist.user.controller.dto.request.UserPostRequest;
import com.vitruv.methodologist.user.controller.dto.request.UserPutRequest;
import com.vitruv.methodologist.user.controller.dto.response.UserResponse;
import com.vitruv.methodologist.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static com.vitruv.methodologist.messages.Message.*;

/**
 * REST controller for managing user operations.
 * Provides endpoints for user sign-up, retrieval, update, and deletion.
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
   * Registers a new user.
   *
   * @param userPostRequest the request body containing user sign-up information
   * @return a response template indicating successful sign-up
   */
  @PostMapping("/v1/users/sign-up")
  public ResponseTemplateDto<Void> createUser(@Valid @RequestBody UserPostRequest userPostRequest) {
    userService.create(userPostRequest);
    return ResponseTemplateDto.<Void>builder().message(SIGNUP_USER_SUCCESSFULLY).build();
  }

  /**
   * Retrieves a user by ID.
   *
   * @param id the ID of the user to retrieve
   * @return a response template containing the user data
   */
  @GetMapping("/v1/users/{id}")
  public ResponseTemplateDto<UserResponse> createUser(@PathVariable Long id) {
    return ResponseTemplateDto.<UserResponse>builder().data(userService.findById(id)).build();
  }

  /**
   * Updates an existing user by ID.
   *
   * @param id the ID of the user to update
   * @param userPutRequest the request body containing updated user information
   * @return a response template indicating successful update
   */
  @PutMapping("/v1/users/{id}")
  public ResponseTemplateDto<Void> createUser(
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
