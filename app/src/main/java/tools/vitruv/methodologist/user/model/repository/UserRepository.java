package tools.vitruv.methodologist.user.model.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
