package tools.vitruv.methodologist.vsum.model.repository;

import java.util.List;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tools.vitruv.methodologist.vsum.model.Vsum;
import tools.vitruv.methodologist.vsum.model.VsumView;

/**
 * Repository for accessing VsumView entities. Provides data access operations for VSUM view records
 * scoped by Vsum.
 */
@Repository
public interface VsumViewRepository extends CrudRepository<VsumView, Long> {

  /**
   * Retrieves all view records associated with the given VSUM.
   *
   * @param vsum the VSUM whose view records should be returned
   * @return a list of VsumView entries associated with the provided VSUM
   */
  List<VsumView> findAllByVsum(Vsum vsum);

  /**
   * Deletes all view records associated with the given VSUM.
   *
   * @param vsum the VSUM whose view records should be removed
   */
  void deleteAllByVsum(Vsum vsum);
}
