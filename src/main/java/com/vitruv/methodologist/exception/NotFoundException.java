package com.vitruv.methodologist.exception;

/**
 * Exception thrown when a requested object is not found.
 * Used to indicate resource absence in service or repository layers.
 */
public class NotFoundException extends RuntimeException {
  public static final String messageTemplate = "%s not found!";

  /**
   * Constructs a new {@code UserConflictException} with a formatted message
   */
  public NotFoundException(String objectName) {
    super(String.format(messageTemplate, objectName));
  }
}
