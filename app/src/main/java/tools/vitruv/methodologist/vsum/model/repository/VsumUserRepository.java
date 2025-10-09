package tools.vitruv.methodologist.vsum.model.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
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
   * Finds all active VSUM user relationships for the specified user email, with pagination.
   *
   * <p>Criteria: - {@code user.email} equals {@code userEmail} - {@code vsum.removedAt} is {@code
   * null}
   *
   * @param vsumId the ID of the VSUM to retrieve user relationships for
   * @return a list of VSUM user relationships associated with the given VSUM ID
   */
  @SuppressWarnings("checkstyle:MethodName")
  List<VsumUser> findAllByVsum_id(Long vsumId);

  /**
   * Finds all VSUM relationships for a specific user.
   *
   * @param userEmail the email of the user to filter by
   * @param pageable pagination information
   * @return paginated list of active {@link VsumUser} relationships
   */
  @SuppressWarnings("checkstyle:MethodName")
  List<VsumUser> findAllByUser_EmailAndVsum_removedAtIsNull(String userEmail, Pageable pageable);

  /**
   * Finds all active VSUM user relationships for the specified user email and VSUM name substring,
   * with pagination.
   *
   * <p>Criteria: - {@code user.email} equals {@code userEmail} - {@code vsum.name} contains {@code
   * name} (case-insensitive) - {@code vsum.removedAt} is {@code null}
   *
   * @param userEmail the email of the user to filter by
   * @param name the substring to search for in VSUM names (case-insensitive)
   * @param pageable pagination information
   * @return paginated list of matching active {@link VsumUser} relationships
   */
  @SuppressWarnings("checkstyle:MethodName")
  List<VsumUser> findAllByUser_EmailAndVsum_NameContainingIgnoreCaseAndVsum_RemovedAtIsNull(
      String userEmail, String name, Pageable pageable);

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
   * Finds an active {@link VsumUser} relationship for the given VSUM and user email, ensuring the
   * user has not been marked as removed.
   *
   * @param vsum the VSUM entity to match
   * @param userEmail the email of the user to match
   * @return an {@link Optional} containing the VSUM user relationship if found and active; empty
   *     otherwise
   */
  @SuppressWarnings("checkstyle:MethodName")
  Optional<VsumUser> findByVsumAndUser_EmailAndUser_RemovedAtIsNull(Vsum vsum, String userEmail);

  /**
   * Finds the active {@link VsumUser} by VSUM id and user email.
   *
   * @param vsumId the VSUM id to match
   * @param callerEmail the user's email to match
   * @return an {@link java.util.Optional} containing the relation if found; empty otherwise
   */
  @SuppressWarnings("checkstyle:MethodName")
  Optional<VsumUser> findByVsum_idAndUser_emailAndUser_removedAtIsNullAndVsum_RemovedAtIsNull(
      Long vsumId, String callerEmail);

  /**
   * Checks if an active {@link VsumUser} relationship exists for the given VSUM and user, ensuring
   * both the VSUM and user have not been marked as removed.
   *
   * @param vsum the VSUM to check
   * @param candidate the user to check
   * @return true if an active relationship exists; false otherwise
   */
  @SuppressWarnings("checkstyle:MethodName")
  boolean existsByVsumAndVsum_removedAtIsNullAndUserAndUser_RemovedAtIsNull(
      Vsum vsum, User candidate);
}
