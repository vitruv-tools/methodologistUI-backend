package tools.vitruv.methodologist.vsum.controller.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.HashSet;
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
 * <p>{@code reactionFileId} is optional when {@code fineGranularMetaModelRelationSet} is non-empty.
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

  @Valid @Builder.Default
  private Set<FineGranularMetaModelRelationRequest> fineGranularMetaModelRelationSet =
      new HashSet<>();

  /**
   * Convenience constructor used by tests and callers that only set the coarse pair and reaction
   * file.
   *
   * @param sourceId the original source meta-model id
   * @param targetId the original target meta-model id
   * @param reactionFileId the coarse reaction file id; may be {@code null}
   */
  public MetaModelRelationRequest(Long sourceId, Long targetId, Long reactionFileId) {
    this(null, sourceId, targetId, reactionFileId, new HashSet<>());
  }

  /**
   * Returns the fine-granular set, never {@code null}.
   *
   * @return the nested fine-granular requests
   */
  public Set<FineGranularMetaModelRelationRequest> getFineGranularMetaModelRelationSet() {
    if (fineGranularMetaModelRelationSet == null) {
      fineGranularMetaModelRelationSet = new HashSet<>();
    }
    return fineGranularMetaModelRelationSet;
  }

  /**
   * Compares this request with a persisted coarse relation, including nested fine-granular
   * children.
   *
   * @param lowCodeReactionRequestMapper mapper used to compare stored template params
   * @param metaModelRelation the entity to compare with
   * @return true if the request already matches the entity
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
    Long existingFileId =
        metaModelRelation.getReactionFileStorage() == null
            ? null
            : metaModelRelation.getReactionFileStorage().getId();
    if (!Objects.equals(reactionFileId, existingFileId)) {
      return false;
    }
    Set<FineGranularMetaModelRelationRequest> desired = getFineGranularMetaModelRelationSet();
    Set<?> existing = metaModelRelation.getFineGranularMetaModelRelationSet();
    int existingSize = existing == null ? 0 : existing.size();
    if (desired.size() != existingSize) {
      return false;
    }
    if (existingSize == 0) {
      return true;
    }
    for (FineGranularMetaModelRelationRequest fgmmr : desired) {
      if (metaModelRelation.getFineGranularMetaModelRelationSet().stream()
          .noneMatch(rel -> fgmmr.equals(lowCodeReactionRequestMapper, rel))) {
        return false;
      }
    }
    return true;
  }
}
