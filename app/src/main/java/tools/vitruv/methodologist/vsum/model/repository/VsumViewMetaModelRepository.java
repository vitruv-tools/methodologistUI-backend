package tools.vitruv.methodologist.vsum.model.repository;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tools.vitruv.methodologist.vsum.model.VsumView;
import tools.vitruv.methodologist.vsum.model.VsumViewMetaModel;

/**
 * Repository for accessing {@link VsumViewMetaModel} entities. Provides data access methods to
 * query and remove view-meta-model associations by their owning {@link VsumView}.
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
   * Retrieves all view-meta-model associations for the given collection of VSUM views in a single
   * query, avoiding N+1 patterns when processing multiple views.
   *
   * @param views the VSUM views whose associated view-meta-model entries are requested
   * @return a list of {@link VsumViewMetaModel} entities linked to any of the provided VSUM views
   */
  List<VsumViewMetaModel> findAllByVsumViewIn(Collection<VsumView> views);

  /**
   * Deletes all view-meta-model associations for the given VSUM view.
   *
   * @param vsumView the VSUM view whose associated view-meta-model entries should be removed
   */
  void deleteAllByVsumView(VsumView vsumView);

  /**
   * Deletes all view-meta-model associations for the given collection of VSUM views in a single
   * bulk query.
   *
   * @param views the VSUM views whose associated view-meta-model entries should be removed
   */
  @Modifying
  @Query("DELETE FROM VsumViewMetaModel vm WHERE vm.vsumView IN :views")
  void deleteAllByVsumViewIn(@Param("views") Collection<VsumView> views);
}
