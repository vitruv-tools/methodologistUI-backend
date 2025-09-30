package tools.vitruv.methodologist.vsum.model.repository;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import tools.vitruv.methodologist.user.model.User;
import tools.vitruv.methodologist.vsum.model.MetaModel;

/**
 * Repository interface for performing CRUD operations and complex queries on {@link MetaModel}
 * entities. Extends the {@link CrudRepository} interface and includes custom query methods.
 *
 * <p>The repository provides methods for searching, filtering, and retrieving MetaModel entities
 * based on various parameters such as name, user ownership, source identifier, and JPA
 * specifications.
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

  /**
   * Retrieves all {@link MetaModel} entities that match the given identifiers and belong to the
   * specified {@link User}, filtering out any that have a non-null {@code source}.
   *
   * @param metaModelIds the set of metamodel identifiers to search for
   * @param user the owner of the metamodels
   * @return a list of {@link MetaModel} entities that match the given IDs, belong to the user, and
   *     have {@code source} set to null
   */
  List<MetaModel> findAllByIdInAndUserAndSourceIsNull(Set<Long> metaModelIds, User user);

  /**
   * Finds a metamodel by its ID and the associated user's email address.
   *
   * @param id the unique identifier of the metamodel to find
   * @param callerEmail the email address of the user who owns the metamodel
   * @return Optional containing the found MetaModel if it exists and belongs to the specified user,
   *     or empty if no match is found
   */
  Optional<MetaModel> findByIdAndUser_Email(Long id, String callerEmail);
}
