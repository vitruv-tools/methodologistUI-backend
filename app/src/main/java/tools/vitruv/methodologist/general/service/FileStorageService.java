package tools.vitruv.methodologist.general.service;

import static tools.vitruv.methodologist.messages.Error.USER_EMAIL_NOT_FOUND_ERROR;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tools.vitruv.methodologist.exception.NotFoundException;
import tools.vitruv.methodologist.general.FileEnumType;
import tools.vitruv.methodologist.general.controller.responsedto.FileStorageResponse;
import tools.vitruv.methodologist.general.mapper.FileStorageMapper;
import tools.vitruv.methodologist.general.model.FileStorage;
import tools.vitruv.methodologist.general.model.repository.FileStorageRepository;
import tools.vitruv.methodologist.user.model.User;
import tools.vitruv.methodologist.user.model.repository.UserRepository;

/**
 * Service class that handles file storage operations including storing, retrieving, and deleting
 * files. Provides deduplication of files based on SHA-256 hash and file size.
 */
@Service
public class FileStorageService {
  private final FileStorageRepository fileStorageRepository;
  private final UserRepository userRepository;
  private final FileStorageMapper fileStorageMapper;

  /**
   * Constructs a new FileStorageService with the specified repositories.
   *
   * @param fileStorageRepository repository for file storage operations
   * @param userRepository repository for user operations
   */
  public FileStorageService(
      FileStorageRepository fileStorageRepository,
      UserRepository userRepository,
      FileStorageMapper fileStorageMapper) {
    this.fileStorageRepository = fileStorageRepository;
    this.userRepository = userRepository;
    this.fileStorageMapper = fileStorageMapper;
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
   * Stores a file in the system with deduplication based on SHA-256 hash and file size. If a file
   * with the same hash and size exists, returns that file instead of creating a duplicate.
   *
   * @param callerUserEmail email of the user storing the file
   * @param file the MultipartFile to store
   * @param type the type of file being stored
   * @return FileStorageResponse containing the stored file's ID
   * @throws Exception if file hashing fails
   * @throws NotFoundException if the user email is not found
   * @throws IllegalArgumentException if the file is empty
   */
  @Transactional
  public FileStorageResponse storeFile(
      String callerUserEmail, MultipartFile file, FileEnumType type) throws Exception {
    User user =
        userRepository
            .findByEmailIgnoreCaseAndRemovedAtIsNull(callerUserEmail)
            .orElseThrow(() -> new NotFoundException(USER_EMAIL_NOT_FOUND_ERROR));
    if (file.isEmpty()) {
      throw new IllegalArgumentException("File is empty");
    }

    byte[] data = file.getBytes();
    String sha = sha256Hex(data);

    FileStorage fileStorage =
        fileStorageRepository
            .findByUserAndSha256AndSizeBytes(user, sha, data.length)
            .orElseGet(
                () -> {
                  FileStorage f = new FileStorage();
                  f.setFilename(file.getOriginalFilename());
                  f.setType(type);
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

    return FileStorageResponse.builder().id(fileStorage.getId()).build();
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
   * Creates a clone of the provided FileStorage object, saves the cloned instance into the
   * repository, and returns the saved instance.
   *
   * @param fileStorage the FileStorage object to be cloned
   * @return the cloned and saved FileStorage object
   */
  @Transactional
  public FileStorage clone(FileStorage fileStorage) {
    FileStorage clonedFileStorage = fileStorageMapper.clone(fileStorage);
    fileStorageRepository.save(clonedFileStorage);
    return clonedFileStorage;
  }

  /**
   * Deletes the given list of {@link FileStorage} entities from the repository.
   *
   * <p>This method removes all provided file storage records in a single transactional operation.
   *
   * @param fileStorages the list of {@link FileStorage} entities to delete
   */
  @Transactional
  public void deleteFiles(List<FileStorage> fileStorages) {
    fileStorageRepository.deleteAll(fileStorages);
  }
}
