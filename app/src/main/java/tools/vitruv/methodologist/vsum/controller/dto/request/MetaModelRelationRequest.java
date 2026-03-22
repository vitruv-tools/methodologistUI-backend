package tools.vitruv.methodologist.vsum.controller.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.Objects;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tools.vitruv.methodologist.vsum.mapper.LowCodeReactionRequestMapper;
import tools.vitruv.methodologist.vsum.model.MetaModelRelation;

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
  private Long id;
  @NotNull private Long sourceId;
  @NotNull private Long targetId;
  private Long reactionFileId;
  private Set<FineGranularMetaModelRelationRequest> fineGranularMetaModelRelationSet;

  /**
   * Constructs a new MetaModelRelationRequest.
   *
   * @param sourceId the ID of the source meta model
   * @param targetId the ID of the target meta model
   * @param reactionFileId the ID of the reaction file
   * @param fineGranularMetaModelRelationSet the set of fine-granular meta-model relation requests
   */
  public MetaModelRelationRequest(
      long sourceId,
      long targetId,
      long reactionFileId,
      Set<FineGranularMetaModelRelationRequest> fineGranularMetaModelRelationSet) {
    this(null, sourceId, targetId, reactionFileId, fineGranularMetaModelRelationSet);
  }

  /**
   * Compares this request with a MetaModelRelation entity.
   *
   * @param lowCodeReactionRequestMapper the mapper for low-code reaction requests
   * @param metaModelRelation            the entity to compare with
   * @return true if the request matches the entity, false otherwise
   */
  public boolean equals(
      LowCodeReactionRequestMapper lowCodeReactionRequestMapper,
      MetaModelRelation metaModelRelation) {
    if (id != null && !Objects.equals(id, metaModelRelation.getId())) {
      return false;
    }
    if (!Objects.equals(sourceId, metaModelRelation.getSource().getSource().getId())) {
      return false;
    }
    if (!Objects.equals(targetId, metaModelRelation.getTarget().getSource().getId())) {
      return false;
    }
    if (!Objects.equals(
        reactionFileId,
        metaModelRelation.getReactionFileStorage() == null
            ? null
            : metaModelRelation.getReactionFileStorage().getId())) {
      return false;
    }
    if (fineGranularMetaModelRelationSet.size()
        != metaModelRelation.getFineGranularMetaModelRelationSet().size()) {
      return false;
    }
    for (var fgmmr : fineGranularMetaModelRelationSet) {
      // Since we checked for size, we can safely assume that this check validates the sets are
      // equal if it passes for all elements
      if (metaModelRelation.getFineGranularMetaModelRelationSet().stream()
          .noneMatch(rel -> fgmmr.equals(lowCodeReactionRequestMapper, rel))) {
        return false;
      }
    }
    return true;
  }
}
