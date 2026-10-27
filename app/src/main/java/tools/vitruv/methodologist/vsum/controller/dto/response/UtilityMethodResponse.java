package tools.vitruv.methodologist.vsum.controller.dto.response;

import java.time.Instant;
import java.util.List;
import tools.vitruv.methodologist.vsum.controller.dto.request.UtilityMethodParameterDto;

/**
 * Response DTO representing a utility method.
 *
 * @param id the utility method ID
 * @param vsumId the VSUM the utility method belongs to
 * @param name the utility method name
 * @param description optional documentation
 * @param returnType the OCL return type
 * @param parameters the parameters in declaration order
 * @param imports the imported metamodel types
 * @param body the OCL expression
 * @param oclDefinition the complete utility method definition in OCL
 * @param javaMethod the generated Java method, or {@code null} if the utility method has problems
 * @param problems the problems that keep the utility method from being translated
 * @param createdAt creation time
 * @param updatedAt last modification time
 */
public record UtilityMethodResponse(
    Long id,
    Long vsumId,
    String name,
    String description,
    String returnType,
    List<UtilityMethodParameterDto> parameters,
    List<String> imports,
    String body,
    String oclDefinition,
    String javaMethod,
    List<UtilityMethodProblemResponse> problems,
    Instant createdAt,
    Instant updatedAt) {}
