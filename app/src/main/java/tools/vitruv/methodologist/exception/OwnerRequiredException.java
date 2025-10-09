package tools.vitruv.methodologist.exception;

/**
 * Exception thrown when an operation requires the current user to be the owner.
 *
 * <p>Typically used to enforce access control where only the owner is permitted to perform certain
 * actions.
 */
public class OwnerRequiredException extends RuntimeException {
  public static final String MESSAGE_TEMPLATE = "Only the owner has access.";

  /**
   * Constructs a new {@code OwnerRequiredException} with a default message indicating that only the
   * owner has access.
   */
  public OwnerRequiredException() {
    super(MESSAGE_TEMPLATE);
  }
}
