package tools.vitruv.methodologist.general.model.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.repository.CrudRepository;
import tools.vitruv.methodologist.general.FileEnumType;
import tools.vitruv.methodologist.general.model.FileStorage;
import tools.vitruv.methodologist.user.model.User;

/**
 * Spring Data repository interface for managing StoredFile entities. Provides CRUD operations and
 * custom queries for StoredFile persistence.
 */
public interface FileStorageRepository extends CrudRepository<FileStorage, Long> {

  /**
   * Checks if a file with the specified user, SHA-256 hash, and size already exists in storage.
   * This method helps prevent duplicate file storage by comparing file attributes.
   *
   * @param user the user who owns the file
   * @param sha256 the SHA-256 hash of the file content
   * @param sizeBytes the size of the file in bytes
   * @return true if a matching file exists, false otherwise
   */
  boolean existsByUserAndSha256AndSizeBytes(User user, String sha256, long sizeBytes);

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
  Optional<FileStorage> findByIdAndUser_EmailAndUser_RemovedAtIsNull(Long id, String callerEmail);
}
