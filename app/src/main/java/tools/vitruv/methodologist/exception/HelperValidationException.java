package tools.vitruv.methodologist.exception;

/**
 * Thrown when a helper cannot be saved or deleted, e.g. because its name is taken, its OCL
 * definition cannot be translated to Java or other helpers still call it.
 */
public class HelperValidationException extends RuntimeException {

  /**
   * Creates a new exception with a detail message.
   *
   * @param message human-readable description of the problem
   */
  public HelperValidationException(String message) {
    super(message);
  }
}
