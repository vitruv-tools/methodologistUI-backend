package tools.vitruv.methodologist.vsum.model.repository;

import java.util.List;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tools.vitruv.methodologist.vsum.model.MetaModelRelation;
import tools.vitruv.methodologist.vsum.model.Vsum;

/**
 * Repository for managing {@link MetaModelRelation} entities. Provides CRUD operations through
 * {@link CrudRepository}.
 */
@Repository
public interface MetaModelRelationRepository extends CrudRepository<MetaModelRelation, Long> {
  /**
   * Retrieves all {@link MetaModelRelation} entities associated with the given {@link Vsum}.
   *
   * <p>Returns an empty list if no relations are found. Order is unspecified.
   *
   * @param vsum the VSUM aggregate to filter by; must not be {@code null}
   * @return a list of matching {@link MetaModelRelation} entities
   */
  List<MetaModelRelation> findAllByVsum(Vsum vsum);
}
