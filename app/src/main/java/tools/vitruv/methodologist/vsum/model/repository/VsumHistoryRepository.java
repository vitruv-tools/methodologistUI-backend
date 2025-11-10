package tools.vitruv.methodologist.vsum.model.repository;

import java.util.List;
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

  /**
   * Deletes all {@link VsumHistory} entities associated with the specified {@link Vsum}.
   *
   * @param vsum the VSUM whose history records should be deleted
   */
  void deleteVsumHistoryByVsum(Vsum vsum);

  /**
   * Finds all non-removed history records for the specified VSUM id that belong to the given user
   * email, ordered by {@code createdAt} ascending.
   *
   * <p>Only returns {@link VsumHistory} entries when both the VSUM and its owning user are not
   * marked as removed ({@code removedAt} is {@code null}). The returned list will be empty if no
   * matching records exist.
   *
   * @param vsumId the VSUM id to filter history records by
   * @param callerEmail the email address of the VSUM owner
   * @return a list of {@link VsumHistory} matching the VSUM id and user email, only for non-removed
   *     VSUMs and users, ordered by {@code createdAt} ascending; never {@code null}
   */
  @SuppressWarnings("checkstyle:MethodName")
  List<VsumHistory>
      findAllByVsum_IdAndVsum_User_EmailAndVsum_User_RemovedAtIsNullAndVsum_RemovedAtIsNullOrderByCreatedAtDesc(
          Long vsumId, String callerEmail);
}
