package tools.vitruv.methodologist.apihandler.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response returned by the setup-service {@code /api/genmodel/inspect} endpoint.
 *
 * <p>On success the service returns {@code data} and a {@code message}. When the GenModel has a
 * problem the service responds with HTTP 422 and an error payload ({@code errorCode}, {@code
 * message}, {@code path}, {@code status}, {@code timestamp}). All fields are mapped into this
 * single DTO so the caller can surface the {@code message} either way.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GenModelInspectionResponse implements Serializable {
  private String errorCode;
  private String message;
  private String path;
  private Integer status;
  private Long timestamp;
  private List<Object> data;
}
