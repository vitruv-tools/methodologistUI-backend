package tools.vitruv.methodologist.vsum.controller.dto.response;

/**
 * Whether a name can be used for a new or renamed helper.
 *
 * @param available {@code true} if the name is valid and not used by another helper
 * @param message why the name cannot be used, or {@code null}
 */
public record HelperNameAvailabilityResponse(boolean available, String message) {}
