package tools.vitruv.methodologist.vsum.model.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import tools.vitruv.methodologist.vsum.model.Helper;

/** JPA repository for {@link Helper} entities. */
public interface HelperRepository extends JpaRepository<Helper, Long> {

  /**
   * Returns all helpers of the given VSUM with their parameters, ordered by name.
   *
   * @param vsumId the VSUM ID
   * @return the helpers
   */
  @EntityGraph(attributePaths = "parameters")
  List<Helper> findByVsumIdOrderByNameAsc(Long vsumId);

  /**
   * Returns the helper with the given ID belonging to the given VSUM, if present.
   *
   * @param id the helper ID
   * @param vsumId the VSUM ID
   * @return the matching helper, or empty
   */
  Optional<Helper> findByIdAndVsumId(Long id, Long vsumId);
}
