package tools.vitruv.methodologist.vsum.model.repository;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tools.vitruv.methodologist.vsum.model.FineGranularMetaModelRelation;

/**
 * Repository for managing {@link FineGranularMetaModelRelation} entities. Provides CRUD operations through
 * {@link CrudRepository}.
 */
@Repository
public interface FineGranularMetaModelRelationRepository extends CrudRepository<FineGranularMetaModelRelation, Long> {
}
