package com.vitruv.methodologist.user.service;

import com.vitruv.methodologist.exception.NotFoundException;
import com.vitruv.methodologist.exception.UserConflictException;
import com.vitruv.methodologist.user.controller.dto.request.UserPostRequest;
import com.vitruv.methodologist.user.controller.dto.request.UserPutRequest;
import com.vitruv.methodologist.user.mapper.UserMapper;
import com.vitruv.methodologist.user.model.User;
import com.vitruv.methodologist.user.model.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.function.Consumer;

import static com.vitruv.methodologist.messages.Error.USER_ALREADY_EXIST_ERROR;
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
    public void create(UserPostRequest userPostRequest) {
        userRepository.findByEmailIgnoreCase(userPostRequest.getEmail()).ifPresentOrElse(u -> {
            throw new UserConflictException(userPostRequest.getEmail(), USER_ALREADY_EXIST_ERROR);
        }, () -> {
            var user = userMapper.toUser(userPostRequest);
            userRepository.save(user);
        });
    }

    @Transactional
    public void update(Long id, UserPutRequest userPutRequest) {
        var user = userRepository.findByIdAndRemovedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException(USER_ID_NOT_FOUND_ERROR));
        userMapper.updateByUserPutRequest(userPutRequest, user);
        userRepository.save(user);
    }

    @Transactional
    public void remove(Long id) {
        var user = userRepository.findByIdAndRemovedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException(USER_ID_NOT_FOUND_ERROR));
        user.setRemovedAt(Instant.now());
        userRepository.save(user);
    }
}