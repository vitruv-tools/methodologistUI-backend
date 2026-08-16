package tools.vitruv.methodologist.exception;

/**
 * Thrown when a FreeMarker low-code reaction template cannot be loaded or applied.
 */
public class LowCodeTemplateException extends RuntimeException {

  /**
   * Creates a new exception with a detail message and root cause.
   *
   * @param message human-readable description of the error
   * @param cause the underlying template or I/O failure
   */
  public LowCodeTemplateException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Creates a new exception with a detail message.
   *
   * @param message human-readable description of the error
   */
  public LowCodeTemplateException(String message) {
    super(message);
  }
}
