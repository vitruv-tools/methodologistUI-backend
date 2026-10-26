package tools.vitruv.methodologist.vsum.controller.dto.response;

import java.time.Instant;
import java.util.List;
import tools.vitruv.methodologist.vsum.controller.dto.request.HelperParameterDto;

/**
 * Response DTO representing a helper.
 *
 * @param id the helper ID
 * @param vsumId the VSUM the helper belongs to
 * @param name the helper name
 * @param description optional documentation
 * @param returnType the OCL return type
 * @param parameters the parameters in declaration order
 * @param imports the imported metamodel types
 * @param body the OCL expression
 * @param oclDefinition the complete helper definition in OCL
 * @param javaMethod the generated Java method, or {@code null} if the helper has problems
 * @param problems the problems that keep the helper from being translated
 * @param createdAt creation time
 * @param updatedAt last modification time
 */
public record HelperResponse(
    Long id,
    Long vsumId,
    String name,
    String description,
    String returnType,
    List<HelperParameterDto> parameters,
    List<String> imports,
    String body,
    String oclDefinition,
    String javaMethod,
    List<HelperProblemResponse> problems,
    Instant createdAt,
    Instant updatedAt) {}
