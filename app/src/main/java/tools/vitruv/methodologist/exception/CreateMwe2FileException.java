package tools.vitruv.methodologist.exception;

/**
 * Runtime exception thrown when generation of an MWE2 file fails. Wraps the specific reason for
 * rejection in a formatted message.
 */
public class CreateMwe2FileException extends RuntimeException {
  public static final String MESSAGE_TEMPLATE = "Metamodel rejected: %s";

  /**
   * Creates a new exception with the given failure reason. The reason is inserted into the
   * predefined message template.
   */
  public CreateMwe2FileException(String reason) {
    super(String.format(MESSAGE_TEMPLATE, reason));
  }
}
