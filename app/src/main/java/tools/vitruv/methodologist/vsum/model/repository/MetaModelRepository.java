package tools.vitruv.methodologist.vsum.model.repository;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tools.vitruv.methodologist.vsum.model.MetaModel;

/**
 * Repository interface for managing {@link tools.vitruv.methodologist.vsum.model.MetaModel}
 * entities. Provides CRUD operations and custom queries for MetaModel data access.
 */
@Repository
public interface MetaModelRepository extends CrudRepository<MetaModel, Long> {
  /**
   * Finds a metamodel by its name, ignoring case sensitivity.
   *
   * @param name the name of the metamodel to find (case-insensitive)
   * @return Optional containing the found MetaModel or empty if not found
   * @throws IllegalArgumentException if name is null or blank
   */
  Optional<MetaModel> findByNameIgnoreCase(@NotNull @NotBlank String name);

  /**
   * Retrieves all {@link MetaModel} entities matching the given JPA specification with pagination
   * support.
   *
   * <p>The provided {@link Specification} defines filtering conditions, while the {@link Pageable}
   * parameter determines page number, size, and sorting.
   *
   * @param spec specification defining filtering conditions for metamodels (may be null to fetch
   *     all)
   * @param pageable pagination information including page index, size, and sort order
   * @return a list of {@link MetaModel} entities that match the specification and pagination
   */
  List<MetaModel> findAll(Specification<MetaModel> spec, Pageable pageable);
}
