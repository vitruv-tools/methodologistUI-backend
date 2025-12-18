/*
 * Copyright 2018-2019 adorsys GmbH & Co KG
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package tools.vitruv.methodologist.exception;

import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.method.HandlerMethod;

/**
 * Handles exceptions thrown by controllers in the application and returns standardized error
 * responses. Provides specific handlers for common exceptions and logs error details for debugging.
 */
@Slf4j
@RestControllerAdvice(basePackages = "tools.vitruv.methodologist")
@RequiredArgsConstructor
public class GlobalExceptionHandlerController {
  private static final String METHOD_ARGUMENT_NOT_VALID_EXCEPTION =
      "MethodArgumentNotValidException handled in controller: {}, message: {}";
  private static final String STACKTRACE_LOG = "Stacktrace: {}";
  private static final String FORMAT_ERROR = "FORMAT_ERROR";
  private static final String INTERNAL_SERVER_ERROR = "INTERNAL_SERVER_ERROR";
  private static final String NOT_FOUND_ERROR = "NOT_FOUND";
  private static final String FORBIDDEN_ERROR = "FORBIDDEN";
  private static final String BAD_REQUEST_ERROR = "BAD_REQUEST_ERROR";
  private static final String TEMPORARY_UNAVAILABLE_ERROR = "TEMPORARY_UNAVAILABLE_ERROR";

