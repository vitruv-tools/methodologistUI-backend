package tools.vitruv.methodologist.vsum.model.repository;

import java.util.List;
import java.util.Set;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tools.vitruv.methodologist.vsum.model.MetaModel;
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

  /**
   * Retrieves all VsumMetaModel entries where the given MetaModel is used as a source.
   *
   * @param metaModel the MetaModel to search for as a source
   * @return a list of VsumMetaModel entities where the specified MetaModel is used as a source,
   *     returns an empty list if no matches are found
   */
  @SuppressWarnings("checkstyle:MethodName")
  List<VsumMetaModel> findAllByMetaModel_Source(MetaModel metaModel);

  /**
   * Retrieves all {@link VsumMetaModel} entities associated with the given {@link Vsum} whose
   * {@code metaModel.source.id} is contained in the provided list of IDs.
   *
   * <p>Returns an empty list if no entities match. Order is unspecified. Duplicate IDs are ignored.
   *
   * @param vsum the VSUM aggregate to filter by; must not be {@code null}
   * @param ids the list of nested {@code metaModel.source.id} values to match; must not be {@code
   *     null} (may be empty)
   * @return a list of matching {@link VsumMetaModel} entities
   */
  @SuppressWarnings("checkstyle:MethodName")
  List<VsumMetaModel> findAllByVsumAndMetaModel_source_idIn(Vsum vsum, Set<Long> ids);

  /**
   * Deletes all {@link VsumMetaModel} associations linked to the specified {@link Vsum}.
   *
   * @param vsum the VSUM whose metamodel associations should be deleted
   */
  void deleteVsumMetaModelByVsum(Vsum vsum);

  /**
     * Finds all VsumMetaModel associations for a given VSUM ID.
     * 
     * @param vsumid the ID of the VSUM
     * @return list of VsumMetaModel associations for that VSUM
     */
    List<VsumMetaModel> findByVsum_Id(Long vsumid);
}
