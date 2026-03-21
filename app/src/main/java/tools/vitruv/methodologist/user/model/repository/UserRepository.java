package tools.vitruv.methodologist.user.model.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tools.vitruv.methodologist.user.model.User;

/**
 * Repository interface for accessing and managing {@link
 * tools.vitruv.methodologist.user.model.User} entities. Extends {@link
 * org.springframework.data.jpa.repository.JpaRepository} to provide CRUD operations.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

  /**
   * Finds a user by ID if the user has not been removed.
   *
   * @param id the unique identifier of the user
   * @return an {@link java.util.Optional} containing the user if found and not removed, otherwise
   *     empty
   */
  Optional<User> findByIdAndRemovedAtIsNull(Long id);

  /**
   * Finds a user by email address, ignoring case sensitivity.
   *
   * @param email the email address to search for
   * @return an {@link java.util.Optional} containing the user if found, otherwise empty
   */
  Optional<User> findByEmailIgnoreCase(String email);

  /**
   * Retrieves a non-deleted user by their email address, ignoring case sensitivity. Only returns
   * users where removedAt is null.
   *
   * @param email the email address to search for (case insensitive)
   * @return an Optional containing the user if found and not removed, empty otherwise
   */
  Optional<User> findByEmailIgnoreCaseAndRemovedAtIsNull(String email);

  /**
   * Finds a user by email (case-insensitive) who is not deleted and is not yet verified.
   *
   * <p>Intended for email verification flows: the user must be unverified (verified = false) and
   * not soft-deleted (removedAt is null) to match.
   *
   * @param email the email to search for (case-insensitive)
   * @return an {@link java.util.Optional} containing the user if found and matching the criteria,
   *     otherwise empty
   */
  Optional<User> findByEmailIgnoreCaseAndRemovedAtIsNullAndVerifiedIsFalse(String email);

  /**
   * Retrieves a paginated list of users, excluding the user with the specified email.
   *
   * <p>Results are ordered by first name, last name, and email (all case-insensitive).
   *
   * @param callerEmail the email address to exclude from the results (case-insensitive)
   * @param pageable the pagination and sorting information
   * @return a list of users excluding the specified email, ordered by name and email
   */
  @Query(
      """
      SELECT u FROM User u
      WHERE lower(u.email) <> lower(:callerEmail)
      ORDER BY lower(COALESCE(u.firstName,'')), lower(COALESCE(u.lastName,'')), lower(u.email)
      """)
  List<User> findAllExcludingEmailOrderByName(
      @Param("callerEmail") String callerEmail, Pageable pageable);

  /**
   * Searches for users whose email, first name, or last name contains the specified query
   * parameter, excluding the user with the given email address.
   *
   * <p>All comparisons are case-insensitive. Results are ordered by first name, last name, and
   * email.
   *
   * @param callerEmail the email address to exclude from the results (case-insensitive)
   * @param queryParam the search term to match against email, first name, or last name
   *     (case-insensitive, partial match)
   * @param pageable pagination and sorting information
   * @return a list of users matching the search criteria, excluding the caller
   */
  @Query(
      """
      SELECT u FROM User u
      WHERE lower(u.email) <> lower(:callerEmail)
        AND (
             lower(u.email) LIKE lower(concat('%', :queryParam, '%'))
          OR lower(COALESCE(u.firstName,'')) LIKE lower(concat('%', :queryParam, '%'))
          OR lower(COALESCE(u.lastName,''))  LIKE lower(concat('%', :queryParam, '%'))
        )
      ORDER BY lower(COALESCE(u.firstName,'')), lower(COALESCE(u.lastName,'')), lower(u.email)
      """)
  List<User> searchByNameOrEmailExcludingCaller(
      @Param("callerEmail") String callerEmail,
      @Param("queryParam") String queryParam,
      Pageable pageable);
}
