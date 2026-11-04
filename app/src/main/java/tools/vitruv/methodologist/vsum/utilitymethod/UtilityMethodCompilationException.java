package tools.vitruv.methodologist.vsum.utilitymethod;

/**
 * Thrown when the utility methods cannot be compiled for a reason other than a mistake in them, for
 * example because the metamodel API does not compile, the backend has no Java compiler or the
 * compilation takes too long.
 */
public class UtilityMethodCompilationException extends RuntimeException {

  /**
   * Creates the exception.
   *
   * @param message what went wrong, shown to the methodologist
   */
  public UtilityMethodCompilationException(String message) {
    super(message);
  }

  /**
   * Creates the exception.
   *
   * @param message what went wrong, shown to the methodologist
   * @param cause the underlying failure
   */
  public UtilityMethodCompilationException(String message, Throwable cause) {
    super(message, cause);
  }
}
