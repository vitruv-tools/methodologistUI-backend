package tools.vitruv.methodologist.vsum.controller.dto.response;

/**
 * A problem found while translating a utility method to Java.
 *
 * @param utilityMethodName the utility method the problem belongs to
 * @param message what is wrong
 * @param line 1-based line in the OCL definition, or {@code null} if the problem is elsewhere
 * @param column 1-based column in the OCL definition, or {@code null}
 */
public record UtilityMethodProblemResponse(
    String utilityMethodName, String message, Integer line, Integer column) {}
