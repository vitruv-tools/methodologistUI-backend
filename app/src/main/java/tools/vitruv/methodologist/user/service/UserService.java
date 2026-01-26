package tools.vitruv.methodologist.user.service;

import static java.nio.charset.StandardCharsets.UTF_8;
import static tools.vitruv.methodologist.messages.Error.USER_DOSE_NOT_HAVE_ACCESS;
import static tools.vitruv.methodologist.messages.Error.USER_EMAIL_NOT_FOUND_ERROR;
import static tools.vitruv.methodologist.messages.Error.USER_ID_NOT_FOUND_ERROR;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.time.Duration;
import java.time.Instant;
import java.time.Year;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.FileCopyUtils;
import tools.vitruv.methodologist.apihandler.KeycloakApiHandler;
import tools.vitruv.methodologist.apihandler.dto.response.KeycloakWebToken;
import tools.vitruv.methodologist.exception.EmailExistsException;
import tools.vitruv.methodologist.exception.NotFoundException;
import tools.vitruv.methodologist.exception.StartupException;
import tools.vitruv.methodologist.exception.UnauthorizedException;
import tools.vitruv.methodologist.exception.ValidationCodeExpiredException;
import tools.vitruv.methodologist.exception.ValidationCodeNotExpiredYetException;
import tools.vitruv.methodologist.exception.VerificationCodeException;
import tools.vitruv.methodologist.general.service.MailService;
import tools.vitruv.methodologist.user.controller.dto.KeycloakUser;
import tools.vitruv.methodologist.user.controller.dto.request.PostAccessTokenByRefreshTokenRequest;
import tools.vitruv.methodologist.user.controller.dto.request.PostAccessTokenRequest;
import tools.vitruv.methodologist.user.controller.dto.request.UserPostRequest;
import tools.vitruv.methodologist.user.controller.dto.request.UserPutRequest;
import tools.vitruv.methodologist.user.controller.dto.request.UserPutVerifyRequest;
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
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserService {
  UserMapper userMapper;
  UserRepository userRepository;
  KeycloakService keycloakService;
  KeycloakApiHandler keycloakApiHandler;
  MailService mailService;
  final String sendOtpMailTemplate;
  final String sendOtpMailSubject;
  final int ttlMinutes;

  /**
   * Constructs a UserService with required dependencies and loads the OTP mail template.
   *
   * <p>Initializes injected collaborators and reads the OTP email template from the provided {@link
   * Resource}. The template content is stored in the instance field {@code sendOtpMailTemplate}.
   *
   * @param userMapper the mapper for converting between entity and DTOs
   * @param userRepository repository for user persistence
   * @param keycloakService service for creating/managing Keycloak users
   * @param keycloakApiHandler handler for Keycloak API calls
   * @param mailService service used to send emails
   * @param sendOtpMailTemplateResource classpath resource pointing to the OTP email template
   * @param ttlMinutes time-to-live for OTP codes in minutes
   * @param sendOtpMailSubject email subject used when sending OTP messages
   * @throws RuntimeException if reading the template resource fails
   */
  public UserService(
      UserMapper userMapper,
      UserRepository userRepository,
      KeycloakService keycloakService,
      KeycloakApiHandler keycloakApiHandler,
      MailService mailService,
      @Value("classpath:templates/mail/send_otp_message.txt") Resource sendOtpMailTemplateResource,
      @Value("${app.otp.ttlMinutes}") int ttlMinutes,
      @Value("${mail.newuser.otp.subject}") String sendOtpMailSubject) {
    this.userMapper = userMapper;
    this.userRepository = userRepository;
    this.keycloakService = keycloakService;
    this.keycloakApiHandler = keycloakApiHandler;
    this.mailService = mailService;
    this.ttlMinutes = ttlMinutes;
    this.sendOtpMailSubject = sendOtpMailSubject;
    Reader reader = null;
    try {
      reader = new InputStreamReader(sendOtpMailTemplateResource.getInputStream(), UTF_8);
      sendOtpMailTemplate = FileCopyUtils.copyToString(reader);
    } catch (IOException e) {
      throw new StartupException(e.getMessage());
    }
  }

  /**
   * Generates a 6-digit numeric one-time password (OTP), computes its BCrypt hash, and returns the
   * OTP together with its hash and expiry instant.
   *
   * <p>The OTP is zero-padded to 6 digits (range `000000`–`999999`) using thread-local randomness.
   * The raw OTP is hashed with BCrypt. The returned {@code OtpResult} contains:
   *
   * <ul>
   *   <li>{@code code} — the plain OTP string
   *   <li>{@code hash} — the BCrypt hash of the OTP
   *   <li>{@code expiresAt} — an {@link Instant} representing now plus the provided TTL
   * </ul>
   *
   * @param ttl time-to-live for the OTP; must be non-null and represent the desired lifetime
   * @return an {@code OtpResult} containing the plain OTP, its BCrypt hash, and expiry instant
   * @throws NullPointerException if {@code ttl} is null
   */
  public OtpResult generateOtp(Duration ttl) {
    String code = String.format("%06d", ThreadLocalRandom.current().nextInt(0, 1_000_000));

    String hash = BCrypt.hashpw(code, BCrypt.gensalt());
    Instant expiresAt = Instant.now().plus(ttl);

    return new OtpResult(code, hash, expiresAt);
  }

  /**
   * Verifies the one-time password (OTP) for the user identified by {@code callerEmail}.
   *
   * <p>Looks up an active, not-yet-verified user by email. If found, checks that the stored OTP has
   * not expired and that the provided input code matches the stored BCrypt hash. On success the
   * user is marked as verified and persisted.
   *
   * @param callerEmail the email address of the caller to verify
   * @param userPutVerifyRequest request containing the input OTP to validate
   * @throws AccessDeniedException if no active unverified user exists for {@code callerEmail}
   * @throws ValidationCodeExpiredException if the stored OTP has already expired
   * @throws VerificationCodeException if the provided OTP does not match the stored hash
   */
  public void verifyOtp(String callerEmail, UserPutVerifyRequest userPutVerifyRequest) {
    User user =
        userRepository
            .findByEmailIgnoreCaseAndRemovedAtIsNullAndVerifiedIsFalse(callerEmail)
            .orElseThrow(() -> new AccessDeniedException(USER_DOSE_NOT_HAVE_ACCESS));

    if (Instant.now().isAfter(user.getOtpExpiresAt())) {
      throw new ValidationCodeExpiredException();
    }

    if (!BCrypt.checkpw(userPutVerifyRequest.getInputCode(), user.getOtpSecret())) {
      throw new VerificationCodeException();
    }
    user.setVerified(true);
    userRepository.save(user);
  }

  /**
   * Resends a one-time password (OTP) to the user identified by {@code callerEmail}.
   *
   * <p>Looks up an active, not-yet-verified user by email. If a previously issued OTP has not yet
   * expired, a {@link ValidationCodeNotExpiredYetException} is thrown. Otherwise a new OTP is
   * generated, sent to the user's email, its BCrypt hash and new expiry are stored, and the user
   * entity is persisted.
   *
   * @param callerEmail the email address of the caller to resend the OTP for (case-insensitive)
   * @throws AccessDeniedException if no active unverified user exists for {@code callerEmail}
   * @throws ValidationCodeNotExpiredYetException if the existing OTP has not yet expired
   */
  public void resendOtp(String callerEmail) {
    User user =
        userRepository
            .findByEmailIgnoreCaseAndRemovedAtIsNullAndVerifiedIsFalse(callerEmail)
            .orElseThrow(() -> new AccessDeniedException(USER_DOSE_NOT_HAVE_ACCESS));

    if (Instant.now().isBefore(user.getOtpExpiresAt())) {
      throw new ValidationCodeNotExpiredYetException();
    }
    OtpResult otp = generateOtp(Duration.ofMinutes(ttlMinutes));
    sendOtp(user.getEmail(), otp.code(), ttlMinutes);
    user.setOtpSecret(otp.hash());
    user.setOtpExpiresAt(otp.expiresAt());
    userRepository.save(user);
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
    OtpResult otp = generateOtp(Duration.ofMinutes(ttlMinutes));
    sendOtp(user.getEmail(), otp.code(), ttlMinutes);
    user.setOtpSecret(otp.hash());
    user.setOtpExpiresAt(otp.expiresAt());
    userRepository.save(user);
    return user;
  }

  /**
   * Sends an OTP email to the given recipient by applying the configured template.
   *
   * <p>The method replaces the template placeholders:
   *
   * <ul>
   *   <li>{@code {{otp}}} - the one-time password to display
   *   <li>{@code {{ttlMinutes}}} - the OTP time-to-live in minutes
   *   <li>{@code {{year}}} - the current year
   *   <li>{@code {{appName}}} - the application name shown in the email
   * </ul>
   *
   * @param to recipient email address
   * @param otp one-time password to embed into the email template
   * @param ttlMinutes time-to-live for the OTP (in minutes) shown in the email
   */
  public void sendOtp(String to, String otp, int ttlMinutes) {
    String html =
        sendOtpMailTemplate
            .replace("{{otp}}", otp)
            .replace("{{ttlMinutes}}", String.valueOf(ttlMinutes))
            .replace("{{year}}", String.valueOf(Year.now().getValue()))
            .replace("{{appName}}", "mwa.sdq.kastel.kit.edu");

    mailService.send(to, sendOtpMailSubject, html);
  }

  /**
   * Checks if a user with the given email already exists in either the application database or
   * Keycloak authentication system.
   *
   * @param email the email address to check for existence
   * @throws EmailExistsException if the email already exists in either system
   */
  public void checkEmailExistsOrThrow(String email) {
    boolean existsInDb = userRepository.findByEmailIgnoreCase(email).isPresent();
    boolean existsInKeycloak = Boolean.TRUE.equals(keycloakService.existUser(email));

    if (existsInDb || existsInKeycloak) {
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
   * Retrieves user information for an active user by their email address. Only returns users that
   * have not been marked as removed.
   *
   * @param email the email address of the user to retrieve (case-insensitive)
   * @return UserResponse containing the user's information
   * @throws NotFoundException if no active user is found with the given email
   */
  @Transactional
  public UserResponse findByEmail(String email) {
    User user =
        userRepository
            .findByEmailIgnoreCaseAndRemovedAtIsNull(email)
            .orElseThrow(() -> new NotFoundException(USER_EMAIL_NOT_FOUND_ERROR));
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

  /**
   * Searches for users by name or email, excluding the caller. Only active users are returned.
   *
   * <p>If {@code queryParam} is blank or null, all users except the caller are listed. Otherwise,
   * users matching the query by name or email are returned, excluding the caller. Results are
   * paginated.
   *
   * @param callerEmail the email address of the authenticated caller (excluded from results)
   * @param queryParam the search query for name or email; if blank, returns all except caller
   * @param pageable pagination and sorting information
   * @return a list of user responses matching the search criteria
   * @throws NotFoundException if the caller is not found or is marked as removed
   */
  @Transactional(readOnly = true)
  public List<UserResponse> searchUserByNameAndEmail(
      String callerEmail, String queryParam, Pageable pageable) {
    userRepository
        .findByEmailIgnoreCaseAndRemovedAtIsNull(callerEmail)
        .orElseThrow(() -> new NotFoundException(USER_EMAIL_NOT_FOUND_ERROR));
    List<User> users =
        (queryParam == null || queryParam.isBlank())
            ? userRepository.findAllExcludingEmailOrderByName(callerEmail, pageable)
            : userRepository.searchByNameOrEmailExcludingCaller(
                callerEmail, queryParam.trim(), pageable);

    return users.stream().map(userMapper::toUserResponse).toList();
  }

  /**
   * Immutable holder for a generated one-time password (OTP), its stored hash, and expiry.
   *
   * <p>The record contains the plain OTP string (for sending to the user), a BCrypt hash suitable
   * for secure storage and verification, and the instant when the OTP becomes invalid.
   *
   * @param code the plain 6-digit OTP string (zero-padded)
   * @param hash the BCrypt hash of the OTP for secure storage
   * @param expiresAt the {@link java.time.Instant} when the OTP expires
   */
  public record OtpResult(String code, String hash, Instant expiresAt) {}
}
