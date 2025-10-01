package tools.vitruv.methodologist.vsum.model.repository;

import java.util.List;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tools.vitruv.methodologist.vsum.model.VsumHistory;

/**
 * Spring Data repository for managing {@link VsumHistory} entities.
 *
 * <p>Provides basic create, read, update, and delete operations via {@link CrudRepository}. Add
 * derived query methods here as needed.
 *
 * @see VsumHistory
 * @see CrudRepository
 */
@Repository
public interface VsumHistoryRepository extends CrudRepository<VsumHistory, Long> {
  List<VsumHistory> findAllByVsum_user_emailAndVsum_removedAtIsNull(String callerEmail);
}
