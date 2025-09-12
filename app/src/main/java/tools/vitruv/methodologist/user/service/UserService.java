package tools.vitruv.methodologist.user.service;

import static tools.vitruv.methodologist.messages.Error.USER_ID_NOT_FOUND_ERROR;

import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.vitruv.methodologist.apihandler.KeycloakApiHandler;
import tools.vitruv.methodologist.apihandler.dto.response.KeycloakWebToken;
import tools.vitruv.methodologist.exception.EmailExistsException;
import tools.vitruv.methodologist.exception.NotFoundException;
import tools.vitruv.methodologist.exception.UnauthorizedException;
import tools.vitruv.methodologist.user.controller.dto.KeycloakUser;
import tools.vitruv.methodologist.user.controller.dto.request.PostAccessTokenByRefreshTokenRequest;
import tools.vitruv.methodologist.user.controller.dto.request.PostAccessTokenRequest;
import tools.vitruv.methodologist.user.controller.dto.request.UserPostRequest;
import tools.vitruv.methodologist.user.controller.dto.request.UserPutRequest;
import tools.vitruv.methodologist.user.controller.dto.response.UserResponse;
import tools.vitruv.methodologist.user.controller.dto.response.UserWebToken;
import tools.vitruv.methodologist.user.mapper.UserMapper;
import tools.vitruv.methodologist.user.model.User;
import tools.vitruv.methodologist.user.model.repository.UserRepository;

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
  private final KeycloakApiHandler keycloakApiHandler;

  /**
   * Constructs a new UserService with the specified UserMapper and UserRepository.
   *
   * @param userMapper the mapper for converting between user DTOs and entities
   * @param userRepository the repository for user persistence operations
   */
  public UserService(
      UserMapper userMapper,
      UserRepository userRepository,
      KeycloakService keycloakService,
      KeycloakApiHandler keycloakApiHandler) {
    this.userMapper = userMapper;
    this.userRepository = userRepository;
    this.keycloakService = keycloakService;
    this.keycloakApiHandler = keycloakApiHandler;
  }

  /**
   * Retrieves a user access token using the provided username and password. The request is
   * validated through Keycloak, and if successful, the response is converted into an
   * application-specific UserWebToken. If validation fails or an unexpected error occurs, an
   * UnauthorizedException is thrown.
   */
  @Transactional
  public UserWebToken getAccessToken(PostAccessTokenRequest postAccessTokenRequest) {
    try {
      KeycloakWebToken response =
          keycloakApiHandler.getAccessTokenOrThrow(
              postAccessTokenRequest.getUsername(), postAccessTokenRequest.getPassword());

      return userMapper.toUserWebToken(response);
    } catch (Exception e) {
      throw new UnauthorizedException();
    }
  }

  /**
   * Retrieves a new access token using the provided refresh token. The refresh token is sent to
   * Keycloak for validation and exchange. If successful, the response is converted into a
   * UserWebToken. If validation fails or an error occurs, an UnauthorizedException is thrown.
   */
  @Transactional
  public UserWebToken getAccessTokenByRefreshToken(
      PostAccessTokenByRefreshTokenRequest postAccessTokenByRefreshTokenRequest) {
    try {
      KeycloakWebToken response =
          keycloakApiHandler.getAccessTokenByRefreshToken(
              postAccessTokenByRefreshTokenRequest.getRefreshToken());

      return userMapper.toUserWebToken(response);
    } catch (Exception e) {
      throw new UnauthorizedException();
    }
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
    checkEmailExistsOrThrow(userPostRequest.getEmail());
    User user = userMapper.toUser(userPostRequest);

    KeycloakUser keycloakUser =
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
   * Checks if a user with the given email already exists in either the application database or
   * Keycloak authentication system.
   *
   * @param email the email address to check for existence
   * @throws EmailExistsException if the email already exists in either system
   */
  private void checkEmailExistsOrThrow(String email) {
    if (userRepository.findByEmailIgnoreCase(email).isPresent()
        || keycloakService.existUser(email)) {
      throw new EmailExistsException(email);
    }
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
    User user =
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
    User user =
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
    User user =
        userRepository
            .findByIdAndRemovedAtIsNull(id)
            .orElseThrow(() -> new NotFoundException(USER_ID_NOT_FOUND_ERROR));
    user.setRemovedAt(Instant.now());
    userRepository.save(user);
    return user;
  }
}
