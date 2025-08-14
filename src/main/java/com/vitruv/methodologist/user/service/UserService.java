package com.vitruv.methodologist.user.service;

import com.vitruv.methodologist.exception.NotFoundException;
import com.vitruv.methodologist.exception.UserConflictException;
import com.vitruv.methodologist.user.controller.dto.request.UserPostRequest;
import com.vitruv.methodologist.user.controller.dto.request.UserPutRequest;
import com.vitruv.methodologist.user.controller.dto.response.UserResponse;
import com.vitruv.methodologist.user.mapper.UserMapper;
import com.vitruv.methodologist.user.model.User;
import com.vitruv.methodologist.user.model.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static com.vitruv.methodologist.messages.Error.USER_ID_NOT_FOUND_ERROR;

@Service
@Slf4j
public class UserService {
  private final UserMapper userMapper;
  private final UserRepository userRepository;

  public UserService(UserMapper userMapper, UserRepository userRepository) {
    this.userMapper = userMapper;
    this.userRepository = userRepository;
  }

  @Transactional
  public User create(UserPostRequest userPostRequest) {
    userRepository
        .findByEmailIgnoreCase(userPostRequest.getEmail())
        .ifPresent(
            user -> {
              throw new UserConflictException(userPostRequest.getEmail());
            });
    var user = userMapper.toUser(userPostRequest);
    userRepository.save(user);

    return user;
  }

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

  @Transactional
  public UserResponse findById(Long id) {
    var user =
        userRepository
            .findByIdAndRemovedAtIsNull(id)
            .orElseThrow(() -> new NotFoundException(USER_ID_NOT_FOUND_ERROR));
    return userMapper.toUserResponse(user);
  }

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
