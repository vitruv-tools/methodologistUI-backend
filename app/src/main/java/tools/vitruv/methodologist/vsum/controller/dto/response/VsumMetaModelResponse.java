package tools.vitruv.methodologist.vsum.controller.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tools.vitruv.methodologist.vsum.VsumRole;

/**
 * Response DTO representing a VsumMetaModel and its associated metadata. Contains identifying
 * information, timestamps, the caller's ID and role in the VSUM, and the list of linked MetaModels,
 * MetaModel relations, and views.
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
  private List<MetaModelRelationResponse> metaModelsRelation;
  private List<ViewsResponse> views;
  private VsumRole role;

  /**
   * ID of the calling user, whose role is {@code role}. Set only by {@code GET
   * /api/v1/vsums/{id}/details}; the collaboration server identifies its connections by it.
   */
  private Long userId;

  private JsonNode layout;
}
