package tools.vitruv.methodologist.vsum.controller.dto.response;

import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Response DTO representing a relation between two meta models.
 *
 * <p>{@code sourceId} and {@code targetId} are original meta-model ids. The coarse reaction file is
 * optional when the relation is defined only by fine-granular children.
 */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MetaModelRelationResponse {
  private Long id;
  private Long sourceId;
  private Long targetId;
  private Long reactionFileStorageId;
  private Set<FineGranularMetaModelRelationResponse> fineGranularMetaModelRelationSet;
}
