package tools.vitruv.methodologist.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when a requested object is not found. Used to indicate resource absence in
 * service or repository layers.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
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
