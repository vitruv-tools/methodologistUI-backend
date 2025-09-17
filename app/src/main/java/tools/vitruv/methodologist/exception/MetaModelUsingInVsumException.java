package tools.vitruv.methodologist.exception;

/**
 * Exception thrown when attempting to perform an operation on a metamodel that is currently in use
 * by one or more VSUMs.
 *
 * <p>This runtime exception indicates that the requested operation cannot be completed because the
 * metamodel is being referenced as a dependency in existing Virtual Software Unit Models (VSUMs).
 *
 * <p>The exception message will include the names of the VSUMs where the metamodel is being used.
 */
public class MetaModelUsingInVsumException extends RuntimeException {
  public static final String messageTemplate = "Meta model is using in %s";

  /**
   * Constructs a new MetaModelUsingInVsumException with the specified VSUM names.
   *
   * @param vsumNames a string containing the names of the VSUMs using the metamodel
   */
  public MetaModelUsingInVsumException(String vsumNames) {
    super(String.format(messageTemplate, vsumNames));
  }
}
