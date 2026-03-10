package tools.vitruv.methodologist.vsum.lowcode.reactions.template.dto.request;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import tools.vitruv.methodologist.annotation.ReactionMetadata;

import java.util.Map;

@Schema(
        description = "Low-code reaction request. The 'name' field selects the request shape.",
        discriminatorProperty = "name",
        subTypes = {
                CreateCorrespondingRootOnInsertRootRequest.class,
                CompositeReactionsRequest.class,
                ExampleRequest.class
        }
)
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "name",
        visible = true
)
@JsonSubTypes({
        @JsonSubTypes.Type(
                value = CreateCorrespondingRootOnInsertRootRequest.class,
                name = "create_corresponding_root_on_insert_root"
        ),
        @JsonSubTypes.Type(
                value = CompositeReactionsRequest.class,
                name = "composite_reactions"
        )
})
@Data
public abstract class LowCodeReactionRequestBase {
    public abstract String getName();

    @ReactionMetadata(hide = true)
    private boolean regenerate = false;

    private boolean bidirectional;

    /**
     * Convert the typed request into the map your FreeMarker template expects.
     */
    public java.util.Map<String, Object> toTemplateData() {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.convertValue(this, Map.class);
    }
}