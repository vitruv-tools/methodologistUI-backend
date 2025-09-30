package tools.vitruv.methodologist.exception;

/**
 * Exception thrown when a user attempts to upload a file that is already stored in the system.
 * This0 exception is used to prevent duplicate file storage for the same user.
 */
public class FileAlreadyExistsException extends RuntimeException {
  public static final String MESSAGE_TEMPLATE = "This file already exists.";

  /** Constructs a new UserFileIsRepeatedException with no detail message. */
  public FileAlreadyExistsException() {
    super(MESSAGE_TEMPLATE);
  }
}
