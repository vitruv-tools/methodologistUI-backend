package tools.vitruv.methodologist.exception;

/**
 * Exception thrown when an attempt is made to remove the OWNER role from a VSUM. This operation is
 * not permitted to ensure the integrity of VSUM ownership.
 */
public class OwnerCannotAddSelfAsMemberException extends RuntimeException {
  public static final String MESSAGE_TEMPLATE = "The owner cannot add themselves as a member.";

  /**
   * Constructs a new OwnerRoleRemovalException with a default message indicating that the OWNER
   * role cannot be removed from a VSUM.
   */
  public OwnerCannotAddSelfAsMemberException() {
    super(MESSAGE_TEMPLATE);
  }
}
