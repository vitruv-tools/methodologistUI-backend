package tools.vitruv.methodologist.general.model.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import tools.vitruv.methodologist.general.FileEnumType;
import tools.vitruv.methodologist.general.model.FileStorage;
import tools.vitruv.methodologist.user.model.User;
import tools.vitruv.methodologist.vsum.model.Vsum;

/**
 * Spring Data repository interface for managing StoredFile entities. Provides CRUD operations and
 * custom queries for StoredFile persistence.
 */
public interface FileStorageRepository extends CrudRepository<FileStorage, Long> {

  /**
   * Checks if a file with the specified user, SHA-256 hash, and size already exists in storage,
   * ignoring files of one type. This method helps prevent duplicate file storage by comparing file
   * attributes.
   *
   * @param user the user who owns the file
   * @param sha256 the SHA-256 hash of the file content
   * @param sizeBytes the size of the file in bytes
   * @param ignoredType files of this type do not count, e.g. the copies held by stable versions
   * @return true if a matching file of another type exists, false otherwise
   */
  boolean existsByUserAndSha256AndSizeBytesAndTypeNot(
      User user, String sha256, long sizeBytes, FileEnumType ignoredType);

  /**
   * Finds a {@link tools.vitruv.methodologist.general.model.FileStorage} by its unique identifier
   * and file type. This method is useful when multiple file records may exist with the same
   * identifier but different {@link tools.vitruv.methodologist.general.FileEnumType}, ensuring that
   * the correct type of file is retrieved.
   *
   * @param id the unique identifier of the file storage entry (must not be {@code null})
   * @param type the {@link tools.vitruv.methodologist.general.FileEnumType} representing the
   *     expected file type (must not be {@code null})
   * @return an {@link java.util.Optional} containing the matching {@link
   *     tools.vitruv.methodologist.general.model.FileStorage} if found, or an empty {@link
   *     java.util.Optional} if no match exists
   */
  Optional<FileStorage> findByIdAndType(Long id, FileEnumType type);

  /**
   * Finds a file storage entry by id, type, and owning user email when the user is active.
   *
   * @param id the identifier of the file storage entry
   * @param type the expected file type
   * @param callerEmail the email of the owning active user
   * @return the matching file storage entry if it exists, belongs to the caller, and has the
   *     expected type; otherwise an empty optional
   */
  @SuppressWarnings("checkstyle:MethodName")
  Optional<FileStorage> findByIdAndTypeAndUser_EmailAndUser_RemovedAtIsNull(
      Long id, FileEnumType type, String callerEmail);

  /**
   * Retrieves all {@link FileStorage} entities matching the given set of IDs and file type.
   *
   * @param fileIds the set of file storage IDs to search for (must not be {@code null})
   * @param type the {@link FileEnumType} to filter by (must not be {@code null})
   * @return a list of matching {@link FileStorage} entities; empty if none found
   */
  List<FileStorage> findAllByIdInAndType(Set<Long> fileIds, FileEnumType type);

  /**
   * Finds a {@link tools.vitruv.methodologist.general.model.FileStorage} by its id when the owning
   * user's email matches the supplied callerEmail and the user has not been removed (i.e. {@code
   * user.removedAt} is {@code null}).
   *
   * @param id the identifier of the file storage entry (must not be {@code null})
   * @param callerEmail the email address of the caller / owning user to match (must not be {@code
   *     null})
   * @return an {@link java.util.Optional} containing the matching {@link
   *     tools.vitruv.methodologist.general.model.FileStorage} if present and the user is not
   *     removed; otherwise an empty {@link java.util.Optional}
   */
  @SuppressWarnings("checkstyle:MethodName")
  Optional<FileStorage> findByIdAndUser_EmailAndUser_RemovedAtIsNull(Long id, String callerEmail);

  /**
   * Checks whether the active user with the given email may read a file: either they uploaded it,
   * or they are a member of a non-removed VSUM that references it as a metamodel Ecore or GenModel
   * file, a reaction file, an OCL file, a view file, or a build artifact.
   *
   * @param fileId the identifier of the file storage entry
   * @param callerEmail the email of the requesting user
   * @return {@code true} if the file exists and the caller may read it
   */
  @Query(
      """
      select count(f) > 0 from FileStorage f
      where f.id = :fileId
        and (
          (lower(f.user.email) = lower(:callerEmail) and f.user.removedAt is null)
          or exists (
            select 1 from VsumUser vu
            where lower(vu.user.email) = lower(:callerEmail)
              and vu.user.removedAt is null
              and vu.vsum.removedAt is null
              and (
                exists (
                  select 1 from VsumMetaModel vmm
                  where vmm.vsum = vu.vsum
                    and vmm.removedAt is null
                    and (vmm.metaModel.ecoreFile = f or vmm.metaModel.genModelFile = f))
                or exists (
                  select 1 from MetaModelRelation r
                  where r.vsum = vu.vsum and r.reactionFileStorage = f)
                or exists (
                  select 1 from FineGranularMetaModelRelation g
                  where g.metaModelRelation.vsum = vu.vsum and g.reactionFileStorage = f)
                or exists (
                  select 1 from ConstraintRuleSet c
                  where c.vsum = vu.vsum and c.oclFile = f)
                or exists (
                  select 1 from VsumView v
                  where v.vsum = vu.vsum and v.fileStorage = f)
                or exists (
                  select 1 from VsumBuild b
                  where b.vsum = vu.vsum and b.artifact = f))))
      """)
  boolean isReadableBy(@Param("fileId") Long fileId, @Param("callerEmail") String callerEmail);

  /**
   * Finds the reaction files among the given ids that may be attached to relations of a VSUM: those
   * the caller uploaded, and those already used by a relation or fine-granular relation of that
   * VSUM, so collaborators can keep a reaction file another member uploaded. Files of any other
   * type, and files that do not exist, are not returned.
   *
   * @param fileIds the file ids requested by the caller
   * @param callerEmail the email of the requesting user
   * @param vsum the VSUM the relations belong to
   * @return the matching reaction files; ids that are missing from the result must be rejected
   */
  @Query(
      """
      SELECT f FROM FileStorage f
      WHERE f.id IN :fileIds
        AND f.type = tools.vitruv.methodologist.general.FileEnumType.REACTION
        AND (
          (LOWER(f.user.email) = LOWER(:callerEmail) AND f.user.removedAt IS NULL)
          OR EXISTS (
            SELECT 1 FROM MetaModelRelation r
            WHERE r.vsum = :vsum AND r.reactionFileStorage = f)
          OR EXISTS (
            SELECT 1 FROM FineGranularMetaModelRelation g
            WHERE g.metaModelRelation.vsum = :vsum AND g.reactionFileStorage = f))
      """)
  List<FileStorage> findAttachableReactionFiles(
      @Param("fileIds") Set<Long> fileIds,
      @Param("callerEmail") String callerEmail,
      @Param("vsum") Vsum vsum);
}
