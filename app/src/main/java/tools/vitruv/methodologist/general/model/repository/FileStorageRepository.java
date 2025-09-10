package tools.vitruv.methodologist.general.model.repository;

import java.util.Optional;
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
   * Finds a stored file by its SHA-256 hash and size in bytes. Used to check for duplicate user's
   * files in storage.
   *
   * @param user the owner of the file
   * @param sha256 the SHA-256 hash of the file
   * @param sizeBytes the size of the file in bytes
   * @return an Optional containing the found StoredFile, or empty if not found
   */
  Optional<FileStorage> findByUserAndSha256AndSizeBytes(User user, String sha256, long sizeBytes);

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
}
