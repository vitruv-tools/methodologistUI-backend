package tools.vitruv.methodologist.vsum.controller.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A parameter of a utility method.
 *
 * @param name the parameter name
 * @param type the Xtend/Java type, e.g. {@code SimulinkModel} or {@code List<SubSystem>}
 */
public record UtilityMethodParameterDto(
    @NotBlank @Size(max = 255) String name, @NotBlank @Size(max = 1024) String type) {}
