package tools.vitruv.methodologist.exception;

/**
 * Exception thrown when a user attempts to upload a file that is already stored in the system.
 * This0 exception is used to prevent duplicate file storage for the same user.
 */
public class AlreadyExistsFileException extends RuntimeException {
  public static final String messageTemplate = "This file used before by user!";

  /** Constructs a new UserFileIsRepeatedException with no detail message. */
  public AlreadyExistsFileException() {
    super(messageTemplate);
  }
}
