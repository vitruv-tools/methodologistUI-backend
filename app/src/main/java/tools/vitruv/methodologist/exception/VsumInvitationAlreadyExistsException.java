package tools.vitruv.methodologist.exception;

/**
 * Exception thrown when a pending invitation already exists for the same email and VSUM.
 *
 * <p>Used to prevent storing duplicate pending invitations for an unregistered invitee.
 */
public class VsumInvitationAlreadyExistsException extends RuntimeException {
  public static final String MESSAGE_TEMPLATE =
      "An invitation for this email already exists for this Vsum.";

  /** Constructs a new {@code VsumInvitationAlreadyExistsException} with a default message. */
  public VsumInvitationAlreadyExistsException() {
    super(MESSAGE_TEMPLATE);
  }
}
