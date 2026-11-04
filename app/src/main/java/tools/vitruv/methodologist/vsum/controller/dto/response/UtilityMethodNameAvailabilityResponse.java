package tools.vitruv.methodologist.vsum.controller.dto.response;

/**
 * Whether a name can be used for a new or renamed utility method.
 *
 * @param available {@code true} if the name is valid and not used by another utility method
 * @param message why the name cannot be used, or {@code null}
 */
public record UtilityMethodNameAvailabilityResponse(boolean available, String message) {}