  /**
   * Handles {@link CLIExecuteException} thrown when execution of the external Vitruv-CLI fails.
   *
   * <p>Responds with HTTP 400 (Bad Request) and returns a standardized {@link ErrorResponse}
   * containing the exception message and the request path.
   *
   * @param ex the {@code CLIExecuteException} that was thrown
   * @param handlerMethod the controller method where the exception originated
   * @param request the current {@link ServletWebRequest} providing request context
   * @return an {@link ErrorResponse} with the failure message and request path
   */
  @ExceptionHandler(value = CLIExecuteException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ResponseBody
  public ErrorResponse cliExecuteException(
      CLIExecuteException ex, HandlerMethod handlerMethod, ServletWebRequest request) {
    return ErrorResponse.builder()
        .message(Objects.requireNonNull(ex.getMessage()))
        .path(getPath(request))
        .build();
  }

  /**
   * Handles {@link VsumBuildingException} thrown during a VSUM build operation.
   *
   * <p>Responds with HTTP 400 (Bad Request) and a standardized {@link ErrorResponse} containing the
   * exception message and the request path.
   *
   * @param ex the {@code VsumBuildingException} that was thrown
   * @param handlerMethod the controller method where the exception originated
   * @param request the current {@link ServletWebRequest} providing request context
   * @return an {@link ErrorResponse} with the failure message and request path
   */
  @ExceptionHandler(value = VsumBuildingException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ResponseBody
  public ErrorResponse vsumBuildingException(
      VsumBuildingException ex, HandlerMethod handlerMethod, ServletWebRequest request) {
    return ErrorResponse.builder()
        .message(Objects.requireNonNull(ex.getMessage()))
        .path(getPath(request))
        .build();
  }

  /**
   * Handles {@link OwnerCannotAddSelfAsMemberException} thrown when an owner attempts to add
   * themselves as a member to a VSUM. Returns an {@link ErrorResponse} with HTTP 400 (Bad Request)
   * status, including the error message and request path.
   *
   * @param ex the thrown {@code OwnerCannotAddSelfAsMemberException}
   * @param handlerMethod the controller method where the exception was raised
   * @param request the current {@code ServletWebRequest}
   * @return a standardized {@code ErrorResponse} describing the forbidden self-add operation
   */
  @ExceptionHandler(value = OwnerCannotAddSelfAsMemberException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ResponseBody
  public ErrorResponse ownerCannotAddSelfAsMemberException(
      OwnerCannotAddSelfAsMemberException ex,
      HandlerMethod handlerMethod,
      ServletWebRequest request) {
    return ErrorResponse.builder()
        .message(Objects.requireNonNull(ex.getMessage()))
        .path(getPath(request))
        .build();
  }

  /**
   * Handles {@link OwnerRoleRemovalException} thrown when an attempt is made to remove the OWNER
   * role from a VSUM. Returns an {@link ErrorResponse} with HTTP 400 (Bad Request) status,
   * including the error message and request path.
   *
   * @param ex the thrown {@code OwnerRoleRemovalException}
   * @param handlerMethod the controller method where the exception was raised
   * @param request the current {@code ServletWebRequest}
   * @return a standardized {@code ErrorResponse} describing the forbidden owner role removal
   */
  @ExceptionHandler(value = OwnerRoleRemovalException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ResponseBody
  public ErrorResponse ownerRoleRemovalException(
      OwnerRoleRemovalException ex, HandlerMethod handlerMethod, ServletWebRequest request) {
    return ErrorResponse.builder()
        .message(Objects.requireNonNull(ex.getMessage()))
        .path(getPath(request))
        .build();
  }

  /**
   * Handles {@link DuplicateVsumMembershipException} thrown when attempting to add a user who is
   * already a member of a VSUM. Returns an {@link ErrorResponse} with HTTP 400 (Bad Request)
   * status, including the error message and request path.
   *
   * @param ex the thrown {@code VsumUserAlreadyMemberException}
   * @param handlerMethod the controller method where the exception was raised
   * @param request the current {@code ServletWebRequest}
   * @return a standardized {@code ErrorResponse} describing the duplicate membership error
   */
  @ExceptionHandler(value = DuplicateVsumMembershipException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ResponseBody
  public ErrorResponse duplicateVsumMembershipException(
      DuplicateVsumMembershipException ex, HandlerMethod handlerMethod, ServletWebRequest request) {
    return ErrorResponse.builder()
        .message(Objects.requireNonNull(ex.getMessage()))
        .path(getPath(request))
        .build();
  }

  /**
   * Handles {@link OwnerRequiredException} thrown when an operation requires ownership. Returns an
   * {@link ErrorResponse} with HTTP 401 (Unauthorized) status, including the error message and
   * request path.
   *
   * @param ex the thrown {@code OwnerRequiredException}
   * @param handlerMethod the controller method where the exception was raised
   * @param request the current {@code ServletWebRequest}
   * @return a standardized {@code ErrorResponse} describing the unauthorized access
   */
  @ExceptionHandler(value = OwnerRequiredException.class)
  @ResponseStatus(HttpStatus.UNAUTHORIZED)
  @ResponseBody
  public ErrorResponse ownerRequiredException(
      OwnerRequiredException ex, HandlerMethod handlerMethod, ServletWebRequest request) {
    return ErrorResponse.builder()
        .message(Objects.requireNonNull(ex.getMessage()))
        .path(getPath(request))
        .build();
  }

  /**
   * Handles {@link FileHashingException} thrown when computing a file's SHA-256 fails. Returns an
   * {@link ErrorResponse} with HTTP 500 (Internal Server Error), including the error code, message,
   * and request path.
   *
   * @param ex the thrown {@code FileHashingException}
   * @param handlerMethod the controller method where the exception was raised
   * @param request the current {@code ServletWebRequest}
   * @return a standardized {@code ErrorResponse} describing the hashing failure
   */
  @ExceptionHandler(value = FileHashingException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ResponseBody
  public ErrorResponse fileHashingException(
      FileHashingException ex, HandlerMethod handlerMethod, ServletWebRequest request) {
    return ErrorResponse.builder()
        .message(Objects.requireNonNull(ex.getMessage()))
        .path(getPath(request))
        .build();
  }

  /**
   * Handles exceptions when attempting to upload a duplicate file. Returns a BAD_REQUEST (400)
   * response with details about the duplicate file.
   *
   * @param ex the exception containing details about the duplicate file
   * @param handlerMethod the handler method where the exception occurred
   * @param request the current web request
   * @return an ErrorResponse containing the error message and request path
   */
  @ExceptionHandler(value = FileAlreadyExistsException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ResponseBody
  public ErrorResponse fileAlreadyExistsException(
      FileAlreadyExistsException ex, HandlerMethod handlerMethod, ServletWebRequest request) {
    return ErrorResponse.builder()
        .error(FileAlreadyExistsException.MESSAGE_TEMPLATE)
        .message(Objects.requireNonNull(ex.getMessage()))
        .path(getPath(request))
        .build();
  }

  /**
   * Handles exceptions when attempting to add a user to a VSUM with a role they already have.
   * Returns a BAD_REQUEST (400) response with details about the duplicate user-role assignment.
   *
   * @param ex the exception containing details about the duplicate user-role assignment
   * @param handlerMethod the handler method where the exception occurred
   * @param request the current web request
   * @return an ErrorResponse containing the error details and request path
   */
  @ExceptionHandler(value = UserAlreadyExistInVsumWithSameRoleException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ResponseBody
  public ErrorResponse userAlreadyExistInVsumWithSameRoleException(
      UserAlreadyExistInVsumWithSameRoleException ex,
      HandlerMethod handlerMethod,
      ServletWebRequest request) {
    return ErrorResponse.builder()
        .error(UserAlreadyExistInVsumWithSameRoleException.MESSAGE_TEMPLATE)
        .message(Objects.requireNonNull(ex.getMessage()))
        .path(getPath(request))
        .build();
  }

  /**
   * Handles {@link MetaModelUsedInVsumException} thrown when a metamodel is in use by a VSUM.
   * Returns an {@link ErrorResponse} with HTTP 400 (Bad Request) status, including the error
   * message and request path.
   *
   * @param ex the thrown {@code MetaModelUsingInVsumException}
   * @param handlerMethod the controller method where the exception was raised
   * @param request the current {@code ServletWebRequest}
   * @return an {@code ErrorResponse} describing the conflict
   */
  @ExceptionHandler(value = MetaModelUsedInVsumException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ResponseBody
  public ErrorResponse metaModelUsedInVsumException(
      MetaModelUsedInVsumException ex, HandlerMethod handlerMethod, ServletWebRequest request) {
    return ErrorResponse.builder()
        .message(Objects.requireNonNull(ex.getMessage()))
        .path(getPath(request))
        .build();
  }

  /**
   * Handles cases where building or validating an MWE2 file fails. When a {@link
   * CreateMwe2FileException} is thrown anywhere in the application, this handler captures it and
   * returns a structured {@link ErrorResponse}. The response includes: The response is sent with
   * HTTP 400 (Bad Request).
   *
   * @param ex the thrown exception containing the reason for rejection
   * @param handlerMethod the controller method where the exception was raised
   * @param request the web request containing request context
   * @return an {@link ErrorResponse} describing the failure
   */
  @ExceptionHandler(value = CreateMwe2FileException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ResponseBody
  public ErrorResponse createMwe2FileException(
      CreateMwe2FileException ex, HandlerMethod handlerMethod, ServletWebRequest request) {
    return ErrorResponse.builder()
        .error(CreateMwe2FileException.MESSAGE_TEMPLATE)
        .message(Objects.requireNonNull(ex.getMessage()))
        .path(getPath(request))
        .build();
  }

  /**
   * Handles {@link tools.vitruv.methodologist.exception.UnauthorizedException} thrown by controller
   * methods. Returns an {@link tools.vitruv.methodologist.exception.ErrorResponse} with HTTP 401
   * (Unauthorized) status, including the error message, error code, and request path. This method
   * ensures clients receive a consistent error response format when authentication or authorization
   * fails.
   *
   * @param ex the {@code UnauthorizedException} that was thrown
   * @param handlerMethod the Spring {@code HandlerMethod} where the exception occurred
   * @param request the current {@code ServletWebRequest}
   * @return a standardized {@code ErrorResponse} describing the unauthorized error
   */
  @ExceptionHandler(value = UnauthorizedException.class)
  @ResponseStatus(HttpStatus.UNAUTHORIZED)
  @ResponseBody
  public ErrorResponse unauthorizedException(
      UnauthorizedException ex, HandlerMethod handlerMethod, ServletWebRequest request) {
    return ErrorResponse.builder()
        .error(UnauthorizedException.MESSAGE_TEMPLATE)
        .message(Objects.requireNonNull(ex.getMessage()))
        .path(getPath(request))
        .build();
  }

  /**
   * Handles conflict exceptions related to user operations.
   *
   * @param ex the caught ConflictException
   * @param handlerMethod the handler method that threw the exception
   * @param request the current web request
   * @return ErrorResponse with conflict details
   */
  @ExceptionHandler(value = EmailExistsException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public ErrorResponse emailExistsException(
      EmailExistsException ex, HandlerMethod handlerMethod, ServletWebRequest request) {
    return ErrorResponse.builder().message(ex.getMessage()).path(getPath(request)).build();
  }

  /**
   * Handles bad request exceptions from HTTP client operations.
   *
   * @param ex the caught BadRequest exception
   * @param handlerMethod the handler method that threw the exception
   * @param request the current web request
   * @return ErrorResponse with bad request details
   */
  @ExceptionHandler(value = HttpClientErrorException.BadRequest.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ResponseBody
  public ErrorResponse badRequestException(
      HttpClientErrorException.BadRequest ex,
      HandlerMethod handlerMethod,
      ServletWebRequest request) {
    log.warn(
        BAD_REQUEST_ERROR,
        handlerMethod.getMethod().getDeclaringClass().getSimpleName(),
        ex.getMessage());
    log.debug(STACKTRACE_LOG, ex.toString());
    return ErrorResponse.builder()
        .error(BAD_REQUEST_ERROR)
        .message(Objects.requireNonNull(ex.getMessage()))
        .path(getPath(request))
        .build();
  }

  /**
   * Handles validation exceptions for method arguments.
   *
   * @param ex the caught MethodArgumentNotValidException
   * @param handlerMethod the handler method that threw the exception
   * @param request the current web request
   * @return ErrorResponse with validation error details
   */
  @ExceptionHandler(value = MethodArgumentNotValidException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ResponseBody
  public ErrorResponse methodArgumentNotValidException(
      MethodArgumentNotValidException ex, HandlerMethod handlerMethod, ServletWebRequest request) {
    log.warn(
        METHOD_ARGUMENT_NOT_VALID_EXCEPTION,
        handlerMethod.getMethod().getDeclaringClass().getSimpleName(),
        ex.getMessage());
    log.debug(STACKTRACE_LOG, ex.toString());
    var fieldError = ex.getBindingResult().getFieldError();

    String clientMessage;
    if (fieldError != null) {
      String fieldName = Objects.requireNonNullElse(fieldError.getField(), "field");
      String defaultMessage =
          Objects.requireNonNullElse(fieldError.getDefaultMessage(), "Invalid value");
      clientMessage = fieldName + ": " + defaultMessage;
    } else {
      clientMessage = Objects.requireNonNullElse(ex.getMessage(), "Validation failed");
    }

    return ErrorResponse.builder()
        .error(FORMAT_ERROR)
        .message(clientMessage)
        .path(getPath(request))
        .build();
  }

  /**
   * Handles access denied exceptions for unauthorized operations.
   *
   * @param ex the caught AccessDeniedException
   * @param handlerMethod the handler method that threw the exception
   * @param request the current web request
   * @return ErrorResponse with forbidden access details
   */
  @ExceptionHandler(value = AccessDeniedException.class)
  @ResponseStatus(HttpStatus.FORBIDDEN)
  @ResponseBody
  public ErrorResponse accessDeniedException(
      AccessDeniedException ex, HandlerMethod handlerMethod, ServletWebRequest request) {
    log.warn(
        METHOD_ARGUMENT_NOT_VALID_EXCEPTION,
        handlerMethod.getMethod().getDeclaringClass().getSimpleName(),
        ex.getMessage());
    log.debug(STACKTRACE_LOG, ex.toString());
    return ErrorResponse.builder()
        .error(FORBIDDEN_ERROR)
        .message(ex.getMessage())
        .path(getPath(request))
        .build();
  }

  /**
   * Handles general HTTP client errors.
   *
   * @param ex the caught HttpClientErrorException
   * @param handlerMethod the handler method that threw the exception
   * @param request the current web request
   * @return ResponseEntity containing ErrorResponse with client error details
   */
  @ExceptionHandler(value = HttpClientErrorException.class)
  public ResponseEntity<ErrorResponse> clientErrorException(
      HttpClientErrorException ex, HandlerMethod handlerMethod, ServletWebRequest request) {
    log.warn(
        "ClientErrorException handled in controller: {}, message: {} ",
        handlerMethod.getMethod().getDeclaringClass().getSimpleName(),
        ex.getMessage());
    log.debug(STACKTRACE_LOG, ex.toString());
    ErrorResponse errorResponse =
        ErrorResponse.builder().error(ex.getMessage().toUpperCase()).path(getPath(request)).build();
    return new ResponseEntity<>(errorResponse, ex.getStatusCode());
  }

  /**
   * Handles not found exceptions for missing resources.
   *
   * @param ex the caught NotFoundException
   * @param handlerMethod the handler method that threw the exception
   * @param request the current web request
   * @return ErrorResponse with not found details
   */
  @ExceptionHandler(value = NotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public ErrorResponse notFoundException(
      NotFoundException ex, HandlerMethod handlerMethod, ServletWebRequest request) {
    log.warn(
        "notFoundException handled in Controller: {}, message: {}, stackTrace: {}",
        handlerMethod.getMethod().getDeclaringClass().getSimpleName(),
        ex.getMessage(),
        ex);
    return ErrorResponse.builder()
        .error(NOT_FOUND_ERROR)
        .message(ex.getMessage())
        .path(getPath(request))
        .build();
  }

  /**
   * Handles uncaught runtime exceptions.
   *
   * @param ex the caught RuntimeException
   * @param handlerMethod the handler method that threw the exception
   * @param request the current web request
   * @return ErrorResponse with internal server error details
   */
  @ExceptionHandler(value = RuntimeException.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public ErrorResponse uncatchedException(
      RuntimeException ex, HandlerMethod handlerMethod, ServletWebRequest request) {
    log.warn(
        "UncatchedException handled in Controller: {}, message: {}, stackTrace: {}",
        handlerMethod.getMethod().getDeclaringClass().getSimpleName(),
        ex.getMessage(),
        ex);

    return ErrorResponse.builder()
        .error(INTERNAL_SERVER_ERROR)
        .message("")
        .path(getPath(request))
        .build();
  }

  /**
   * Handles exceptions for malformed request bodies. Provides specific error messages for type
   * resolution and deserialization issues.
   *
   * @param ex the caught HttpMessageNotReadableException
   * @param handlerMethod the handler method that threw the exception
   * @param request the current web request
   * @return ErrorResponse with format error details
   */
  @ExceptionHandler(value = HttpMessageNotReadableException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ErrorResponse httpMessageNotReadableException(
      HttpMessageNotReadableException ex, HandlerMethod handlerMethod, ServletWebRequest request) {
    log.warn(
        "HttpMessageNotReadableException handled in Controller: {}, message: {}, stackTrace: {}",
        handlerMethod.getMethod().getDeclaringClass().getSimpleName(),
        ex.getMessage(),
        ex);
    if (Objects.requireNonNull(ex.getRootCause())
        .getMessage()
        .startsWith("Could not resolve type id")) {
      return ErrorResponse.builder()
          .error(FORMAT_ERROR)
          .message(Objects.requireNonNull(ex.getRootCause()).getMessage().split("as")[0].trim())
          .path(getPath(request))
          .build();
    }
    if (Objects.requireNonNull(ex.getRootCause())
        .getMessage()
        .startsWith("Cannot deserialize value of type")) {
      return ErrorResponse.builder()
          .error(FORMAT_ERROR)
          .message(Objects.requireNonNull(ex.getRootCause()).getMessage().split(":")[0].trim())
          .path(getPath(request))
          .build();
    }

    return ErrorResponse.builder()
        .error(FORMAT_ERROR)
        .message(Objects.requireNonNull(ex.getRootCause()).getMessage())
        .path(getPath(request))
        .build();
  }

  /**
   * Extracts the request URI from the web request.
   *
   * @param request the current web request
   * @return the request URI as a string
   */
  private String getPath(ServletWebRequest request) {
    return request.getRequest().getRequestURI();
  }
}
