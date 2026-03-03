package tools.vitruv.methodologist.vsum.lowcode.reactions.template.dto.response;

import lombok.*;

import java.util.Map;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LowCodeReactionMetadataResponse {
    private Map<String, LowCodeReactionMetadata> reactionMetadataMap;
}
