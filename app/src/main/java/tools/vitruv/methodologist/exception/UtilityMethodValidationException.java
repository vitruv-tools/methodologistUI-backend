package tools.vitruv.methodologist.exception;

/**
 * Thrown when a utility method cannot be saved or deleted, e.g. because its name is taken, its
 * Xtend body does not compile or other utility methods still call it.
 */
public class UtilityMethodValidationException extends RuntimeException {

  /**
   * Creates a new exception with a detail message.
   *
   * @param message human-readable description of the problem
   */
  public UtilityMethodValidationException(String message) {
    super(message);
  }
}
