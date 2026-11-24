package tools.vitruv.methodologist.vsum.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.vitruv.methodologist.general.model.FileStorage;
import tools.vitruv.methodologist.general.model.repository.FileStorageRepository;
import tools.vitruv.methodologist.user.model.User;

/**
 * The file copies of one version being published. A stored file is copied once, however often the
 * build inputs and the content use it; a generated file, which is not stored, is copied each time.
 */
final class VersionFileCopies {

  private final FileStorageRepository fileStorageRepository;
  private final User owner;
  private final Map<Long, FileStorage> copiesByFileId = new LinkedHashMap<>();
  private final Map<Long, FileStorage> contentCopiesById = new LinkedHashMap<>();

  /**
   * Starts the copies of a version.
   *
   * @param fileStorageRepository saves the copies and loads the files the content refers to
   * @param owner the publisher, who owns the copies
   */
  VersionFileCopies(FileStorageRepository fileStorageRepository, User owner) {
    this.fileStorageRepository = fileStorageRepository;
    this.owner = owner;
  }

  /**
   * Returns the copy of a file, saving it on first use.
   *
   * @param file a stored or generated file
   * @return the copy; the same copy for every call with the same stored file
   */
  FileStorage copy(FileStorage file) {
    if (file.getId() == null) {
      return save(file);
    }
    return copiesByFileId.computeIfAbsent(file.getId(), id -> save(file));
  }

  /**
   * Returns the copies of the given files, in their order.
   *
   * @param files stored or generated files
   * @return their copies
   */
  List<FileStorage> copyAll(List<FileStorage> files) {
    List<FileStorage> copies = new ArrayList<>(files.size());
    for (FileStorage file : files) {
      copies.add(copy(file));
    }
    return copies;
  }

  /**
   * Returns the id of the copy of a stored file the content refers to and remembers the copy as
   * part of the content.
   *
   * @param fileId the id of the stored file; may be {@code null}
   * @return the id of its copy; {@code null} if {@code fileId} is {@code null}
   * @throws IllegalStateException if no file has that id
   */
  Long contentCopyId(Long fileId) {
    if (fileId == null) {
      return null;
    }
    FileStorage copy = copiesByFileId.get(fileId);
    if (copy == null) {
      FileStorage file =
          fileStorageRepository
              .findById(fileId)
              .orElseThrow(() -> new IllegalStateException("No file with id " + fileId));
      copy = copy(file);
    }
    contentCopiesById.putIfAbsent(copy.getId(), copy);
    return copy.getId();
  }

  /**
   * Returns the copies handed out by {@link #contentCopyId(Long)}.
   *
   * @return each content copy once, in the order of first use
   */
  List<FileStorage> contentCopies() {
    return List.copyOf(contentCopiesById.values());
  }

  private FileStorage save(FileStorage file) {
    return fileStorageRepository.save(VsumVersionService.copyOf(file, owner));
  }
}
