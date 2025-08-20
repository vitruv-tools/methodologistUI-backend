package com.vitruv.methodologist.exception;

/**
 * Custom runtime exception for handling unexpected errors in the application. Used to wrap
 * unchecked exceptions that should be propagated up the call stack.
 *
 * @extends RuntimeException
 */
public class UncaughtRuntimeException extends RuntimeException {

  /**
   * Custom runtime exception for handling unexpected errors in the application. Used to wrap
   * unchecked exceptions that should be propagated up the call stack.
   *
   * @extends RuntimeException
   */
  public UncaughtRuntimeException(String message) {
    super(message);
  }
}
