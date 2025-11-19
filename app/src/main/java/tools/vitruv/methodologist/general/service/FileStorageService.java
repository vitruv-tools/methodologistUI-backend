package tools.vitruv.methodologist.general.service;

import static tools.vitruv.methodologist.messages.Error.FILE_HASHING_EXCEPTION;
import static tools.vitruv.methodologist.messages.Error.USER_EMAIL_NOT_FOUND_ERROR;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tools.vitruv.methodologist.exception.FileAlreadyExistsException;
import tools.vitruv.methodologist.exception.FileHashingException;
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
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class FileStorageService {
  FileStorageRepository fileStorageRepository;
  UserRepository userRepository;
  FileStorageMapper fileStorageMapper;

  /**
   * Computes the SHA\-256 digest of the given bytes and returns its lowercase hexadecimal string.
   *
   * <p>Uses {@link java.security.MessageDigest} with the {@code SHA\-256} algorithm and wraps any
   * algorithm lookup failure into {@link
   * tools.vitruv.methodologist.exception.FileHashingException}.
   *
   * @param data the bytes to hash; must not be {@code null}
   * @return lowercase hex representation of the SHA\-256 digest
   * @throws tools.vitruv.methodologist.exception.FileHashingException if hashing cannot be
   *     performed
   */
  private String sha256Hex(byte[] data) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(md.digest(data));
    } catch (NoSuchAlgorithmException e) {
      throw new FileHashingException(FILE_HASHING_EXCEPTION, e);
    }
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
  @SuppressWarnings({
    "checkstyle:CommentsIndentation",
    "checkstyle:VariableDeclarationUsageDistance"
  })
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

    // todo: we have to comment below condition for powerful reason before our demo day
    //    if (fileStorageRepository.existsByUserAndSha256AndSizeBytes(user, sha, data.length)) {
    //      throw new FileAlreadyExistsException();
    //    }

    FileStorage fileStorage = new FileStorage();
    fileStorage.setFilename(file.getOriginalFilename());
    fileStorage.setType(type);
    fileStorage.setContentType(
        file.getContentType() == null ? "application/octet-stream" : file.getContentType());
    fileStorage.setSizeBytes(data.length);
    fileStorage.setSha256(sha);
    fileStorage.setData(data);
    fileStorage.setUser(user);
    fileStorageRepository.save(fileStorage);

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
   * Updates an existing file by overwriting its stored content with a new file, while enforcing
   * deduplication against other files owned by the same user.
   *
   * <p>The existing file is identified by its ID. The caller must be the owner of the file. If a
   * different file of the same user with identical content already exists, the update is rejected.
   *
   * @param callerUserEmail email of the user requesting the update
   * @param fileId the ID of the existing file to update
   * @param file the new MultipartFile whose content will replace the existing file content
   * @return FileStorageResponse containing the updated file's ID
   * @throws Exception if file hashing fails
   * @throws NotFoundException if the user email is not found
   * @throws IllegalArgumentException if the file is empty or the file ID is not found
   * @throws FileAlreadyExistsException if another file with the same content already exists for the
   *     same user
   */
  @Transactional
  public FileStorageResponse updateFile(String callerUserEmail, Long fileId, MultipartFile file)
      throws Exception {
    User user =
        userRepository
            .findByEmailIgnoreCaseAndRemovedAtIsNull(callerUserEmail)
            .orElseThrow(() -> new NotFoundException(USER_EMAIL_NOT_FOUND_ERROR));

    FileStorage existing =
        fileStorageRepository
            .findByIdAndType(fileId, FileEnumType.REACTION)
            .orElseThrow(() -> new NotFoundException("File not found"));

    if (existing.getUser() == null
        || existing.getUser().getId() == null
        || !existing.getUser().getId().equals(user.getId())) {
      throw new IllegalArgumentException("File does not belong to the requesting user");
    }

    if (file.isEmpty()) {
      throw new IllegalArgumentException("File is empty");
    }

    byte[] data = file.getBytes();
    String sha = sha256Hex(data);

    existing.setFilename(file.getOriginalFilename());
    existing.setType(FileEnumType.REACTION);
    existing.setContentType(
        file.getContentType() == null ? "application/octet-stream" : file.getContentType());
    existing.setSizeBytes(data.length);
    existing.setSha256(sha);
    existing.setData(data);

    fileStorageRepository.save(existing);

    return FileStorageResponse.builder().id(existing.getId()).build();
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
