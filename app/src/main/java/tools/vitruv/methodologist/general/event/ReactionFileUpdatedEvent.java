package tools.vitruv.methodologist.general.event;

/**
 * Published when the content of a reaction file has been updated.
 *
 * @param fileId the id of the updated reaction file
 */
public record ReactionFileUpdatedEvent(Long fileId) {}
