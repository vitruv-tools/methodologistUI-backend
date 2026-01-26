package tools.vitruv.methodologist.exception;

/**
 * Thrown when a provided verification code is invalid or cannot be accepted.
 *
 * <p>Used in verification flows (e.g., email or account verification) to indicate that the supplied
 * code is incorrect, expired, or otherwise unacceptable.
 */
public class VerificationCodeException extends RuntimeException {
  public static final String VERIFICATION_CODE_IS_NOT_VALID = "Verification cod is not valid!";

  /** Constructs a new {@code VerificationCodeException} with the standard error message. */
  public VerificationCodeException() {
    super(String.format(VERIFICATION_CODE_IS_NOT_VALID));
  }
}
