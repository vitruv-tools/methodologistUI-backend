package com.vitruv.methodologist.exception;

/**
 * A runtime exception that represents unchecked errors within the application.
 *
 * <p>This is a custom wrapper around {@link RuntimeException} to provide a clearer semantic meaning
 * in the codebase.
 */
public class UncheckedRuntimeException extends RuntimeException {

  /**
   * Constructs a new {@code UncheckedRuntimeException} with the specified detail message.
   *
   * @param message the detail message describing the cause of the exception
   */
  public UncheckedRuntimeException(String message) {
    super(message);
  }
}
