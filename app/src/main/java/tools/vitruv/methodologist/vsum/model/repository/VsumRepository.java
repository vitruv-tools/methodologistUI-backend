package tools.vitruv.methodologist.vsum.model.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tools.vitruv.methodologist.vsum.model.Vsum;

/**
 * Repository interface for managing {@link tools.vitruv.methodologist.vsum.model.Vsum} entities.
 * Provides CRUD operations and custom queries for VSUM data access.
 */
@Repository
public interface VsumRepository extends CrudRepository<Vsum, Long> {
  /**
   * Retrieves a {@link Vsum} by its ID and the owning user's email, ensuring that the entity has
   * not been marked as removed.
   *
   * @param id the ID of the Vsum to retrieve
   * @param email the email of the owning user
   * @return an Optional containing the Vsum if found and not removed, otherwise empty
   */
  @SuppressWarnings("checkstyle:MethodName")
  Optional<Vsum> findByIdAndUser_emailAndRemovedAtIsNull(Long id, String email);

  /**
   * Finds all {@link Vsum} entities that have been marked as removed before the given cutoff
   * timestamp.
   *
   * @param cutoff the {@link Instant} representing the cutoff time; only VSUMs with {@code
   *     removedAt} before this will be returned
   * @return a list of {@link Vsum} entities removed before the specified cutoff
   */
  List<Vsum> findAllByRemovedAtBefore(Instant cutoff);

  /**
   * Retrieves a {@link Vsum} by its ID, ensuring that the entity has not been marked as removed.
   *
   * @param id the ID of the Vsum to retrieve
   * @return an {@link Optional} containing the Vsum if found and not removed, otherwise empty
   */
  Optional<Vsum> findByIdAndRemovedAtIsNull(Long id);

  /**
   * Retrieves a removed {@link Vsum} by id for an active (not-removed) user identified by the given
   * email.
   *
   * @param id the id of the {@link Vsum} to retrieve
   * @param callerEmail the email of the owning user
   * @return an {@link Optional} containing the matching removed {@link Vsum} if present, otherwise
   *     empty
   */
  @SuppressWarnings("checkstyle:MethodName")
  Optional<Vsum> findByIdAndUser_EmailAndUser_RemovedAtIsNullAndRemovedAtIsNotNull(
      Long id, String callerEmail);
}
