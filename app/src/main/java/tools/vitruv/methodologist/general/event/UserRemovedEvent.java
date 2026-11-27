package tools.vitruv.methodologist.general.event;

/**
 * Published when a user removes their own account. The user loses access to every VSUM, so the
 * collaboration server checks all of the user's open connections again.
 *
 * @param userId the id of the removed user
 */
public record UserRemovedEvent(Long userId) {}
