package tools.vitruv.methodologist.vsum.model.repository;

import java.util.List;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tools.vitruv.methodologist.vsum.model.VsumMetaModel;

/**
 * Repository interface for accessing and managing {@link VsumMetaModel} entities.
 *
 * <p>Provides methods for querying relationships between {@link
 * tools.vitruv.methodologist.vsum.model.Vsum} and {@link
 * tools.vitruv.methodologist.vsum.model.MetaModel}, including filtering by user ownership and
 * removal state.
 *
 * <p>Methods:
 *
 * <ul>
 *   <li><b>findAllByUser_emailAndRemovedAtIsNull</b> — retrieves all active {@link VsumMetaModel}
 *       associations for a given user, excluding removed entries.
 * </ul>
 */
@Repository
public interface VsumMetaModelRepository extends CrudRepository<VsumMetaModel, Long> {
  /**
   * Retrieves all {@link VsumMetaModel} entities associated with the given user email where the
   * entry has not been marked as removed.
   *
   * @param email the email of the user whose {@link VsumMetaModel} associations should be fetched
   * @return a list of active {@link VsumMetaModel} records linked to the specified user
   */
  List<VsumMetaModel> findAllByUser_emailAndRemovedAtIsNull(String email);
}
