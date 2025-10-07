package tools.vitruv.methodologist.exception;

/**
 * Exception thrown when attempting to add a user who is already a member of the specified VSUM.
 *
 * <p>Used to prevent duplicate user membership in a VSUM.
 */
public class VsumUserAlreadyMemberException extends RuntimeException {
  public static final String MESSAGE_TEMPLATE = "The user is already a member of this Vsum.";

  /** Constructs a new VsumUserAlreadyMemberException with a default message. */
  public VsumUserAlreadyMemberException() {
    super(MESSAGE_TEMPLATE);
  }
}
