package tools.vitruv.methodologist.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.vitruv.methodologist.exception.EmailExistsException;
import tools.vitruv.methodologist.user.RoleType;
import tools.vitruv.methodologist.user.controller.dto.request.UserPostRequest;
import tools.vitruv.methodologist.user.controller.dto.request.UserPutRequest;
import tools.vitruv.methodologist.user.mapper.UserMapperImpl;
import tools.vitruv.methodologist.user.model.User;
import tools.vitruv.methodologist.user.model.repository.UserRepository;

class UserServiceTest {
  private UserService userService;
  private UserRepository userRepositoryMock;
  private KeycloakService keycloakServiceMock;

  @BeforeEach
  void initialize() {
    userRepositoryMock = mock(UserRepository.class);
    keycloakServiceMock = mock(KeycloakService.class);
    userService =
        new UserService(new UserMapperImpl(), userRepositoryMock, keycloakServiceMock, null);
  }

  @Test
  void create() {
    var inputData =
        UserPostRequest.builder()
            .email("dummy")
            .username("dummy")
            .firstName("dummy")
            .lastName("dummy")
            .build();
    var user = userService.create(inputData);

    assertThat(user.getEmail()).isEqualTo(inputData.getEmail());
    assertThat(user.getRoleType()).isEqualTo(RoleType.USER);
    assertThat(user.getUsername()).isEqualTo(inputData.getUsername());
    assertThat(user.getFirstName()).isEqualTo(inputData.getFirstName());
    assertThat(user.getLastName()).isEqualTo(inputData.getLastName());
    assertThat(user.getRemovedAt()).isNull();

    verify(userRepositoryMock).findByEmailIgnoreCase(inputData.getEmail());
    verify(userRepositoryMock).save(user);
  }

  @Test
  void create_existUser() {
    when(userRepositoryMock.findByEmailIgnoreCase("dummy"))
        .thenReturn(Optional.of(User.builder().email("dummy").build()));
    assertThatThrownBy(() -> userService.create(UserPostRequest.builder().email("dummy").build()))
        .isInstanceOf(EmailExistsException.class);
  }

  @Test
  void update() {
    var existUser =
        User.builder()
            .id(1L)
            .email("dummy")
            .username("dummy")
            .firstName("dummy")
            .lastName("dummy")
            .roleType(RoleType.USER)
            .removedAt(null)
            .createdAt(Instant.now())
            .build();

    var inputData = UserPutRequest.builder().firstName("new dummy").lastName("new dummy").build();

    when(userRepositoryMock.findByIdAndRemovedAtIsNull(existUser.getId()))
        .thenReturn(Optional.of(existUser));
    var user = userService.update(existUser.getId(), inputData);

    assertThat(user.getEmail()).isEqualTo(existUser.getEmail());
    assertThat(user.getRoleType()).isEqualTo(existUser.getRoleType());
    assertThat(user.getUsername()).isEqualTo(existUser.getUsername());
    assertThat(user.getFirstName()).isEqualTo(inputData.getFirstName());
    assertThat(user.getLastName()).isEqualTo(inputData.getLastName());
    assertThat(user.getRemovedAt()).isNull();
    assertThat(user.getCreatedAt()).isEqualTo(existUser.getCreatedAt());

    verify(userRepositoryMock).findByIdAndRemovedAtIsNull(1L);
    verify(userRepositoryMock).save(existUser);
  }

  @Test
  void findById() {
    var existUser =
        User.builder()
            .id(1L)
            .email("dummy")
            .username("dummy")
            .firstName("dummy")
            .lastName("dummy")
            .roleType(RoleType.USER)
            .removedAt(null)
            .createdAt(Instant.now())
            .build();
    when(userRepositoryMock.findByIdAndRemovedAtIsNull(existUser.getId()))
        .thenReturn(Optional.of(existUser));
    var userResponse = userService.findById(1L);
    assertThat(userResponse).usingRecursiveComparison().isEqualTo(existUser);
    verify(userRepositoryMock).findByIdAndRemovedAtIsNull(1L);
  }

  @Test
  void remove() {
    var existUser =
        User.builder()
            .id(1L)
            .email("dummy")
            .username("dummy")
            .firstName("dummy")
            .lastName("dummy")
            .roleType(RoleType.USER)
            .removedAt(null)
            .createdAt(Instant.now())
            .build();
    when(userRepositoryMock.findByIdAndRemovedAtIsNull(existUser.getId()))
        .thenReturn(Optional.of(existUser));
    var user = userService.remove(1L);
    verify(userRepositoryMock).findByIdAndRemovedAtIsNull(1L);
    assertThat(user.getRemovedAt()).isNotNull();
  }
}
