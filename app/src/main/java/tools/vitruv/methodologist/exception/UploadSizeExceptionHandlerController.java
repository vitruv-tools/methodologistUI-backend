package tools.vitruv.methodologist.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Answers uploads above {@code spring.servlet.multipart.max-file-size} or {@code max-request-size}
 * with HTTP 413 and an {@link ErrorResponse} naming the limits.
 *
 * <p>The multipart request is parsed before a controller is chosen, so there is no handler and
 * {@link GlobalExceptionHandlerController}, which is restricted to the application's packages, is
 * never asked. This advice therefore has no package restriction; it handles only this exception.
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class UploadSizeExceptionHandlerController {
  private static final String PAYLOAD_TOO_LARGE_ERROR = "PAYLOAD_TOO_LARGE";

  private final String message;

  /**
   * Creates the handler with the configured upload limits.
   *
   * @param maxFileSize the maximum size of a single uploaded file
   * @param maxRequestSize the maximum size of a whole multipart request
   */
  public UploadSizeExceptionHandlerController(
      @Value("${spring.servlet.multipart.max-file-size:1MB}") DataSize maxFileSize,
      @Value("${spring.servlet.multipart.max-request-size:10MB}") DataSize maxRequestSize) {
    this.message =
        "The upload is too large. Each file may be at most %d MB and the whole request at most %d MB."
            .formatted(maxFileSize.toMegabytes(), maxRequestSize.toMegabytes());
  }

  /**
   * Handles {@link MaxUploadSizeExceededException} thrown when an uploaded file or the whole
   * multipart request exceeds the configured limits.
   *
   * @param ex the thrown {@code MaxUploadSizeExceededException}
   * @param request the current {@code ServletWebRequest}
   * @return an {@code ErrorResponse} stating the upload limits
   */
  @ExceptionHandler(MaxUploadSizeExceededException.class)
  @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
  public ErrorResponse maxUploadSizeExceededException(
      MaxUploadSizeExceededException ex, ServletWebRequest request) {
    log.debug("{} handled", ex.getClass().getSimpleName());
    return ErrorResponse.builder()
        .error(PAYLOAD_TOO_LARGE_ERROR)
        .message(message)
        .path(request.getRequest().getRequestURI())
        .build();
  }
}
