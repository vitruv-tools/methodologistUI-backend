package tools.vitruv.methodologist.vsum.lowcode.reactions.template.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.stereotype.Component;
import tools.vitruv.methodologist.annotation.ReactionMetadata;

import java.util.HashMap;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Data
@Component
@ReactionMetadata(name = "Create Corresponding Root", description = "Create a corresponding root when a new root is inserted")
public class CreateCorrespondingRootOnInsertRootRequest extends LowCodeReactionRequestBase {

    @Schema(
            description = "Template discriminator",
            allowableValues = "create_corresponding_root_on_insert_root",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank
    @ReactionMetadata(hide = true)
    private String name = "create_corresponding_root_on_insert_root";

    @ReactionMetadata(defaultStringValue = "${sourceUri}")
    @NotNull private String model1Uri;
    @ReactionMetadata(defaultStringValue = "${targetUri}")
    @NotNull private String model2Uri;
    @ReactionMetadata(defaultStringValue = "${sourceAlias}")
    @NotNull private String model1Alias;
    @ReactionMetadata(defaultStringValue = "${targetAlias}")
    @NotNull private String model2Alias;
    @ReactionMetadata(defaultStringValue = "${sourceAlias}2${targetAlias}")
    @NotNull private String reactionName;
    @NotNull private String model1RootType;
    @NotNull private String model2RootType;
    @NotNull private String model1RootVar;

    @Override
    public Map<String, Object> toTemplateData() {
        Map<String, Object> data = new HashMap<>();
        data.put("model1Uri", model1Uri);
        data.put("model2Uri", model2Uri);
        data.put("model1Alias", model1Alias);
        data.put("model2Alias", model2Alias);
        data.put("reactionName", reactionName);
        data.put("model1RootType", model1RootType);
        data.put("model2RootType", model2RootType);
        data.put("model1RootVar", model1RootVar);
        return data;
    }
}