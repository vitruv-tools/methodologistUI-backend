package tools.vitruv.methodologist.apihandler.dto.response;

import java.util.List;
import tools.vitruv.methodologist.exception.BuildProblem;

/**
 * Result of the setup-service {@code /api/vsum/validate} endpoint.
 *
 * @param valid whether the VSUM is valid
 * @param problems the problems found, each traced back to the uploaded platform file if possible
 */
public record VsumValidationResult(boolean valid, List<BuildProblem> problems) {}
