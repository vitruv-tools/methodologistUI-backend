package tools.vitruv.methodologist.vsum.controller.dto.response;

import java.util.List;

/**
 * The result of translating an unsaved utility method.
 *
 * @param oclDefinition the complete utility method definition in OCL
 * @param javaMethod the generated Java method, or {@code null} if there are problems
 * @param problems the problems found, including an already used name
 */
public record UtilityMethodPreviewResponse(
    String oclDefinition, String javaMethod, List<UtilityMethodProblemResponse> problems) {}
