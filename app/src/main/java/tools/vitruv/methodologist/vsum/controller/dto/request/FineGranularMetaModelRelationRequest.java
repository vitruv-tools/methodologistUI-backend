package tools.vitruv.methodologist.vsum.controller.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

/** Request DTO for fine-granular meta-model relations. */
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
  private LowCodeReactionRequestBase lowCodeReactionRequestBase;

  /**
   * Compares this request with a FineGranularMetaModelRelation entity.
   *
   * @param lowCodeReactionRequestMapper the mapper for low-code reaction requests
   * @param fineGranularMetaModelRelation the entity to compare with
   * @return true if the request matches the entity, false otherwise
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
    if (!Objects.equals(
        reactionFileStorageId, fineGranularMetaModelRelation.getReactionFileStorage().getId())) {
      return false;
    }
    if (lowCodeReactionRequestBase != null) {
      if (!Objects.equals(
          lowCodeReactionRequestBase.getName(),
          fineGranularMetaModelRelation.getLowCodeReactionTemplate())) {
        return false;
      }
      ObjectMapper mapper = new ObjectMapper();
      JsonNode node1 = mapper.valueToTree(lowCodeReactionRequestBase.toTemplateData());
      JsonNode node2 =
          mapper.valueToTree(
              lowCodeReactionRequestMapper
                  .map(
                      lowCodeReactionRequestBase.getName(),
                      fineGranularMetaModelRelation.getLowCodeReactionTemplateParams())
                  .toTemplateData());
      return node1.equals(node2);
    }
    return true;
  }
}
