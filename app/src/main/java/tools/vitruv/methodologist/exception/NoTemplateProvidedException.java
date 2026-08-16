package tools.vitruv.methodologist.exception;

import static tools.vitruv.methodologist.messages.Error.NO_TEMPLATE_PROVIDED_ERROR;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when a fine-granular relation is created or updated without a reaction file or
 * a low-code template.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class NoTemplateProvidedException extends MetaModelRelationCreationException {

  /**
   * Constructs a new exception with {@link
   * tools.vitruv.methodologist.messages.Error#NO_TEMPLATE_PROVIDED_ERROR}.
   */
  public NoTemplateProvidedException() {
    super(NO_TEMPLATE_PROVIDED_ERROR);
  }
}
