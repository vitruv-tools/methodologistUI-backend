package tools.vitruv.methodologist.exception;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Value;

/**
 * Data transfer object representing error details returned by the API. Contains timestamp, error
 * type, message, and request path for debugging and client feedback. Errors of the setup-service
 * additionally carry its error code and the problems it found in the uploaded files.
 */
@Value
@Builder
public class ErrorResponse {
  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSxxx")
  @Builder.Default
  private ZonedDateTime timestamp = ZonedDateTime.now(ZoneId.of("UTC"));

  private String error;

  private String message;

  private String path;

  /** Error code of the setup-service, e.g. {@code VSUM_BUILD_FAILED}; absent for other errors. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String errorCode;

  /** Problems of a failed VSUM build, root causes first; absent if there are none. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private List<BuildProblem> problems;
}
