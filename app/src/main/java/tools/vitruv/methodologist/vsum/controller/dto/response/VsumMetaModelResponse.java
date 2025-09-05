package tools.vitruv.methodologist.vsum.controller.dto.response;

import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Response DTO representing a VsumMetaModel and its associated metadata. Contains identifying
 * information, timestamps, and the list of linked MetaModels.
 */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class VsumMetaModelResponse {
  private Long id;
  private String name;
  private Instant createdAt;
  private Instant updatedAt;
  private Instant removedAt;
  private List<MetaModelResponse> metaModels;
}
