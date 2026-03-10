package tools.vitruv.methodologist.vsum.controller.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.*;
import tools.vitruv.methodologist.vsum.lowcode.reactions.template.dto.request.LowCodeReactionRequestBase;
import tools.vitruv.methodologist.vsum.model.FineGranularMetaModelRelation;

import java.util.Map;
import java.util.Objects;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FineGranularMetaModelRelationRequest {
    private String sourceId;
    private String targetId;
    private Long reactionFileStorageId;
    private LowCodeReactionRequestBase lowCodeReactionRequestBase;

    public boolean equals(FineGranularMetaModelRelation fineGranularMetaModelRelation) {
        if (!Objects.equals(sourceId, fineGranularMetaModelRelation.getSourceId())) {
            return false;
        }
        if (!Objects.equals(targetId, fineGranularMetaModelRelation.getTargetId())) {
            return false;
        }
        if (!Objects.equals(reactionFileStorageId, fineGranularMetaModelRelation.getReactionFileStorage().getId())) {
            return false;
        }
        if (lowCodeReactionRequestBase != null) {
            if (!Objects.equals(lowCodeReactionRequestBase.getName(), fineGranularMetaModelRelation.getLowCodeReactionTemplate())) {
                return false;
            }
            ObjectMapper mapper = new ObjectMapper();
            JsonNode node1 = mapper.valueToTree(lowCodeReactionRequestBase.toTemplateData());
            JsonNode node2 = mapper.valueToTree(fineGranularMetaModelRelation.getLowCodeReactionTemplateParams());
            return node1.equals(node2);
        }
        return true;
    }
}
