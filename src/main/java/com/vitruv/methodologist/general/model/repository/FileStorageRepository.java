package com.vitruv.methodologist.general.model.repository;

import com.vitruv.methodologist.general.model.FileStorage;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

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
}
