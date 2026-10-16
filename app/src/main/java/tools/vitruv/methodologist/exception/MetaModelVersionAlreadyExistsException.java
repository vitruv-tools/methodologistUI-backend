package tools.vitruv.methodologist.exception;

/**
 * Thrown when a user tries to keep two library metamodels with the same name and version. Another
 * version of the same name is allowed.
 */
public class MetaModelVersionAlreadyExistsException extends RuntimeException {
  public static final String MESSAGE_TEMPLATE =
      "A metamodel with this name and version already exists.";

  /** Constructs the exception with {@link #MESSAGE_TEMPLATE}. */
  public MetaModelVersionAlreadyExistsException() {
    super(MESSAGE_TEMPLATE);
  }
}
