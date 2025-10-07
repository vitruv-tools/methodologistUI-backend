package tools.vitruv.methodologist.vsum.model.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tools.vitruv.methodologist.user.model.User;
import tools.vitruv.methodologist.vsum.VsumRole;
import tools.vitruv.methodologist.vsum.model.Vsum;
import tools.vitruv.methodologist.vsum.model.VsumUser;

/**
 * Repository interface for managing {@link VsumUser} entities. Provides methods to find VSUM user
 * relationships by various criteria.
 */
@Repository
public interface VsumUserRepository extends CrudRepository<VsumUser, Long> {
  /**
   * Finds all user relationships for a specific VSUM.
   *
   * @param vsum the VSUM to find users for
   * @return list of VSUM user relationships
   */
  List<VsumUser> findAllByVsum(Vsum vsum);

  /**
   * Finds all VSUM relationships for a specific user.
   *
   * @param userEmail the user to find VSUMs for
   * @return list of VSUM user relationships
   */
  @SuppressWarnings("checkstyle:MethodName")
  List<VsumUser> findAllByUser_EmailAndVsum_removedAtIsNull(String userEmail);

  /**
   * Checks if a user relationship exists with the specified VSUM, user, and role.
   *
   * @param vsum the VSUM to check
   * @param user the user to check
   * @param role the role to check
   * @return true if a relationship exists with the given criteria, false otherwise
   */
  boolean existsByVsumAndUserAndRole(Vsum vsum, User user, VsumRole role);

  /**
   * Finds the active {@link VsumUser} by VSUM id and user email.
   *
   * @param id the VSUM id to match
   * @param callerEmail the user's email to match
   * @return an {@link java.util.Optional} containing the relation if found; empty otherwise
   */
  @SuppressWarnings("checkstyle:MethodName")
  Optional<VsumUser> findByVsum_idAndUser_emailAndVsum_RemovedAtIsNull(Long id, String callerEmail);

  /**
   * Deletes all {@link VsumUser} relationships associated with the specified VSUM.
   *
   * @param vsum the VSUM whose user relationships should be deleted
   */
  void deleteVsumUserByVsum(Vsum vsum);
}
