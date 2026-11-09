package tools.vitruv.methodologist.exception;

/** Thrown when a VSUM is published while an earlier publish of it is still building. */
public class PublishInProgressException extends RuntimeException {

  /**
   * Creates a new exception with a detail message.
   *
   * @param message human-readable description of the problem
   */
  public PublishInProgressException(String message) {
    super(message);
  }
}
