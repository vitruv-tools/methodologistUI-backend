package tools.vitruv.methodologist.exception;

/**
 * Exception indicating that a previously issued validation code (OTP) is expired.
 *
 * <p>This is an unchecked exception that carries a default message template available via {@link
 * #messageTemplate}. Throw this when an operation fails because the stored validation code is no
 * longer valid due to expiry.
 */
public class ValidationCodeExpiredException extends RuntimeException {
  public static final String MESSAGE_TEMPLATE = "The validation code is expired!";

  /**
   * Constructs a new ValidationCodeExpiredException with the default message.
   *
   * <p>The default detail message is provided by {@link #messageTemplate}.
   */
  public ValidationCodeExpiredException() {
    super(MESSAGE_TEMPLATE);
  }
}
