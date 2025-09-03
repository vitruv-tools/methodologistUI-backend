package tools.vitruv.methodologist.vsum.model.repository;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
   * Finds an active VSUM by its ID. A VSUM is considered active when its removedAt field is null.
   *
   * @param id the ID of the VSUM to find
   * @return an Optional containing the found VSUM, or empty if not found
   */
  Optional<Vsum> findByIdAndRemovedAtIsNull(Long id);

  /**
   * Finds a VSUM by its name, ignoring case sensitivity.
   *
   * @param name the name of the VSUM to find (must not be null or blank)
   * @return an Optional containing the found VSUM, or empty if not found
   */
  Optional<Vsum> findByNameIgnoreCase(@NotNull @NotBlank String name);

  /**
   * Retrieves a {@link Vsum} by its ID and the owning user's email, ensuring that the entity has
   * not been marked as removed.
   *
   * @param id the ID of the Vsum to retrieve
   * @param email the email of the owning user
   * @return an Optional containing the Vsum if found and not removed, otherwise empty
   */
  Optional<Vsum> findByIdAndUser_emailAndRemovedAtIsNull(Long id, String email);
}
