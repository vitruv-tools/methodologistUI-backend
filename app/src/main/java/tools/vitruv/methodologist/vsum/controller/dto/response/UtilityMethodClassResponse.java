package tools.vitruv.methodologist.vsum.controller.dto.response;

import java.util.List;

/**
 * The generated Java utility class of a VSUM.
 *
 * @param qualifiedClassName the fully qualified class name reactions import
 * @param fileName the file name of the class
 * @param source the Java source; utility methods with problems are left out
 * @param problems the problems of utility methods that could not be translated
 */
public record UtilityMethodClassResponse(
    String qualifiedClassName,
    String fileName,
    String source,
    List<UtilityMethodProblemResponse> problems) {}
