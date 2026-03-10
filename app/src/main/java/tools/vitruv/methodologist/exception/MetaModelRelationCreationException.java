package tools.vitruv.methodologist.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when a metamodel relation cannot be created.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class MetaModelRelationCreationException extends RuntimeException {
    /**
     * Constructs a new {@code NotFoundException} with a formatted message.
     *
     * @param message the message
     */
    public MetaModelRelationCreationException(String message) {
        super(message);
    }
}

