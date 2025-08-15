package com.vitruv.methodologist.exception;

/**
 * Exception thrown when a user conflict occurs, such as when an email is already in use.
 */
public class UserConflictException extends RuntimeException {
  public static final String USER_CONFLICT_ERROR = "USER_CONFLICT";
  public static final String messageTemplate = "%s is already in use!";

  /**
   * Constructs a new {@code UserConflictException} with a formatted message
   * indicating the email that is already in use.
   *
   * @param email the email address that caused the conflict
   */
  public UserConflictException(String email) {
    super(String.format(messageTemplate, email));
  }
}
