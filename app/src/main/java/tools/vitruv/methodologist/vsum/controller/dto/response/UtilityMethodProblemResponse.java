package tools.vitruv.methodologist.vsum.controller.dto.response;

/**
 * A problem found while compiling a utility method to Java.
 *
 * @param utilityMethodName the utility method the problem belongs to
 * @param message what is wrong
 * @param line 1-based line relative to the method body, or {@code null} if the problem is not in
 *     the body
 * @param column 1-based column relative to the method body, or {@code null}
 * @param severity {@code ERROR}, which keeps the utility method from being saved, or {@code
 *     WARNING}
 */
public record UtilityMethodProblemResponse(
    String utilityMethodName, String message, Integer line, Integer column, String severity) {}
