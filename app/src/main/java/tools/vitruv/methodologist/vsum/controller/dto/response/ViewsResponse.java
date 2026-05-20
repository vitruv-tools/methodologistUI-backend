package tools.vitruv.methodologist.vsum.controller.dto.response;

import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Response DTO representing a VSUM view (NeoJoin configuration).
 *
 * <p>Provides the view identifier, the associated file storage identifier, creation timestamp, and
 * all metamodels assigned to the view.
 *
 * <p>Lombok generates getters, setters, constructors, and a builder.
 */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ViewsResponse {
  private Long id;
  private Long fileStorageId;
  private Instant createdAt;
  private List<MetaModelResponse> assignedModels;
}
