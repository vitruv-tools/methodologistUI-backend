package tools.vitruv.methodologist.vsum.controller.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Request DTO for creating, updating or previewing a helper.
 *
 * @param name the helper name, which becomes the Java method name
 * @param description optional documentation
 * @param returnType the OCL return type, e.g. {@code Sequence(simulink::SubSystem)}
 * @param parameters the parameters in declaration order
 * @param imports metamodel types usable by their simple name, e.g. {@code simulink::SubSystem}
 * @param body the OCL expression
 */
public record HelperRequest(
    @NotBlank @Size(max = 255) String name,
    String description,
    @NotBlank @Size(max = 1024) String returnType,
    @Valid List<HelperParameterDto> parameters,
    List<String> imports,
    @NotBlank String body) {}
