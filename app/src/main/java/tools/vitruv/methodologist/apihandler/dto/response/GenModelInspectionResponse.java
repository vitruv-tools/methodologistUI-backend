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
 *
 * <p>{@code data} is an opaque JSON array whose element shape is owned by the setup-service. It is
 * typed as {@link Serializable} rather than {@link Object} so the field takes part in Java
 * serialization like the rest of the payload: Jackson binds a {@code Serializable} target the same
 * way it binds {@code Object} (untyped JSON values), and every value it produces for untyped JSON
 * is itself {@code Serializable}.
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
  private List<Serializable> data;
}
