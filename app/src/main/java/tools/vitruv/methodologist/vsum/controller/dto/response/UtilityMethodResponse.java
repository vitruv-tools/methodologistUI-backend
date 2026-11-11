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
 * @param returnType the Xtend/Java return type
 * @param parameters the parameters in declaration order
 * @param imports the Java imports
 * @param body the Xtend method body
 * @param xtendDefinition the utility method as it appears in the generated Xtend class
 * @param javaMethod the generated Java method, or {@code null} if the utility method has problems
 * @param problems the problems found, including warnings; errors keep the utility method out of the
 *     class
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
    String xtendDefinition,
    String javaMethod,
    List<UtilityMethodProblemResponse> problems,
    Instant createdAt,
    Instant updatedAt) {}
