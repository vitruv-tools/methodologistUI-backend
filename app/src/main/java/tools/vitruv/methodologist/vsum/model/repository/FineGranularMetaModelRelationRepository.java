package tools.vitruv.methodologist.vsum.model.repository;

import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tools.vitruv.methodologist.vsum.model.FineGranularMetaModelRelation;

/**
 * Repository for managing {@link FineGranularMetaModelRelation} entities. Provides CRUD operations
 * through {@link CrudRepository}.
 */
@Repository
public interface FineGranularMetaModelRelationRepository
    extends CrudRepository<FineGranularMetaModelRelation, Long> {

  /**
   * Returns the ids of the VSUMs whose fine-granular relations use the given reaction file.
   *
   * @param reactionFileId the id of the reaction file
   * @return the distinct VSUM ids; empty if no fine-granular relation uses the file
   */
  @Query(
      """
          select distinct f.metaModelRelation.vsum.id from FineGranularMetaModelRelation f
          where f.reactionFileStorage.id = ?1""")
  List<Long> findVsumIdsByReactionFileId(Long reactionFileId);
}
