package tools.vitruv.methodologist.exception;

/**
 * Exception thrown when a requested object is not found. Used to indicate resource absence in
 * service or repository layers.
 */
public class NotFoundException extends RuntimeException {
  public static final String MESSAGE_TEMPLATE = "%s not found!";

  /**
   * Constructs a new {@code NotFoundException} with a formatted message.
   *
   * @param objectName the name of the object that was not found
   */
  public NotFoundException(String objectName) {
    super(String.format(MESSAGE_TEMPLATE, objectName));
  }
}
