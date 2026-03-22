package tools.vitruv.methodologist.vsum.lowcode.reactions.template.dto.response;

import java.util.List;
import lombok.*;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LowCodeReactionMetadata {
  private String name;
  private String description;
  private boolean hide;
  private List<LowCodeReactionFieldMetadata> fields;
}
