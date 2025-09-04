package tools.vitruv.methodologist.vsum.model.repository;

import java.util.List;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tools.vitruv.methodologist.vsum.model.Vsum;
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
   * Retrieves all {@link VsumMetaModel} entries that belong to the specified {@link Vsum}.
   *
   * @param vsum the Vsum entity whose associated VsumMetaModels should be fetched
   * @return list of VsumMetaModel entities linked to the given Vsum
   */
  List<VsumMetaModel> findAllByVsum(Vsum vsum);
}
