package tools.vitruv.methodologist.vsum.controller.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Response DTO representing a relation between two meta models.
 *
 * <p>Provides the relation identifier, the IDs of the source and target meta models, and an
 * optional reaction file storage ID.
 *
 * <p>Lombok generates getters, setters, constructors, and a builder.
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
}
