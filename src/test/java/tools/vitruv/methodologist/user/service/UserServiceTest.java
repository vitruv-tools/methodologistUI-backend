package tools.vitruv.methodologist.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.vitruv.methodologist.apihandler.KeycloakApiHandler;
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

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  @Mock private UserMapper userMapper;
  @Mock private UserRepository userRepository;
  @Mock private KeycloakService keycloakService;
  @Mock private KeycloakApiHandler keycloakApiHandler;

  @InjectMocks private UserService userService;

  private PostAccessTokenRequest accessReq;
  private PostAccessTokenByRefreshTokenRequest refreshReq;
  private UserWebToken userWebToken;

  @BeforeEach
  void setUp() {
    accessReq = new PostAccessTokenRequest("alice", "p@ssw0rd");
    refreshReq = new PostAccessTokenByRefreshTokenRequest("refresh-123");
    userWebToken =
        new UserWebToken(
            "access-abc",
            "refresh-xyz",
            3600,
            86400,
            "Bearer",
            0,
            "session-1",
            "openid profile email");
  }

  @Test
  void getAccessToken_returnsUserWebToken_onSuccess() {
    var keycloakToken = new tools.vitruv.methodologist.apihandler.dto.response.KeycloakWebToken();
    when(keycloakApiHandler.getAccessTokenOrThrow("alice", "p@ssw0rd")).thenReturn(keycloakToken);
    when(userMapper.toUserWebToken(keycloakToken)).thenReturn(userWebToken);

    var result = userService.getAccessToken(accessReq);

    assertThat(result).isEqualTo(userWebToken);
    verify(keycloakApiHandler).getAccessTokenOrThrow("alice", "p@ssw0rd");
    verify(userMapper).toUserWebToken(keycloakToken);
  }

  @Test
  void getAccessToken_throwsUnauthorized_onAnyError() {
    when(keycloakApiHandler.getAccessTokenOrThrow("alice", "p@ssw0rd"))
        .thenThrow(new RuntimeException("boom"));

    assertThatThrownBy(() -> userService.getAccessToken(accessReq))
        .isInstanceOf(UnauthorizedException.class);
  }

  @Test
  void getAccessTokenByRefreshToken_returnsUserWebToken_onSuccess() {
    var keycloakToken = new tools.vitruv.methodologist.apihandler.dto.response.KeycloakWebToken();
    when(keycloakApiHandler.getAccessTokenByRefreshToken("refresh-123")).thenReturn(keycloakToken);
    when(userMapper.toUserWebToken(keycloakToken)).thenReturn(userWebToken);

    var result = userService.getAccessTokenByRefreshToken(refreshReq);

    assertThat(result).isEqualTo(userWebToken);
    verify(keycloakApiHandler).getAccessTokenByRefreshToken("refresh-123");
    verify(userMapper).toUserWebToken(keycloakToken);
  }

  @Test
  void getAccessTokenByRefreshToken_throwsUnauthorized_onAnyError() {
    when(keycloakApiHandler.getAccessTokenByRefreshToken("refresh-123"))
        .thenThrow(new RuntimeException("token invalid"));

    assertThatThrownBy(() -> userService.getAccessTokenByRefreshToken(refreshReq))
        .isInstanceOf(UnauthorizedException.class);
  }

  @Test
  void create_persistsUser_andCreatesKeycloakUser_whenEmailFree() {
    when(userRepository.findByEmailIgnoreCase("alice@example.com")).thenReturn(Optional.empty());

    UserPostRequest req =
            UserPostRequest.builder()
                    .firstName("Alice")
                    .lastName("Doe")
                    .email("alice@example.com")
                    .username("alice")
                    .password("p@ssw0rd")
                    .roleType(tools.vitruv.methodologist.user.RoleType.USER)
                    .build();

    User entity = new User();
    entity.setFirstName("Alice");
    entity.setLastName("Doe");
    entity.setEmail("alice@example.com");
    entity.setUsername("alice");
    entity.setRoleType(tools.vitruv.methodologist.user.RoleType.USER);

    when(userMapper.toUser(req)).thenReturn(entity);

    User result = userService.create(req);

    assertThat(result).isEqualTo(entity);
    verify(keycloakService).createUser(any(KeycloakUser.class));

    ArgumentCaptor<User> savedCaptor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(savedCaptor.capture());
    assertThat(savedCaptor.getValue().getEmail()).isEqualTo("alice@example.com");
  }

  @Test
  void create_throwsEmailExists_whenEmailAlreadyUsed() {
    var req =
        UserPostRequest.builder()
            .email("alice@example.com")
            .username("alice")
            .password("x")
            .roleType(tools.vitruv.methodologist.user.RoleType.USER)
            .build();

    when(userRepository.findByEmailIgnoreCase("alice@example.com"))
        .thenReturn(Optional.of(new User()));

    assertThatThrownBy(() -> userService.create(req)).isInstanceOf(EmailExistsException.class);

    verify(userMapper, never()).toUser(any());
    verify(keycloakService, never()).createUser(any());
    verify(userRepository, never()).save(any());
  }

  @Test
  void update_appliesChanges_andSaves_whenUserExists() {
    var id = 42L;
    var existing = new User();
    existing.setId(id);
    existing.setEmail("alice@example.com");

    var put = UserPutRequest.builder().firstName("Alicia").lastName("Doe").build();

    when(userRepository.findByIdAndRemovedAtIsNull(id)).thenReturn(Optional.of(existing));

    var result = userService.update(id, put);

    assertThat(result).isSameAs(existing);
    verify(userMapper).updateByUserPutRequest(eq(put), eq(existing));
    verify(userRepository).save(existing);
  }

  @Test
  void update_throwsNotFound_whenUserMissing() {
    var id = 7L;
    when(userRepository.findByIdAndRemovedAtIsNull(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> userService.update(id, UserPutRequest.builder().build()))
        .isInstanceOf(NotFoundException.class);

    verify(userRepository, never()).save(any());
  }

  @Test
  void findById_returnsMappedResponse_whenUserExists() {
    var id = 9L;
    var user = new User();
    user.setId(id);
    var resp = UserResponse.builder().id(id).build();

    when(userRepository.findByIdAndRemovedAtIsNull(id)).thenReturn(Optional.of(user));
    when(userMapper.toUserResponse(user)).thenReturn(resp);

    var result = userService.findById(id);

    assertThat(result).isEqualTo(resp);
  }

  @Test
  void findById_throwsNotFound_whenUserMissing() {
    when(userRepository.findByIdAndRemovedAtIsNull(123L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> userService.findById(123L)).isInstanceOf(NotFoundException.class);
  }

  @Test
  void remove_setsRemovedAt_andSaves_whenUserExists() {
    var id = 11L;
    var user = new User();
    user.setId(id);

    when(userRepository.findByIdAndRemovedAtIsNull(id)).thenReturn(Optional.of(user));

    var result = userService.remove(id);

    assertThat(result).isSameAs(user);
    assertThat(user.getRemovedAt()).isNotNull();
    assertThat(user.getRemovedAt()).isBeforeOrEqualTo(Instant.now());
    verify(userRepository).save(user);
  }

  @Test
  void remove_throwsNotFound_whenUserMissing() {
    when(userRepository.findByIdAndRemovedAtIsNull(77L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> userService.remove(77L)).isInstanceOf(NotFoundException.class);
  }
}
