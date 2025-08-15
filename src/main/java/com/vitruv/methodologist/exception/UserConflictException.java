package com.vitruv.methodologist.exception;

/**
 * Exception thrown when a user-related conflict occurs, such as duplicate email registration. Used
 * to indicate that a resource is already in use.
 */
public class UserConflictException extends RuntimeException {
  public static final String USER_CONFLICT_ERROR = "USER_CONFLICT";
  public static final String messageTemplate = "%s is already in use!";

  public UserConflictException(String email) {
    super(String.format(messageTemplate, email));
  }
}
