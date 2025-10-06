package tools.vitruv.methodologist.vsum.model.repository;

import java.util.List;
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
   * Finds all users with a specific role in a VSUM.
   *
   * @param vsum the VSUM to search in
   * @param role the role to filter by
   * @return list of VSUM user relationships matching the criteria
   */
  List<VsumUser> findAllByVsumAndRole(Vsum vsum, VsumRole role);

  /**
   * Checks if a user relationship exists with the specified VSUM, user, and role.
   *
   * @param vsum the VSUM to check
   * @param user the user to check
   * @param role the role to check
   * @return true if a relationship exists with the given criteria, false otherwise
   */
  boolean existsByVsumAndUserAndRole(Vsum vsum, User user, VsumRole role);
}
