package tools.vitruv.methodologist.exception;

/**
 * Custom runtime exception for handling unauthorized access attempts. Thrown when a user attempts
 * to access a resource without proper authentication or authorization.
 *
 * @extends RuntimeException
 */
public class UnauthorizedException extends RuntimeException {
  public static final String messageTemplate = "Unauthorized access";

  /** Constructs a new UnauthorizedException with the default error message. */
  public UnauthorizedException() {
    super(messageTemplate);
  }
}
