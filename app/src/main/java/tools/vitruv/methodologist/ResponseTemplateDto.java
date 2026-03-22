package tools.vitruv.methodologist;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * Generic DTO for API responses.
 *
 * @param <T> the type of the data contained in the response
 */
@Setter
@Getter
@Builder
@AllArgsConstructor
public class ResponseTemplateDto<T> {
  /** The data returned in the response. */
  private T data;
  /** A message providing additional information about the response. */
  private String message;
}
