package tools.vitruv.methodologist.vsum;

/**
 * Lifecycle status of a {@link tools.vitruv.methodologist.vsum.model.VsumInvitation}.
 *
 * <p>An invitation starts as {@link #PENDING} when the invited email does not yet belong to a
 * registered user. Once that user registers (or is otherwise resolved) and the membership is
 * created, the invitation becomes {@link #ACCEPTED}.
 */
public enum VsumInvitationStatus {
  PENDING,
  ACCEPTED
}
