package tools.vitruv.methodologist.vsum.controller.dto.response;

import lombok.*;
import tools.vitruv.methodologist.vsum.lowcode.reactions.template.dto.request.LowCodeReactionRequestBase;

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
