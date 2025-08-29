package tools.vitruv.methodologist.user.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;
import tools.vitruv.methodologist.apihandler.dto.response.KeycloakWebToken;
import tools.vitruv.methodologist.user.controller.dto.request.UserPostRequest;
import tools.vitruv.methodologist.user.controller.dto.request.UserPutRequest;
import tools.vitruv.methodologist.user.controller.dto.response.UserResponse;
import tools.vitruv.methodologist.user.controller.dto.response.UserWebToken;
import tools.vitruv.methodologist.user.model.User;

/**
 * MapStruct mapper for converting between User entities and DTOs. Handles mapping for user
 * creation, update, and response objects.
 */
@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = "spring")
public interface UserMapper {

  /**
   * Converts a {@link tools.vitruv.methodologist.user.controller.dto.request.UserPostRequest} DTO
   * to a {@link tools.vitruv.methodologist.user.model.User} entity.
   *
   * @param userDto the user post request DTO
   * @return the mapped {@link tools.vitruv.methodologist.user.model.User} entity
   */
  User toUser(UserPostRequest userDto);

  /**
   * Converts a {@link tools.vitruv.methodologist.user.model.User} entity to a {@link
   * tools.vitruv.methodologist.user.controller.dto.response.UserResponse} DTO.
   *
   * @param user the user entity
   * @return the mapped {@link tools.vitruv.methodologist.user.controller.dto.response.UserResponse}
   *     DTO
   */
  UserResponse toUserResponse(User user);

  /**
   * Updates an existing {@link tools.vitruv.methodologist.user.model.User} entity with data from a
   * {@link tools.vitruv.methodologist.user.controller.dto.request.UserPutRequest} DTO.
   *
   * @param userPutRequest the user put request DTO containing updated data
   * @param user the target user entity to update
   */
  void updateByUserPutRequest(UserPutRequest userPutRequest, @MappingTarget User user);

  /**
   * Converts a {@link tools.vitruv.methodologist.apihandler.dto.response.KeycloakWebToken} into an
   * application-specific {@link
   * tools.vitruv.methodologist.user.controller.dto.response.UserWebToken}. This allows the system
   * to map Keycloak token data into the format expected by internal services and clients.
   */
  UserWebToken toUserWebToken(KeycloakWebToken keycloakWebToken);
}
