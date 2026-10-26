package tools.vitruv.methodologist.vsum.controller.dto.response;

import java.util.List;

/**
 * The result of translating an unsaved helper.
 *
 * @param oclDefinition the complete helper definition in OCL
 * @param javaMethod the generated Java method, or {@code null} if there are problems
 * @param problems the problems found, including an already used name
 */
public record HelperPreviewResponse(
    String oclDefinition, String javaMethod, List<HelperProblemResponse> problems) {}
