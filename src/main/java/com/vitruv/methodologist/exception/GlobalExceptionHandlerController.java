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

package com.vitruv.methodologist.exception;

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
import java.util.Objects;

/**
 * Handles exceptions thrown by controllers in the application and returns standardized error
 * responses. Provides specific handlers for common exceptions and logs error details for debugging.
 */
@Slf4j
@RestControllerAdvice(basePackages = "com.vitruv.methodologist")
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
   * Handles conflict exceptions related to user operations.
   *
   * @param ex the caught ConflictException
   * @param handlerMethod the handler method that threw the exception
   * @param request the current web request
   * @return ErrorResponse with conflict details
   */
  @ExceptionHandler(value = ConflictException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public ErrorResponse conflictException(
      ConflictException ex, HandlerMethod handlerMethod, ServletWebRequest request) {
    return ErrorResponse.builder()
        .error(ConflictException.USER_CONFLICT_ERROR)
        .message(ex.getMessage())
        .path(getPath(request))
        .build();
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
    return ErrorResponse.builder()
        .error(FORMAT_ERROR)
        .message(Objects.requireNonNull(ex.getFieldError()).getField())
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
    var errorResponse =
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
   * Handles exceptions for malformed request bodies.
   * Provides specific error messages for type resolution and deserialization issues.
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
