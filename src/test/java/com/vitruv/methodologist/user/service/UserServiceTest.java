package com.vitruv.methodologist.user.service;

import com.vitruv.methodologist.exception.UserConflictException;
import com.vitruv.methodologist.user.RoleType;
import com.vitruv.methodologist.user.controller.dto.request.UserPostRequest;
import com.vitruv.methodologist.user.controller.dto.request.UserPutRequest;
import com.vitruv.methodologist.user.mapper.UserMapperImpl;
import com.vitruv.methodologist.user.model.User;
import com.vitruv.methodologist.user.model.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.*;

class UserServiceTest {
    private UserService userService;
    private UserRepository userRepository;

    @BeforeEach
    void initialize(){
        userRepository = mock(UserRepository.class);
        userService = new UserService(new UserMapperImpl(), userRepository);
    }

    @Test
    void create() {
        var inputData = UserPostRequest.builder()
                .email("dummy")
                .username("dummy")
                .firstName("dummy")
                .lastName("dummy")
                .build();
        var user = userService.create(inputData);

        assertThat(user.getEmail())
                .isEqualTo(inputData.getEmail());
        assertThat(user.getRoleType())
                .isEqualTo(RoleType.USER);
        assertThat(user.getUsername())
                .isEqualTo(inputData.getUsername());
        assertThat(user.getFirstName())
                .isEqualTo(inputData.getFirstName());
        assertThat(user.getLastName())
                .isEqualTo(inputData.getLastName());
        assertThat(user.getRemovedAt()).isNull();

        verify(userRepository).findByEmailIgnoreCase(inputData.getEmail());
        verify(userRepository).save(user);
    }

    @Test
    void create_existUser() {
        when(userRepository.findByEmailIgnoreCase("dummy"))
                .thenReturn(Optional.of(User.builder().email("dummy").build()));
        assertThatThrownBy(() -> userService.create(UserPostRequest.builder().email("dummy").build()))
                .isInstanceOf(UserConflictException.class);
    }

    @Test
    void update() {
        var existUser = User.builder()
                .id(1L)
                .email("dummy")
                .username("dummy")
                .firstName("dummy")
                .lastName("dummy")
                .roleType(RoleType.USER)
                .removedAt(null)
                .createdAt(Instant.now())
                .build();

        var inputData = UserPutRequest.builder()
                .firstName("new dummy")
                .lastName("new dummy")
                .build();

        when(userRepository.findByIdAndRemovedAtIsNull(existUser.getId())).thenReturn(Optional.of(existUser));
        var user = userService.update(existUser.getId(), inputData);

        assertThat(user.getEmail())
                .isEqualTo(existUser.getEmail());
        assertThat(user.getRoleType())
                .isEqualTo(existUser.getRoleType());
        assertThat(user.getUsername())
                .isEqualTo(existUser.getUsername());
        assertThat(user.getFirstName())
                .isEqualTo(inputData.getFirstName());
        assertThat(user.getLastName())
                .isEqualTo(inputData.getLastName());
        assertThat(user.getRemovedAt()).isNull();
        assertThat(user.getCreatedAt())
                .isEqualTo(existUser.getCreatedAt());


        verify(userRepository).findByIdAndRemovedAtIsNull(1L);
        verify(userRepository).save(existUser);
    }

    @Test
    void findById() {
        var existUser = User.builder()
                .id(1L)
                .email("dummy")
                .username("dummy")
                .firstName("dummy")
                .lastName("dummy")
                .roleType(RoleType.USER)
                .removedAt(null)
                .createdAt(Instant.now())
                .build();
        when(userRepository.findByIdAndRemovedAtIsNull(existUser.getId())).thenReturn(Optional.of(existUser));
        var userResponse = userService.findById(1L);
        assertThat(userResponse)
                .usingRecursiveComparison()
                .isEqualTo(existUser);
        verify(userRepository).findByIdAndRemovedAtIsNull(1L);
    }

    @Test
    void remove() {
        var existUser = User.builder()
                .id(1L)
                .email("dummy")
                .username("dummy")
                .firstName("dummy")
                .lastName("dummy")
                .roleType(RoleType.USER)
                .removedAt(null)
                .createdAt(Instant.now())
                .build();
        when(userRepository.findByIdAndRemovedAtIsNull(existUser.getId())).thenReturn(Optional.of(existUser));
        var user = userService.remove(1L);
        verify(userRepository).findByIdAndRemovedAtIsNull(1L);
        assertThat(user.getRemovedAt()).isNotNull();
    }
}