package tools.vitruv.methodologist.vsum.model.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import tools.vitruv.methodologist.vsum.model.ConstraintRuleSet;

/** JPA repository for {@link ConstraintRuleSet} entities. */
public interface ConstraintRuleSetRepository extends JpaRepository<ConstraintRuleSet, Long> {

  /**
   * Returns all rule sets belonging to the given VSUM.
   *
   * @param vsumId the VSUM ID
   * @return list of matching rule sets
   */
  List<ConstraintRuleSet> findByVsumId(Long vsumId);

  /**
   * Returns the rule set with the given ID belonging to the given VSUM, if present.
   *
   * @param id the rule set ID
   * @param vsumId the VSUM ID
   * @return the matching rule set, or empty
   */
  Optional<ConstraintRuleSet> findByIdAndVsumId(Long id, Long vsumId);

  /**
   * Returns whether a rule set with the given ID belongs to the given VSUM.
   *
   * @param id the rule set ID
   * @param vsumId the VSUM ID
   * @return {@code true} if such a rule set exists
   */
  boolean existsByIdAndVsumId(Long id, Long vsumId);
}
