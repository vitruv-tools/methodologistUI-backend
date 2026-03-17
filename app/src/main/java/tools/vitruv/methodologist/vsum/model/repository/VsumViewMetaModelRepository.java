package tools.vitruv.methodologist.vsum.model.repository;

import java.util.List;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tools.vitruv.methodologist.vsum.model.VsumView;
import tools.vitruv.methodologist.vsum.model.VsumViewMetaModel;

/**
 * Repository for accessing {@link VsumViewMetaModel} entities.
 * Provides data access methods to query and remove view-meta-model associations by their owning
 * {@link VsumView}.
 */
@Repository
public interface VsumViewMetaModelRepository extends CrudRepository<VsumViewMetaModel, Long> {

  /**
   * Retrieves all view-meta-model associations for the given VSUM view.
   *
   * @param vsumView the VSUM view whose associated view-meta-model entries are requested
   * @return a list of {@link VsumViewMetaModel} entities linked to the provided VSUM view
   */
  List<VsumViewMetaModel> findAllByVsumView(VsumView vsumView);

  /**
   * Deletes all view-meta-model associations for the given VSUM view.
   *
   * @param vsumView the VSUM view whose associated view-meta-model entries should be removed
   */
  void deleteAllByVsumView(VsumView vsumView);
}
