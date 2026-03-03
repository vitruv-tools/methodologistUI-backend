package tools.vitruv.methodologist.vsum.lowcode.reactions.template.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.validator.constraints.Length;
import org.springframework.stereotype.Component;
import tools.vitruv.methodologist.annotation.ReactionMetadata;

import java.util.HashMap;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Data
@Component
public class ExampleRequest extends LowCodeReactionRequestBase {

    @Schema(
            description = "Template discriminator",
            allowableValues = "example_request",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank
    @ReactionMetadata(hide = true)
    private String name = "example_request";

    @Length(min = 1, max = 10) @NotNull
    private String stringField;
    @NotNull private Boolean booleanField;
    @Min(-10)
    @Max(300)
    @NotNull private Integer integerField;
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    @NotNull private Double doubleField;
    @Size(min = 1, max = 10)
    @NotNull private String[] stringArrayField;
    @NotNull private Map<String, Boolean> mapField;

    @Override
    public Map<String, Object> toTemplateData() {
        throw new UnsupportedOperationException();
    }
}