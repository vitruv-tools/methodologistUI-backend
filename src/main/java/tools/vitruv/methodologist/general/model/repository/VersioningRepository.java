package tools.vitruv.methodologist.general.model.repository;

import java.util.Optional;
import org.springframework.data.repository.CrudRepository;
import tools.vitruv.methodologist.general.model.Versioning;

/**
 * Repository interface for managing Versioning entities. Extends CrudRepository to provide basic
 * CRUD operations for version information.
 *
 * @see Versioning
 * @see CrudRepository
 */
public interface VersioningRepository extends CrudRepository<Versioning, Long> {
  /**
   * Finds the most recent version entry for a given application name. Orders results by ID in
   * descending order and returns the first match.
   *
   * @param name the name of the application to find version for
   * @return Optional containing the most recent Versioning entity if found
   */
  Optional<Versioning> findTopByAppNameOrderByIdDesc(String name);
}
