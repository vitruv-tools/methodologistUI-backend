package tools.vitruv.methodologist.vsum.model.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
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
   * @return a list of {@link MetaModel} entities that match the given IDs, belong to the user, and
   *     have {@code source} set to null
   */
  List<MetaModel> findAllByIdInAndSourceIsNull(Set<Long> metaModelIds);

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

  /**
   * Returns whether the user already has an active library metamodel with this name and version.
   * Cloned metamodels are ignored.
   *
   * @param user the owning user
   * @param name the metamodel name
   * @param version the metamodel version
   * @return {@code true} when a matching library metamodel exists
   */
  @Query(
      """
      SELECT CASE WHEN COUNT(m) > 0 THEN true ELSE false END
      FROM MetaModel m
      WHERE m.user = :user
        AND m.name = :name
        AND m.version = :version
        AND m.source IS NULL
        AND m.removedAt IS NULL
      """)
  boolean existsLibraryMetamodel(
      @Param("user") User user, @Param("name") String name, @Param("version") String version);

  /**
   * Returns whether another active library metamodel of the user has this name and version.
   *
   * @param user the owning user
   * @param name the metamodel name
   * @param version the metamodel version
   * @param excludedId the metamodel id to ignore, typically the one being updated
   * @return {@code true} when a different matching library metamodel exists
   */
  @Query(
      """
      SELECT CASE WHEN COUNT(m) > 0 THEN true ELSE false END
      FROM MetaModel m
      WHERE m.user = :user
        AND m.name = :name
        AND m.version = :version
        AND m.source IS NULL
        AND m.removedAt IS NULL
        AND m.id <> :excludedId
      """)
  boolean existsOtherLibraryMetamodel(
      @Param("user") User user,
      @Param("name") String name,
      @Param("version") String version,
      @Param("excludedId") Long excludedId);
}
