package tools.vitruv.methodologist.vsum.controller.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request DTO for creating or updating a relation between two meta models.
 *
 * <p>Contains the IDs of the source and target meta models and an optional reaction file ID.
 * Validation annotations mark required properties. Lombok generates getters, setters, constructors,
 * and a builder.
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MetaModelRelationRequest {
  @NotNull private Long sourceId;
  @NotNull private Long targetId;
  @NotNull private Long reactionFileId;
}
