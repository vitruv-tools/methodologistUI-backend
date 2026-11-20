package tools.vitruv.methodologist.general.event;

/**
 * Published when a user changes a VSUM through a user endpoint, e.g. by syncing changes, editing a
 * metamodel or reverting to a history entry. Changes written back by the collaboration server
 * through the internal endpoints do not publish it.
 *
 * @param vsumId the id of the changed VSUM
 */
public record VsumChangedByUserEvent(Long vsumId) {}
