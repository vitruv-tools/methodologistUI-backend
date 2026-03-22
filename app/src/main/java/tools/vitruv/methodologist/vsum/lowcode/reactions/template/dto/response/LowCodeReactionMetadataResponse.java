package tools.vitruv.methodologist.vsum.lowcode.reactions.template.dto.response;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Response object containing metadata for low-code reactions.
 */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LowCodeReactionMetadataResponse {
  /** Map of reaction names to their metadata. */
  private Map<String, LowCodeReactionMetadata> reactionMetadataMap;
}
