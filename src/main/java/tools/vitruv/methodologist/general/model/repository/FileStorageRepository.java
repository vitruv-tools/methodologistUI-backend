package tools.vitruv.methodologist.general.model.repository;

import java.util.Optional;
import org.springframework.data.repository.CrudRepository;
import tools.vitruv.methodologist.general.FileEnumType;
import tools.vitruv.methodologist.general.model.FileStorage;

/**
 * Spring Data repository interface for managing StoredFile entities. Provides CRUD operations and
 * custom queries for StoredFile persistence.
 */
public interface FileStorageRepository extends CrudRepository<FileStorage, Long> {

  /**
   * Finds a stored file by its SHA-256 hash and size in bytes. Used to check for duplicate files in
   * storage.
   *
   * @param sha256 the SHA-256 hash of the file
   * @param sizeBytes the size of the file in bytes
   * @return an Optional containing the found StoredFile, or empty if not found
   */
  Optional<FileStorage> findBySha256AndSizeBytes(String sha256, long sizeBytes);

  /**
   * Finds a {@link FileStorage} by its unique identifier and file type. This method is useful when
   * multiple file records may exist with the same identifier but different {@link FileEnumType},
   * ensuring that the correct type of file is retrieved.
   *
   * @param id the unique identifier of the file storage entry (must not be {@code null})
   * @param type the {@link FileEnumType} representing the expected file type (must not be {@code
   *     null})
   * @return an {@link Optional} containing the matching {@link FileStorage} if found, or an empty
   *     {@link Optional} if no match exists
   */
  Optional<FileStorage> findByIdAndType(Long id, FileEnumType type);
}
