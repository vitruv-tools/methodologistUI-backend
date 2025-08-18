package com.vitruv.methodologist.exception;

/**
 * Exception thrown when attempting to create a VSUM with a name that already exists.
 * Extends RuntimeException for unchecked exception handling.
 */
public class VsumAlreadyExistException extends RuntimeException {
  public static final String messageTemplate = "%s is already in use!";

  /**
   * Constructs a new exception with a formatted message using the given VSUM name.
   *
   * @param name the name that caused the conflict
   */
  public VsumAlreadyExistException(String name) {
    super(String.format(messageTemplate, name));
  }
}
