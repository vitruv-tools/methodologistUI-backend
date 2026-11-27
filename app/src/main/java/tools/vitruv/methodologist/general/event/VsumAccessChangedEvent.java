package tools.vitruv.methodologist.general.event;

/**
 * Published when a change may have taken a user's access, or write access, to a VSUM away, e.g.
 * when the owner removes a member or viewer. Every future path that does so, such as leaving a
 * VSUM, changing a member's role or transferring the ownership, must publish it too, so the
 * collaboration server checks the open connections of the VSUM again. Granting access publishes
 * nothing.
 *
 * @param vsumId the id of the VSUM whose members changed
 */
public record VsumAccessChangedEvent(Long vsumId) {}
