package tools.vitruv.methodologist.vsum.model.repository;

import java.util.List;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tools.vitruv.methodologist.vsum.model.MetaModel;
import tools.vitruv.methodologist.vsum.model.MetaModelRelation;
import tools.vitruv.methodologist.vsum.model.Vsum;

/**
 * Repository for managing {@link MetaModelRelation} entities. Provides CRUD operations through
 * {@link CrudRepository}.
 */
@Repository
public interface MetaModelRelationRepository extends CrudRepository<MetaModelRelation, Long> {
  /**
   * Deletes all {@link MetaModelRelation} entries that belong to the given {@link Vsum}, have the
   * specified source {@link MetaModel}, and whose target is any of the provided targets.
   *
   * <p>Derived Spring Data bulk delete method.
   *
   * @param vsum the VSUM context to match
   * @param sourceMetaModel the source meta model of the relations to delete
   * @param targetMetaModels the target meta models to match (IN clause)
   */
  void deleteByVsumAndSourceAndTargetIn(
      Vsum vsum, MetaModel sourceMetaModel, List<MetaModel> targetMetaModels);

  /**
   * Finds all {@link MetaModelRelation} that belong to the given {@link Vsum} and have the
   * specified source {@link MetaModel}.
   *
   * <p>Derived Spring Data finder method.
   *
   * @param vsum the VSUM context to match
   * @param sourceMetaModel the source meta model of the relations to match
   * @return a list of matching {@link MetaModelRelation}; empty if none found
   */
  List<MetaModelRelation> findAllByVsumAndSource(Vsum vsum, MetaModel sourceMetaModel);

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
