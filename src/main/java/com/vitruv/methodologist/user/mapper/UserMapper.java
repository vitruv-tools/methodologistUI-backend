package com.vitruv.methodologist.user.mapper;

import com.vitruv.methodologist.user.controller.dto.request.UserPostRequest;
import com.vitruv.methodologist.user.controller.dto.request.UserPutRequest;
import com.vitruv.methodologist.user.controller.dto.response.UserResponse;
import com.vitruv.methodologist.user.model.User;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapper for converting between User entities and DTOs.
 * Handles mapping for user creation, update, and response objects.
 */
@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = "spring")
public interface UserMapper {

  User toUser(UserPostRequest userDto);

  UserResponse toUserResponse(User user);

  void updateByUserPutRequest(UserPutRequest userPutRequest, @MappingTarget User user);
}
