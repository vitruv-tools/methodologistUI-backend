package tools.vitruv.methodologist.vsum.model.repository;

import java.util.Optional;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tools.vitruv.methodologist.vsum.model.Vsum;
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
  /**
   * Counts history records for the specified VSUM.
   *
   * @param vsum the VSUM whose history snapshots to count
   * @return number of {@link VsumHistory} records for the VSUM
   */
  long countByVsum(Vsum vsum);

  /**
   * Finds the latest history record for the specified VSUM.
   *
   * <p>Returns the most recently created {@link VsumHistory} for the given VSUM, ordered by {@code
   * createdAt} descending.
   *
   * @param vsum the VSUM whose latest history snapshot to retrieve
   * @return an {@link java.util.Optional} containing the most recent {@link VsumHistory}, or empty
   *     if none found
   */
  Optional<VsumHistory> findTopByVsumOrderByCreatedAtDesc(Vsum vsum);
}
