package tools.vitruv.methodologist.exception;

/**
 * Exception thrown when attempting to register or update a user with an email address that already
 * exists in the system.
 *
 * <p>This exception indicates a conflict between the requested email and an existing user record.
 */
public class EmailExistsException extends RuntimeException {
  public static final String MESSAGE_TEMPLATE = "%s is already in use!";

  /**
   * Constructs a new {@code EmailExistsException} with a formatted message.
   *
   * @param email the email address that caused the conflict
   */
  public EmailExistsException(String email) {
    super(String.format(MESSAGE_TEMPLATE, email));
  }
}
