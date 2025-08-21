package com.vitruv.methodologist.user.service;

import static com.vitruv.methodologist.messages.Error.USER_ID_NOT_FOUND_ERROR;

import com.vitruv.methodologist.exception.ConflictException;
import com.vitruv.methodologist.exception.NotFoundException;
import com.vitruv.methodologist.user.controller.dto.KeycloakUser;
import com.vitruv.methodologist.user.controller.dto.request.UserPostRequest;
import com.vitruv.methodologist.user.controller.dto.request.UserPutRequest;
import com.vitruv.methodologist.user.controller.dto.response.UserResponse;
import com.vitruv.methodologist.user.mapper.UserMapper;
import com.vitruv.methodologist.user.model.User;
import com.vitruv.methodologist.user.model.repository.UserRepository;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service class for managing user operations. Handles business logic for creating, updating,
 * retrieving, and removing users.
 */
@Service
@Slf4j
public class UserService {
  private final UserMapper userMapper;
  private final UserRepository userRepository;
  private final KeycloakService keycloakService;

  /**
   * Constructs a new UserService with the specified UserMapper and UserRepository.
   *
   * @param userMapper the mapper for converting between user DTOs and entities
   * @param userRepository the repository for user persistence operations
   */
  public UserService(
      UserMapper userMapper, UserRepository userRepository, KeycloakService keycloakService) {
    this.userMapper = userMapper;
    this.userRepository = userRepository;
    this.keycloakService = keycloakService;
  }

  /**
   * Creates a new user from the provided sign-up request. Throws UserConflictException if the email
   * already exists.
   *
   * @param userPostRequest the request containing user sign-up information
   * @return the created User entity
   */
  @Transactional
  public User create(UserPostRequest userPostRequest) {
    userRepository
        .findByEmailIgnoreCase(userPostRequest.getEmail())
        .ifPresent(
            user -> {
              throw new ConflictException(userPostRequest.getEmail());
            });
    var user = userMapper.toUser(userPostRequest);

    var keycloakUser =
        KeycloakUser.builder()
            .firstName(user.getFirstName())
            .lastName(user.getLastName())
            .email(user.getEmail())
            .username(user.getUsername())
            .password(userPostRequest.getPassword())
            .role(user.getRoleType().getName())
            .roleType(user.getRoleType())
            .build();
    keycloakService.createUser(keycloakUser);
    userRepository.save(user);

    return user;
  }

  /**
   * Updates an existing user by ID with the provided update request. Throws NotFoundException if
   * the user is not found.
   *
   * @param id the ID of the user to update
   * @param userPutRequest the request containing updated user information
   * @return the updated User entity
   */
  @Transactional
  public User update(Long id, UserPutRequest userPutRequest) {
    var user =
        userRepository
            .findByIdAndRemovedAtIsNull(id)
            .orElseThrow(() -> new NotFoundException(USER_ID_NOT_FOUND_ERROR));
    userMapper.updateByUserPutRequest(userPutRequest, user);
    userRepository.save(user);

    return user;
  }

  /**
   * Retrieves a user by ID. Throws NotFoundException if the user is not found.
   *
   * @param id the ID of the user to retrieve
   * @return the UserResponse DTO containing user data
   */
  @Transactional
  public UserResponse findById(Long id) {
    var user =
        userRepository
            .findByIdAndRemovedAtIsNull(id)
            .orElseThrow(() -> new NotFoundException(USER_ID_NOT_FOUND_ERROR));
    return userMapper.toUserResponse(user);
  }

  /**
   * Marks a user as removed by setting the removedAt timestamp. Throws NotFoundException if the
   * user is not found.
   *
   * @param id the ID of the user to remove
   * @return the updated User entity with removedAt set
   */
  @Transactional
  public User remove(Long id) {
    var user =
        userRepository
            .findByIdAndRemovedAtIsNull(id)
            .orElseThrow(() -> new NotFoundException(USER_ID_NOT_FOUND_ERROR));
    user.setRemovedAt(Instant.now());
    userRepository.save(user);

    return user;
  }
}
