package tools.vitruv.methodologist.exception;

/**
 * Unchecked exception indicating a failure while building or sending an email.
 *
 * <p>Typically wraps lower-level issues such as mail server connectivity problems, or errors
 * encountered when constructing the message. Use this to signal a domain-specific mail sending
 * failure instead of throwing generic exceptions.
 */
public class MailSendingException extends RuntimeException {
  /**
   * Creates a new {@code MailSendingException} with a detail message and root cause.
   *
   * @param message human-readable description of the error
   * @param cause underlying cause of the hashing failure
   */
  public MailSendingException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Creates a new {@code MailSendingException} with a detail message.
   *
   * @param message human-readable description of the error
   */
  public MailSendingException(String message)  {super(message); }
}
