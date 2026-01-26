package tools.vitruv.methodologist.exception;

/**
 * Thrown when a previously issued validation or verification code is still valid and a new code
 * cannot be generated yet.
 *
 * <p>Used to enforce a cooldown period between issuing validation codes (for example email or SMS
 * verification codes). Consumers can catch this exception to return an appropriate client-facing
 * response (e.g. HTTP 400 with an explanatory message).
 */
public class ValidationCodeNotExpiredYetException extends RuntimeException {
  public static final String messageTemplate = "The previous code is still valid!";

  /** Constructs a new {@code ValidationCodeNotExpiredYetException} with the standard message. */
  public ValidationCodeNotExpiredYetException() {
    super(messageTemplate);
  }
}
