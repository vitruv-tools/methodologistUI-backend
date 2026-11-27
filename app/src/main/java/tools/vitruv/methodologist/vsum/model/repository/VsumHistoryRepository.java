package tools.vitruv.methodologist.vsum.model.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
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
   * Finds the oldest history record for the specified VSUM, the one to delete when the VSUM has too
   * many. Of records created at the same time, the one with the lowest id is the oldest.
   *
   * @param vsum the VSUM whose oldest history snapshot to retrieve
   * @return an {@link java.util.Optional} containing the oldest {@link VsumHistory}, or empty if
   *     none found
   */
  Optional<VsumHistory> findTopByVsumOrderByCreatedAtAscIdAsc(Vsum vsum);

  /**
   * Finds the oldest history record for the specified VSUM except the given one, the one to delete
   * when the VSUM has too many while it is reverted to the given one.
   *
   * @param vsum the VSUM whose oldest history snapshot to retrieve
   * @param id the id of the history record to skip
   * @return an {@link java.util.Optional} containing the oldest other {@link VsumHistory}, or empty
   *     if none found
   */
  Optional<VsumHistory> findTopByVsumAndIdNotOrderByCreatedAtAscIdAsc(Vsum vsum, Long id);

  /**
   * Finds when the latest history record of the specified VSUM was created and what it was taken
   * before, without loading its representation. Records without a creation time are skipped.
   *
   * @param vsum the VSUM whose latest history snapshot to look at
   * @return the latest snapshot, or empty if the VSUM has none
   */
  Optional<LatestSnapshot> findTopByVsumAndCreatedAtNotNullOrderByCreatedAtDescIdDesc(Vsum vsum);

  /** The creation time of a history record and what it was taken before. */
  interface LatestSnapshot {
    /**
     * Returns when the snapshot was created.
     *
     * @return the creation time
     */
    Instant getCreatedAt();

    /**
     * Returns whether the snapshot was taken before a writeback of the collaboration server.
     *
     * @return {@code true} for a snapshot taken before a writeback
     */
    boolean isCollaborationWriteback();
  }

  /**
   * Finds a {@link VsumHistory} record by ID with eager loading of related associations.
   *
   * <p>This method uses an entity graph to eagerly load the following associations to avoid N+1
   * query problems:
   *
   * @param id the history record ID to retrieve
   * @return an {@link java.util.Optional} containing the {@link VsumHistory} with all related
   *     associations loaded, or empty if no record found
   */
  @EntityGraph(attributePaths = {"vsum", "vsum.user", "vsum.views", "vsum.views.viewMetaModels"})
  Optional<VsumHistory> findById(Long id);

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
   * <p>Only returns {@link tools.vitruv.methodologist.vsum.model.VsumHistory} entries when both the
   * VSUM and its owning user are not marked as removed ({@code removedAt} is {@code null}). The
   * returned list will be empty if no matching records exist.
   *
   * @param vsumId the VSUM id to filter history records by
   * @param callerEmail the email address of the VSUM owner
   * @return a list of {@link tools.vitruv.methodologist.vsum.model.VsumHistory} matching the VSUM
   *     id and user email, only for non-removed VSUMs and users, ordered by {@code createdAt}
   *     ascending; never {@code null}
   */
  @Query(
      """
          select v from VsumHistory v
          where v.vsum.id = ?1
            and v.vsum.user.email = ?2
            and v.vsum.user.removedAt is null
            and v.vsum.removedAt is null
          order by v.createdAt DESC""")
  List<VsumHistory> getVsumHistories(Long vsumId, String callerEmail);
}
