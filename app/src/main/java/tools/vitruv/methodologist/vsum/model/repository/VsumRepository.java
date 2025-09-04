package tools.vitruv.methodologist.vsum.model.repository;

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
  Optional<Vsum> findByIdAndUser_emailAndRemovedAtIsNull(Long id, String email);

  /**
   * Retrieves all {@link Vsum} entities that belong to the user with the given email, provided the
   * user has not been marked as removed.
   *
   * <p>This query leverages Spring Data JPA's property path parsing to traverse the {@code user}
   * association and check both the {@code email} and {@code removedAt} fields.
   *
   * @param callerEmail the email address of the user who owns the {@link Vsum}
   * @return a list of {@link Vsum} entities associated with the given user and not removed
   */
  List<Vsum> findAllByUser_emailAndUser_removedAtIsNull(String callerEmail);
}
