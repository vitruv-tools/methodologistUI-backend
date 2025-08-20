package com.vitruv.methodologist.user.mapper;

import com.vitruv.methodologist.user.controller.dto.KeycloakUser;
import com.vitruv.methodologist.user.controller.dto.request.UserPostRequest;
import com.vitruv.methodologist.user.controller.dto.request.UserPutRequest;
import com.vitruv.methodologist.user.controller.dto.response.UserResponse;
import com.vitruv.methodologist.user.model.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapper for converting between User entities and DTOs.
 * Handles mapping for user creation, update, and response objects.
 */
@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = "spring")
public interface UserMapper {

  /**
   * Converts a {@link UserPostRequest} DTO to a {@link User} entity.
   *
   * @param userDto the user post request DTO
   * @return the mapped {@link User} entity
   */
  User toUser(UserPostRequest userDto);

  /**
   * Converts a {@link User} entity to a {@link UserResponse} DTO.
   *
   * @param user the user entity
   * @return the mapped {@link UserResponse} DTO
   */
  UserResponse toUserResponse(User user);

  /**
   * Updates an existing {@link User} entity with data from a {@link UserPutRequest} DTO.
   *
   * @param userPutRequest the user put request DTO containing updated data
   * @param user the target user entity to update
   */
  void updateByUserPutRequest(UserPutRequest userPutRequest, @MappingTarget User user);
}
