package tools.vitruv.methodologist.vsum.model.repository;

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
 * Repository interface for managing {@link tools.vitruv.methodologist.vsum.model.MetaModel}
 * entities. Provides CRUD operations and custom queries for MetaModel data access.
 */
@Repository
public interface MetaModelRepository extends CrudRepository<MetaModel, Long> {
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
  @SuppressWarnings("checkstyle:MethodName")
  Optional<MetaModel> findByIdAndUser_Email(Long id, String callerEmail);
}
