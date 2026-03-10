package tools.vitruv.methodologist.vsum.controller.dto.response;

import lombok.*;

import java.util.Map;

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
    private String lowCodeReactionTemplate;
    private Map<String, Object> lowCodeReactionTemplateParams;
}
