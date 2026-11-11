package tools.vitruv.methodologist.vsum.controller.dto.response;

import java.util.List;

/**
 * The result of compiling an unsaved utility method.
 *
 * @param xtendDefinition the utility method as it appears in the generated Xtend class
 * @param javaMethod the generated Java method, or {@code null} if there are problems
 * @param problems the problems found, including an already used name
 */
public record UtilityMethodPreviewResponse(
    String xtendDefinition, String javaMethod, List<UtilityMethodProblemResponse> problems) {}
