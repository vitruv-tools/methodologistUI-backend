package tools.vitruv.methodologist.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tools.vitruv.methodologist.messages.Error.USER_DOSE_NOT_HAVE_ACCESS;
import static tools.vitruv.methodologist.messages.Error.USER_EMAIL_NOT_FOUND_ERROR;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.bcrypt.BCrypt;
import tools.vitruv.methodologist.apihandler.KeycloakApiHandler;
import tools.vitruv.methodologist.apihandler.PostmarkApiHandler;
import tools.vitruv.methodologist.apihandler.dto.response.KeycloakWebToken;
import tools.vitruv.methodologist.exception.EmailExistsException;
import tools.vitruv.methodologist.exception.NotFoundException;
import tools.vitruv.methodologist.exception.UnauthorizedException;
import tools.vitruv.methodologist.exception.ValidationCodeExpiredException;
import tools.vitruv.methodologist.exception.ValidationCodeNotExpiredYetException;
import tools.vitruv.methodologist.exception.VerificationCodeException;
import tools.vitruv.methodologist.user.controller.dto.KeycloakUser;
import tools.vitruv.methodologist.user.controller.dto.request.PostAccessTokenByRefreshTokenRequest;
import tools.vitruv.methodologist.user.controller.dto.request.PostAccessTokenRequest;
import tools.vitruv.methodologist.user.controller.dto.request.UserPostForgotPasswordRequest;
import tools.vitruv.methodologist.user.controller.dto.request.UserPostRequest;
import tools.vitruv.methodologist.user.controller.dto.request.UserPutChangePasswordRequest;
import tools.vitruv.methodologist.user.controller.dto.request.UserPutRequest;
import tools.vitruv.methodologist.user.controller.dto.request.UserPutVerifyRequest;
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
  @Mock private PostmarkApiHandler postmarkApiHandler;

  private UserService userService;

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

    int ttlMinutes = 5;
    String subject = "dummy-subject";

    userService =
        new UserService(
            userMapper,
            userRepository,
            keycloakService,
            keycloakApiHandler,
            postmarkApiHandler,
            ttlMinutes);
  }

  @Test
  void getAccessToken_returnsUserWebToken_onSuccess() {
    KeycloakWebToken keycloakToken =
        new tools.vitruv.methodologist.apihandler.dto.response.KeycloakWebToken();
    when(keycloakApiHandler.getAccessTokenOrThrow("alice", "p@ssw0rd")).thenReturn(keycloakToken);
    when(userMapper.toUserWebToken(keycloakToken)).thenReturn(userWebToken);

    UserWebToken result = userService.getAccessToken(accessReq);

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
    KeycloakWebToken keycloakToken =
        new tools.vitruv.methodologist.apihandler.dto.response.KeycloakWebToken();
    when(keycloakApiHandler.getAccessTokenByRefreshToken("refresh-123")).thenReturn(keycloakToken);
    when(userMapper.toUserWebToken(keycloakToken)).thenReturn(userWebToken);

    UserWebToken result = userService.getAccessTokenByRefreshToken(refreshReq);

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

    User entity = new User();
    entity.setFirstName("Alice");
    entity.setLastName("Doe");
    entity.setEmail("alice@example.com");
    entity.setUsername("alice");
    entity.setRoleType(tools.vitruv.methodologist.user.RoleType.USER);

    when(userMapper.toUser(any(UserPostRequest.class))).thenReturn(entity);

    UserPostRequest req =
        UserPostRequest.builder()
            .firstName("Alice")
            .lastName("Doe")
            .email("alice@example.com")
            .username("alice")
            .password("p@ssw0rd")
            .roleType(tools.vitruv.methodologist.user.RoleType.USER)
            .build();

    User result = userService.create(req);

    assertThat(result).isEqualTo(entity);
    verify(keycloakService).createUser(any(KeycloakUser.class));

    ArgumentCaptor<User> savedCaptor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(savedCaptor.capture());
    assertThat(savedCaptor.getValue().getEmail()).isEqualTo("alice@example.com");
    assertThat(savedCaptor.getValue().getOtpSecret()).isNotNull();
  }

  @Test
  void create_throwsEmailExists_whenEmailAlreadyUsed() {
    UserPostRequest req =
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
  void create_throwsEmailExists_whenEmailAlreadyExistInKeycloak() {
    UserPostRequest req =
        UserPostRequest.builder()
            .email("alice@example.com")
            .username("alice")
            .password("x")
            .roleType(tools.vitruv.methodologist.user.RoleType.USER)
            .build();

    when(userRepository.findByEmailIgnoreCase("alice@example.com")).thenReturn(Optional.empty());

    when(keycloakService.existUser("alice@example.com")).thenReturn(true);

    assertThatThrownBy(() -> userService.create(req)).isInstanceOf(EmailExistsException.class);

    verify(userMapper, never()).toUser(any());
    verify(keycloakService, never()).createUser(any());
    verify(userRepository, never()).save(any());
  }

  @Test
  void update_appliesChanges_andSaves_whenUserExists() {
    long id = 42L;
    User existing = new User();
    existing.setId(id);
    existing.setEmail("alice@example.com");

    UserPutRequest put = UserPutRequest.builder().firstName("Alicia").lastName("Doe").build();

    when(userRepository.findByIdAndRemovedAtIsNull(id)).thenReturn(Optional.of(existing));

    User result = userService.update(id, put);

    assertThat(result).isSameAs(existing);
    verify(userMapper).updateByUserPutRequest(eq(put), eq(existing));
    verify(userRepository).save(existing);
  }

  @Test
  void update_throwsNotFound_whenUserMissing() {
    long id = 7L;
    when(userRepository.findByIdAndRemovedAtIsNull(id)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> userService.update(id, UserPutRequest.builder().build()))
        .isInstanceOf(NotFoundException.class);
    verify(userRepository, never()).save(any());
  }

  @Test
  void findByEmail_returnsMappedResponse_whenUserExists() {
    long id = 1L;
    String callerEmail = "dummy@dummy.com";
    User user = new User();
    user.setId(id);
    UserResponse resp = UserResponse.builder().id(id).build();

    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull(callerEmail))
        .thenReturn(Optional.of(user));
    when(userMapper.toUserResponse(user)).thenReturn(resp);

    UserResponse result = userService.findByEmail(callerEmail);

    assertThat(result).isEqualTo(resp);
  }

  @Test
  void findByEmail_throwsNotFound_whenUserMissing() {
    String callerEmail = "dummy@dummy.com";
    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull(callerEmail))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> userService.findByEmail(callerEmail))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void remove_setsRemovedAt_andSaves_whenUserExists() {
    long id = 11L;
    User user = new User();
    user.setId(id);

    when(userRepository.findByIdAndRemovedAtIsNull(id)).thenReturn(Optional.of(user));

    User result = userService.remove(id);

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

  @Test
  void searchUserByNameAndEmail_returnsAllExcludingCaller_whenQueryBlank() {
    String callerEmail = "caller@example.com";
    User caller = new User();
    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull(callerEmail))
        .thenReturn(Optional.of(caller));

    User u1 = new User();
    u1.setId(1L);
    User u2 = new User();
    u2.setId(2L);
    Pageable pageable = PageRequest.of(0, 10);
    when(userRepository.findAllExcludingEmailOrderByName(callerEmail, pageable))
        .thenReturn(List.of(u1, u2));

    UserResponse r1 = UserResponse.builder().id(1L).build();
    UserResponse r2 = UserResponse.builder().id(2L).build();
    when(userMapper.toUserResponse(u1)).thenReturn(r1);
    when(userMapper.toUserResponse(u2)).thenReturn(r2);

    List<UserResponse> result = userService.searchUserByNameAndEmail(callerEmail, "  ", pageable);

    assertThat(result).containsExactly(r1, r2);
    verify(userRepository).findByEmailIgnoreCaseAndRemovedAtIsNull(callerEmail);
    verify(userRepository).findAllExcludingEmailOrderByName(callerEmail, pageable);
    verify(userRepository, never()).searchByNameOrEmailExcludingCaller(any(), any(), any());
  }

  @Test
  void searchUserByNameAndEmail_searchesByNameOrEmail_whenQueryProvided() {
    String callerEmail = "caller@example.com";
    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull(callerEmail))
        .thenReturn(Optional.of(new User()));

    User u1 = new User();
    u1.setId(10L);
    String query = "ali";
    Pageable pageable = PageRequest.of(1, 5);
    when(userRepository.searchByNameOrEmailExcludingCaller(callerEmail, query, pageable))
        .thenReturn(List.of(u1));

    UserResponse r1 = UserResponse.builder().id(10L).build();
    when(userMapper.toUserResponse(u1)).thenReturn(r1);

    List<UserResponse> result = userService.searchUserByNameAndEmail(callerEmail, query, pageable);

    assertThat(result).containsExactly(r1);
    verify(userRepository).findByEmailIgnoreCaseAndRemovedAtIsNull(callerEmail);
    verify(userRepository).searchByNameOrEmailExcludingCaller(callerEmail, query, pageable);
    verify(userRepository, never()).findAllExcludingEmailOrderByName(any(), any());
  }

  @Test
  void searchUserByNameAndEmail_trimsQuery_beforeSearching() {
    String callerEmail = "caller@example.com";
    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull(callerEmail))
        .thenReturn(Optional.of(new User()));

    User u = new User();
    u.setId(5L);
    String trimmed = "Alice@example.com";
    Pageable pageable = PageRequest.of(0, 20);
    when(userRepository.searchByNameOrEmailExcludingCaller(callerEmail, trimmed, pageable))
        .thenReturn(List.of(u));

    UserResponse r = UserResponse.builder().id(5L).build();
    when(userMapper.toUserResponse(u)).thenReturn(r);

    String rawQuery = "  Alice@example.com  ";
    List<UserResponse> result =
        userService.searchUserByNameAndEmail(callerEmail, rawQuery, pageable);

    assertThat(result).containsExactly(r);
    verify(userRepository).searchByNameOrEmailExcludingCaller(callerEmail, trimmed, pageable);
  }

  @Test
  void searchUserByNameAndEmail_throwsNotFound_whenCallerMissing() {
    String callerEmail = "missing@example.com";
    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull(callerEmail))
        .thenReturn(Optional.empty());

    Pageable pageable = PageRequest.of(0, 10);
    assertThatThrownBy(() -> userService.searchUserByNameAndEmail(callerEmail, "any", pageable))
        .isInstanceOf(NotFoundException.class);

    verify(userRepository, never()).findAllExcludingEmailOrderByName(any(), any());
    verify(userRepository, never()).searchByNameOrEmailExcludingCaller(any(), any(), any());
  }

  @Test
  void generateOtp_returnsValidResult() {
    Duration ttl = Duration.ofMinutes(5);

    UserService.OtpResult otpResult = userService.generateOtp(ttl);

    assertNotNull(otpResult);
    assertNotNull(otpResult.code());
    assertNotNull(otpResult.hash());
    assertNotNull(otpResult.expiresAt());
  }

  @Test
  void generateOtp_codeIsSixDigits_numeric_andAllowsLeadingZeros() {
    UserService.OtpResult otpResult = userService.generateOtp(Duration.ofMinutes(5));

    assertTrue(otpResult.code().matches("\\d{6}"));
  }

  @Test
  void generateOtp_hashMatchesCode_bcryptCheckPasses() {
    UserService.OtpResult otpResult = userService.generateOtp(Duration.ofMinutes(5));

    assertTrue(BCrypt.checkpw(otpResult.code(), otpResult.hash()));
  }

  @Test
  void generateOtp_expiresAt_isNowPlusTtlWithinTolerance() {
    Duration ttl = Duration.ofSeconds(30);

    Instant before = Instant.now();
    UserService.OtpResult otpResult = userService.generateOtp(ttl);
    Instant after = Instant.now();

    Instant minExpected = before.plus(ttl);
    Instant maxExpected = after.plus(ttl);

    assertFalse(otpResult.expiresAt().isBefore(minExpected));
    assertFalse(otpResult.expiresAt().isAfter(maxExpected));
  }

  @Test
  void generateOtp_ttlZero_expiresAtIsNowWithinTolerance() {
    Duration ttl = Duration.ZERO;

    Instant before = Instant.now();
    UserService.OtpResult otpResult = userService.generateOtp(ttl);
    Instant after = Instant.now();

    assertFalse(otpResult.expiresAt().isBefore(before));
    assertFalse(otpResult.expiresAt().isAfter(after));
  }

  @Test
  void generateOtp_negativeTtl_expiresAtInPast() {
    Duration ttl = Duration.ofSeconds(-10);

    Instant before = Instant.now();
    UserService.OtpResult otpResult = userService.generateOtp(ttl);

    assertTrue(otpResult.expiresAt().isBefore(before));
  }

  @Test
  void generateOtp_nullTtl_throwsException() {
    assertThrows(NullPointerException.class, () -> userService.generateOtp(null));
  }

  @Test
  void generateOtp_twoCalls_shouldDiffer_mostOfTheTime() {
    UserService.OtpResult otpResultA = userService.generateOtp(Duration.ofMinutes(5));
    UserService.OtpResult otpResultB = userService.generateOtp(Duration.ofMinutes(5));

    assertNotEquals(otpResultA.code(), otpResultB.code());
    assertNotEquals(otpResultA.hash(), otpResultB.hash());
    assertNotEquals(otpResultA.expiresAt(), otpResultB.expiresAt());
  }

  @Test
  void verifyOtp_setsVerifiedTrue_andSaves_whenCodeValidAndNotExpired() {
    User user = new User();
    user.setVerified(false);
    user.setOtpExpiresAt(Instant.now().plusSeconds(60));
    user.setOtpSecret(BCrypt.hashpw("123456", BCrypt.gensalt()));

    UserPutVerifyRequest req = new UserPutVerifyRequest();
    req.setInputCode("123456");

    String callerEmail = "caller@example.com";
    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNullAndVerifiedIsFalse(callerEmail))
        .thenReturn(Optional.of(user));

    userService.verifyOtp(callerEmail, req);

    assertThat(user.getVerified()).isTrue();
    verify(userRepository).save(user);
  }

  @Test
  void verifyOtp_throwsAccessDenied_whenUserNotFoundOrAlreadyVerified() {
    UserPutVerifyRequest req = new UserPutVerifyRequest();
    req.setInputCode("123456");

    String callerEmail = "caller@example.com";
    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNullAndVerifiedIsFalse(callerEmail))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> userService.verifyOtp(callerEmail, req))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessageContaining(USER_DOSE_NOT_HAVE_ACCESS);

    verify(userRepository, never()).save(any());
  }

  @Test
  void verifyOtp_throwsValidationCodeExpired_whenOtpExpired() {

    User user = new User();
    user.setVerified(false);
    user.setOtpExpiresAt(Instant.now().minusSeconds(1));
    user.setOtpSecret(BCrypt.hashpw("123456", BCrypt.gensalt()));

    UserPutVerifyRequest req = new UserPutVerifyRequest();
    req.setInputCode("123456");

    String callerEmail = "caller@example.com";
    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNullAndVerifiedIsFalse(callerEmail))
        .thenReturn(Optional.of(user));

    assertThatThrownBy(() -> userService.verifyOtp(callerEmail, req))
        .isInstanceOf(ValidationCodeExpiredException.class);

    verify(userRepository, never()).save(any());
  }

  @Test
  void verifyOtp_throwsVerificationCodeException_whenCodeInvalid() {

    User user = new User();
    user.setVerified(false);
    user.setOtpExpiresAt(Instant.now().plusSeconds(60));
    user.setOtpSecret(BCrypt.hashpw("123456", BCrypt.gensalt()));

    UserPutVerifyRequest req = new UserPutVerifyRequest();
    req.setInputCode("000000");

    String callerEmail = "caller@example.com";
    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNullAndVerifiedIsFalse(callerEmail))
        .thenReturn(Optional.of(user));

    assertThatThrownBy(() -> userService.verifyOtp(callerEmail, req))
        .isInstanceOf(VerificationCodeException.class);

    verify(userRepository, never()).save(any());
  }

  @Test
  void resendOtp_generatesNewOtp_andSaves_whenExpired() {

    User user = new User();
    user.setVerified(false);
    user.setOtpExpiresAt(Instant.now().minusSeconds(10));
    user.setEmail("caller@example.com");
    user.setLastName("dummy");

    String callerEmail = "caller@example.com";
    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNullAndVerifiedIsFalse(callerEmail))
        .thenReturn(Optional.of(user));

    UserService spyService = spy(userService);
    UserService.OtpResult otp =
        new UserService.OtpResult(
            "123456", BCrypt.hashpw("123456", BCrypt.gensalt()), Instant.now().plusSeconds(300));

    doReturn(otp).when(spyService).generateOtp(any());
    doNothing().when(spyService).sendOtp(anyString(), anyString(), anyString(), anyInt());

    spyService.resendOtp(callerEmail);

    assertThat(user.getOtpSecret()).isEqualTo(otp.hash());
    assertThat(user.getOtpExpiresAt()).isEqualTo(otp.expiresAt());
    verify(userRepository).save(user);
  }

  @Test
  void resendOtp_throwsAccessDenied_whenUserNotFoundOrAlreadyVerified() {
    String callerEmail = "caller@example.com";

    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNullAndVerifiedIsFalse(callerEmail))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> userService.resendOtp(callerEmail))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessageContaining(USER_DOSE_NOT_HAVE_ACCESS);

    verify(userRepository, never()).save(any());
  }

  @Test
  void resendOtp_throwsValidationCodeNotExpiredYet_whenOtpStillValid() {

    User user = new User();
    user.setVerified(false);
    user.setOtpExpiresAt(Instant.now().plusSeconds(60));

    String callerEmail = "caller@example.com";
    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNullAndVerifiedIsFalse(callerEmail))
        .thenReturn(Optional.of(user));

    assertThatThrownBy(() -> userService.resendOtp(callerEmail))
        .isInstanceOf(ValidationCodeNotExpiredYetException.class);

    verify(userRepository, never()).save(any());
  }

  @Test
  void forgotPassword_setsNewPassword_andSendsEmail_whenUserExists() {
    String email = "caller@example.com";

    User user = new User();
    user.setEmail(email);
    user.setUsername("kc-user-1");
    user.setLastName("dummy");

    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull(email))
        .thenReturn(Optional.of(user));

    UserService spyService = spy(userService);

    UserPostForgotPasswordRequest userPostForgotPasswordRequest =
        UserPostForgotPasswordRequest.builder().email(email).build();
    spyService.forgotPassword(userPostForgotPasswordRequest);

    verify(keycloakService).setPassword(eq(user.getUsername()), anyString());
  }

  @Test
  void forgotPassword_throwsNotFound_whenUserMissing() {
    String email = "missing@example.com";

    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull(email))
        .thenReturn(Optional.empty());

    UserPostForgotPasswordRequest userPostForgotPasswordRequest =
        UserPostForgotPasswordRequest.builder().email(email).build();
    assertThatThrownBy(() -> userService.forgotPassword(userPostForgotPasswordRequest))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining(USER_EMAIL_NOT_FOUND_ERROR);

    verify(keycloakService, never()).setPassword(anyString(), anyString());
    verify(postmarkApiHandler, never()).postPasswordMail(any(), any());
  }

  @Test
  void forgotPassword_propagates_whenMailjetFails_afterKeycloakPasswordSet() {
    String email = "caller@example.com";

    User user = new User();
    user.setEmail(email);
    user.setUsername("kc-user-1");
    user.setLastName("dummy");

    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull(email))
        .thenReturn(Optional.of(user));

    UserService spyService = spy(userService);

    doThrow(new RuntimeException("Mailjet failed"))
        .when(postmarkApiHandler)
        .postPasswordMail(any(), any());

    UserPostForgotPasswordRequest userPostForgotPasswordRequest =
        UserPostForgotPasswordRequest.builder().email(email).build();
    assertThatThrownBy(() -> spyService.forgotPassword(userPostForgotPasswordRequest))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Mailjet failed");

    verify(keycloakService).setPassword(eq(user.getUsername()), anyString());
  }

  @Test
  void changePassword_setsPassword_whenUserExists() {
    String email = "caller@example.com";

    User user = new User();
    user.setEmail(email);
    user.setUsername("kc-user-1");

    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull(email))
        .thenReturn(Optional.of(user));

    UserPutChangePasswordRequest req = new UserPutChangePasswordRequest();
    req.setPassword("StrongPass#12345");

    userService.changePassword(email, req);

    verify(keycloakService).setPassword(user.getUsername(), req.getPassword());
  }

  @Test
  void changePassword_throwsNotFound_whenUserMissing() {
    String email = "missing@example.com";

    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull(email))
        .thenReturn(Optional.empty());

    UserPutChangePasswordRequest req = new UserPutChangePasswordRequest();
    req.setPassword("StrongPass#12345");

    assertThatThrownBy(() -> userService.changePassword(email, req))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining(USER_EMAIL_NOT_FOUND_ERROR);

    verify(keycloakService, never()).setPassword(anyString(), anyString());
  }

  @Test
  void changePassword_propagates_whenKeycloakFails() {
    String email = "caller@example.com";

    User user = new User();
    user.setEmail(email);
    user.setUsername("kc-user-1");

    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull(email))
        .thenReturn(Optional.of(user));

    UserPutChangePasswordRequest req = new UserPutChangePasswordRequest();
    req.setPassword("StrongPass#12345");

    doThrow(new RuntimeException("Keycloak down"))
        .when(keycloakService)
        .setPassword(user.getUsername(), req.getPassword());

    assertThatThrownBy(() -> userService.changePassword(email, req))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Keycloak down");
  }
}
