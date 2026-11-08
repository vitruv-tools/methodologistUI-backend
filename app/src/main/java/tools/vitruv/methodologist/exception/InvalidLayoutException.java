package tools.vitruv.methodologist.exception;

/** Thrown when a VSUM canvas layout is not a JSON object or exceeds the maximum allowed size. */
public class InvalidLayoutException extends RuntimeException {

  /**
   * Creates a new exception with a detail message.
   *
   * @param message human-readable description of the problem
   */
  public InvalidLayoutException(String message) {
    super(message);
  }
}
