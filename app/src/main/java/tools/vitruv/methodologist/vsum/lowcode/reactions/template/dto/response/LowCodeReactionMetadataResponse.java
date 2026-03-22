package tools.vitruv.methodologist.vsum.lowcode.reactions.template.dto.response;

import java.util.Map;
import lombok.*;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LowCodeReactionMetadataResponse {
  private Map<String, LowCodeReactionMetadata> reactionMetadataMap;
}
