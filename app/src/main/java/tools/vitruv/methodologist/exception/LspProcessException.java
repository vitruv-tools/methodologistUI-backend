package tools.vitruv.methodologist.exception;

/**
 * Exception thrown when managing the lifecycle of a Language Server Protocol (LSP) server process
 * fails, for example while waiting for the process to terminate during session cleanup.
 */
public class LspProcessException extends RuntimeException {

  /**
   * Creates a new {@code LspProcessException} with all messages.
   *
   * @param message human-readable description of the error
   * @param cause underlying cause of the failure
   */
  public LspProcessException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Creates a new {@code LspProcessException} with all messages.
   *
   * @param message human-readable description of the error
   */
  public LspProcessException(String message) {
    super(message);
  }
}
