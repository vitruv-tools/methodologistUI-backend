package tools.vitruv.methodologist.vsum.controller.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Request DTO for creating, updating or previewing a utility method.
 *
 * @param name the utility method name, which becomes the Java method name
 * @param description optional documentation
 * @param returnType the Xtend/Java return type, e.g. {@code List<SubSystem>}
 * @param parameters the parameters in declaration order
 * @param imports Java imports without keyword and semicolon, e.g. {@code
 *     tools.vitruv.methodologisttemplate.model.simulink.*}
 * @param body the Xtend method body without the surrounding braces
 */
public record UtilityMethodRequest(
    @NotBlank @Size(max = 255) String name,
    String description,
    @NotBlank @Size(max = 1024) String returnType,
    @Valid List<UtilityMethodParameterDto> parameters,
    @Size(max = 50) List<@Size(max = 255) String> imports,
    @NotBlank @Size(max = 20000) String body) {}
