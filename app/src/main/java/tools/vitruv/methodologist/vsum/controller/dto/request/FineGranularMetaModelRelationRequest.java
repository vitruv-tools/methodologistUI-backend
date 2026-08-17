package tools.vitruv.methodologist.vsum.controller.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tools.vitruv.methodologist.vsum.lowcode.reactions.template.dto.request.LowCodeReactionRequestBase;
import tools.vitruv.methodologist.vsum.mapper.LowCodeReactionRequestMapper;
import tools.vitruv.methodologist.vsum.model.FineGranularMetaModelRelation;

/** Request DTO for an element-level mapping under a coarse meta-model relation. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FineGranularMetaModelRelationRequest {
  private Long id;
  @NotNull @NotBlank private String sourceId;
  @NotNull @NotBlank private String targetId;
  private Long reactionFileStorageId;
  @Valid private LowCodeReactionRequestBase lowCodeReactionRequestBase;

  /**
   * Compares this request with a persisted fine-granular relation. A {@code regenerate=true}
   * template request never matches, so the existing file is regenerated.
   *
   * @param lowCodeReactionRequestMapper mapper used to rebuild stored template params
   * @param fineGranularMetaModelRelation the entity to compare with
   * @return true if the request already matches the entity
   */
  public boolean equals(
      LowCodeReactionRequestMapper lowCodeReactionRequestMapper,
      FineGranularMetaModelRelation fineGranularMetaModelRelation) {
    if (id != null && !Objects.equals(id, fineGranularMetaModelRelation.getId())) {
      return false;
    }
    if (!Objects.equals(sourceId, fineGranularMetaModelRelation.getSourceId())) {
      return false;
    }
    if (!Objects.equals(targetId, fineGranularMetaModelRelation.getTargetId())) {
      return false;
    }
    Long existingFileId =
        fineGranularMetaModelRelation.getReactionFileStorage() == null
            ? null
            : fineGranularMetaModelRelation.getReactionFileStorage().getId();
    if (!Objects.equals(reactionFileStorageId, existingFileId)) {
      return false;
    }
    if (lowCodeReactionRequestBase == null) {
      return true;
    }
    if (lowCodeReactionRequestBase.isRegenerate()) {
      return false;
    }
    if (!Objects.equals(
        lowCodeReactionRequestBase.getName(),
        fineGranularMetaModelRelation.getLowCodeReactionTemplate())) {
      return false;
    }
    LowCodeReactionRequestBase storedRequest =
        lowCodeReactionRequestMapper.map(
            lowCodeReactionRequestBase.getName(),
            fineGranularMetaModelRelation.getLowCodeReactionTemplateParams());
    if (storedRequest == null) {
      return false;
    }
    ObjectMapper mapper = new ObjectMapper();
    JsonNode node1 = mapper.valueToTree(lowCodeReactionRequestBase.toTemplateData());
    JsonNode node2 = mapper.valueToTree(storedRequest.toTemplateData());
    return node1.equals(node2);
  }
}
