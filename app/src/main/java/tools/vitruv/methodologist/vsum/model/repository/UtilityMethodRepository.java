package tools.vitruv.methodologist.vsum.model.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import tools.vitruv.methodologist.vsum.model.UtilityMethod;

/** JPA repository for {@link Utility method} entities. */
public interface UtilityMethodRepository extends JpaRepository<UtilityMethod, Long> {

  /**
   * Returns all utility methods of the given VSUM with their parameters, ordered by name.
   *
   * @param vsumId the VSUM ID
   * @return the utility methods
   */
  @EntityGraph(attributePaths = "parameters")
  List<UtilityMethod> findByVsumIdOrderByNameAsc(Long vsumId);

  /**
   * Returns the utility method with the given ID belonging to the given VSUM, if present.
   *
   * @param id the utility method ID
   * @param vsumId the VSUM ID
   * @return the matching utility method, or empty
   */
  Optional<UtilityMethod> findByIdAndVsumId(Long id, Long vsumId);
}
