package tools.vitruv.methodologist.vsum.model.repository;

import java.util.List;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tools.vitruv.methodologist.vsum.VsumInvitationStatus;
import tools.vitruv.methodologist.vsum.model.Vsum;
import tools.vitruv.methodologist.vsum.model.VsumInvitation;

/**
 * Repository for {@link VsumInvitation} entities. Provides lookups used when inviting users and
 * when applying pending invitations during registration.
 */
@Repository
public interface VsumInvitationRepository extends CrudRepository<VsumInvitation, Long> {

  /**
   * Checks whether an invitation in the given status already exists for the VSUM and email
   * (case-insensitive).
   *
   * @param vsum the VSUM to check
   * @param inviteeEmail the invited email address (matched case-insensitively)
   * @param status the invitation status to match
   * @return {@code true} if such an invitation exists
   */
  boolean existsByVsumAndInviteeEmailIgnoreCaseAndStatus(
      Vsum vsum, String inviteeEmail, VsumInvitationStatus status);

  /**
   * Finds all invitations for the given email (case-insensitive) in the given status.
   *
   * @param inviteeEmail the invited email address (matched case-insensitively)
   * @param status the invitation status to match
   * @return the matching invitations
   */
  List<VsumInvitation> findAllByInviteeEmailIgnoreCaseAndStatus(
      String inviteeEmail, VsumInvitationStatus status);
}
