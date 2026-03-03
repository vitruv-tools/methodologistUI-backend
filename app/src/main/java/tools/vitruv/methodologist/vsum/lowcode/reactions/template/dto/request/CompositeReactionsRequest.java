package tools.vitruv.methodologist.vsum.lowcode.reactions.template.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.stereotype.Component;
import tools.vitruv.methodologist.annotation.ReactionMetadata;

import java.util.HashMap;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Data
@Component
@ReactionMetadata(hide = true)
public class CompositeReactionsRequest extends LowCodeReactionRequestBase {

    @Schema(
            description = "Template discriminator",
            allowableValues = "composite_reactions",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank
    @ReactionMetadata(hide = true)
    private String name = "composite_reactions";

    @NotNull private String model1Uri;
    @NotNull private String model2Uri;
    @NotNull private String model1Alias;
    @NotNull private String model2Alias;
    @NotNull private String reactionName;

    @NotNull
    @Size(min = 1, message = "Must be at least one import")
    private String[] imports;

    @Override
    public Map<String, Object> toTemplateData() {
        Map<String, Object> data = new HashMap<>();
        data.put("model1Uri", model1Uri);
        data.put("model2Uri", model2Uri);
        data.put("model1Alias", model1Alias);
        data.put("model2Alias", model2Alias);
        data.put("reactionName", reactionName);
        data.put("imports", imports);
        return data;
    }
}