package tools.vitruv.methodologist.exception;

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

  /**
   * Constructs a new {@code UncheckedRuntimeException} with the specified detail message and cause.
   *
   * @param message the detail message describing what failed
   * @param cause the exception that caused the failure
   */
  public UncheckedRuntimeException(String message, Throwable cause) {
    super(message, cause);
  }
}
