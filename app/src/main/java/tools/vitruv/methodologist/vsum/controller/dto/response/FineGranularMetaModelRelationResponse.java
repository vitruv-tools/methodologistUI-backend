package tools.vitruv.methodologist.vsum.controller.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tools.vitruv.methodologist.vsum.lowcode.reactions.template.dto.request.LowCodeReactionRequestBase;

/**
 * Response DTO for fine-granular meta-model relations.
 */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FineGranularMetaModelRelationResponse {
  private Long id;
  private String sourceId;
  private String targetId;
  private Long reactionFileStorageId;
  private LowCodeReactionRequestBase lowCodeReactionRequestBase;
}
