package tools.vitruv.methodologist.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when a meta-model relation cannot be created or updated, for example when
 * neither a reaction file nor a low-code template is provided.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class MetaModelRelationCreationException extends RuntimeException {

  /**
   * Constructs a new exception with the given detail message.
   *
   * @param message the detail message
   */
  public MetaModelRelationCreationException(String message) {
    super(message);
  }
}
