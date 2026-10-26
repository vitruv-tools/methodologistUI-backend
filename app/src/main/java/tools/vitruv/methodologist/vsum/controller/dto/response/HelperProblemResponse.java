package tools.vitruv.methodologist.vsum.controller.dto.response;

/**
 * A problem found while translating a helper to Java.
 *
 * @param helperName the helper the problem belongs to
 * @param message what is wrong
 * @param line 1-based line in the OCL definition, or {@code null} if the problem is elsewhere
 * @param column 1-based column in the OCL definition, or {@code null}
 */
public record HelperProblemResponse(
    String helperName, String message, Integer line, Integer column) {}
