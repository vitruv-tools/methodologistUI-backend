package tools.vitruv.methodologist.vsum.model.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import tools.vitruv.methodologist.vsum.model.ConstraintRuleSet;

public interface ConstraintRuleSetRepository extends JpaRepository<ConstraintRuleSet, Long> {

  List<ConstraintRuleSet> findByVsumId(Long vsumId);

  Optional<ConstraintRuleSet> findByIdAndVsumId(Long id, Long vsumId);

  boolean existsByIdAndVsumId(Long id, Long vsumId);
}
