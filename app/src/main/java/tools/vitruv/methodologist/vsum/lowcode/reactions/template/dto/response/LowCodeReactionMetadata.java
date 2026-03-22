package tools.vitruv.methodologist.vsum.lowcode.reactions.template.dto.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Metadata for a low-code reaction.
 */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LowCodeReactionMetadata {
  /** The name of the reaction. */
  private String name;
  /** The description of the reaction. */
  private String description;
  /** Whether the reaction is hidden. */
  private boolean hide;
  /** The fields of the reaction. */
  private List<LowCodeReactionFieldMetadata> fields;
}
