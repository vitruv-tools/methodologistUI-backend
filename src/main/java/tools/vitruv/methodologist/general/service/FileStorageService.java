package tools.vitruv.methodologist.general.service;

import static tools.vitruv.methodologist.messages.Error.USER_EMAIL_NOT_FOUND_ERROR;

import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tools.vitruv.methodologist.exception.NotFoundException;
import tools.vitruv.methodologist.general.model.FileStorage;
import tools.vitruv.methodologist.general.model.repository.FileStorageRepository;
import tools.vitruv.methodologist.user.model.repository.UserRepository;

/**
 * Service class that handles file storage operations including storing, retrieving, and deleting
 * files. Provides deduplication of files based on SHA-256 hash and file size.
 */
@Service
public class FileStorageService {
  private final FileStorageRepository fileStorageRepository;
  private final UserRepository userRepository;

  /**
   * Constructs a new FileStorageService with the specified repositories.
   *
   * @param fileStorageRepository repository for file storage operations
   * @param userRepository repository for user operations
   */
  public FileStorageService(
      FileStorageRepository fileStorageRepository, UserRepository userRepository) {
    this.fileStorageRepository = fileStorageRepository;
    this.userRepository = userRepository;
  }

  /**
   * Calculates the SHA-256 hash of the given data and returns it as a hexadecimal string.
   *
   * @param data the byte array to hash
   * @return hexadecimal string representation of the SHA-256 hash
   * @throws Exception if the hashing algorithm is not available
   */
  private static String sha256Hex(byte[] data) throws Exception {
    MessageDigest md = MessageDigest.getInstance("SHA-256");
    return HexFormat.of().formatHex(md.digest(data));
  }

  /**
   * Stores a file with deduplication check based on SHA-256 hash and file size. If a file with the
   * same hash and size exists, returns the existing file entry.
   *
   * @param callerUserEmail email of the user storing the file
   * @param file the multipart file to store
   * @return the stored or existing FileStorage entity
   * @throws Exception if file processing or storage fails
   * @throws NotFoundException if the user email is not found
   * @throws IllegalArgumentException if the file is empty
   */
  @Transactional
  public FileStorage storeFile(String callerUserEmail, MultipartFile file) throws Exception {
    var user =
        userRepository
            .findByEmailIgnoreCaseAndRemovedAtIsNull(callerUserEmail)
            .orElseThrow(() -> new NotFoundException(USER_EMAIL_NOT_FOUND_ERROR));
    if (file.isEmpty()) {
      throw new IllegalArgumentException("File is empty");
    }

    byte[] data = file.getBytes();
    String sha = sha256Hex(data);

    // dedup check
    return fileStorageRepository
        .findBySha256AndSizeBytes(sha, data.length)
        .orElseGet(
            () -> {
              FileStorage f = new FileStorage();
              f.setFilename(file.getOriginalFilename());
              f.setContentType(
                  file.getContentType() == null
                      ? "application/octet-stream"
                      : file.getContentType());
              f.setSizeBytes(data.length);
              f.setSha256(sha);
              f.setData(data);
              f.setUser(user);
              return fileStorageRepository.save(f);
            });
  }

  /**
   * Retrieves a file by its ID.
   *
   * @param id the ID of the file to retrieve
   * @return the FileStorage entity
   * @throws IllegalArgumentException if the file is not found
   */
  @Transactional(readOnly = true)
  public FileStorage getFile(Long id) {
    return fileStorageRepository
        .findById(id)
        .orElseThrow(() -> new IllegalArgumentException("File not found"));
  }

  /**
   * Deletes a file by its ID.
   *
   * @param id the ID of the file to delete
   */
  @Transactional
  public void deleteFile(Long id) {
    fileStorageRepository.deleteById(id);
  }
}
