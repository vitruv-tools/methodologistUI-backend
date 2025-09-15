package tools.vitruv.methodologist.exception;


/**
 * Exception thrown when attempting to add a user to a VSUM with a role they already have. This
 * runtime exception indicates a duplicate user-role assignment attempt within a VSUM.
 */
public class UserAlreadyExistInVsumWithSameRoleException extends RuntimeException {
  public static final String messageTemplate = "%s is already in %s project with role %s!";

  /**
   * Constructs a new exception with a formatted message using the provided details.
   *
   * @param userName the name of the user that already exists
   * @param vsumName the name of the VSUM project
   * @param vsumRoleName the name of the role that the user already has
   */
  public UserAlreadyExistInVsumWithSameRoleException(
      String userName, String vsumName, String vsumRoleName) {
    super(String.format(messageTemplate, userName, vsumName, vsumRoleName));
  }
}
