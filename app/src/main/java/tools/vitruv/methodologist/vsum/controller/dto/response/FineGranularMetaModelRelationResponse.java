package tools.vitruv.methodologist.vsum.controller.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tools.vitruv.methodologist.vsum.lowcode.reactions.template.dto.request.LowCodeReactionRequestBase;

/** Response DTO for an element-level mapping under a coarse meta-model relation. */
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
