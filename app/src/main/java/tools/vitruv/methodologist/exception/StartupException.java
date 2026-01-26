package tools.vitruv.methodologist.exception;

/**
 * Thrown to indicate that the application failed to start correctly.
 *
 * <p>This is an unchecked exception intended for fatal startup problems that should prevent the
 * application from continuing initialization.
 *
 * @param message a detail message describing the startup failure
 */
public class StartupException extends RuntimeException {
  /**
   * Constructs a new {@code StartupException} with the specified detail message.
   *
   * @param message the detail message explaining the startup failure
   */
  public StartupException(String message) {
    super(message);
  }
}
