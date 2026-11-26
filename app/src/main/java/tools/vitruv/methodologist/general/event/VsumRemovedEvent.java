package tools.vitruv.methodologist.general.event;

/**
 * Published when the owner moves a VSUM to the trash or deletes it permanently from the trash. The
 * scheduled cleanup of VSUMs that have been in the trash for 30 days does not publish it.
 *
 * @param vsumId the id of the removed VSUM
 */
public record VsumRemovedEvent(Long vsumId) {}
