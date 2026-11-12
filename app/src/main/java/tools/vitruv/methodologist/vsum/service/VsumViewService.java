package tools.vitruv.methodologist.vsum.service;

import static tools.vitruv.methodologist.messages.Error.VIEW_FILE_ID_NOT_FOUND_ERROR;

import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.vitruv.methodologist.exception.NotFoundException;
import tools.vitruv.methodologist.general.FileEnumType;
import tools.vitruv.methodologist.general.model.FileStorage;
import tools.vitruv.methodologist.general.model.repository.FileStorageRepository;
import tools.vitruv.methodologist.vsum.model.Vsum;
import tools.vitruv.methodologist.vsum.model.VsumView;
import tools.vitruv.methodologist.vsum.model.repository.VsumViewMetaModelRepository;
import tools.vitruv.methodologist.vsum.model.repository.VsumViewRepository;

/**
 * Service for managing {@link VsumView} entities.
 *
 * <p>A {@link VsumView} represents one NeoJoin-backed view belonging to a VSUM. The actual
 * metamodel associations of a view are managed separately through {@code VsumViewMetaModel}.
 */
@Service
@Slf4j
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class VsumViewService {

  VsumViewRepository vsumViewRepository;
  FileStorageRepository fileStorageRepository;
  VsumViewMetaModelRepository vsumViewMetaModelRepository;

  /**
   * Creates and persists a new {@link VsumView} for the given VSUM and NeoJoin file.
   *
   * @param vsum target VSUM
   * @param fileStorageId identifier of the NeoJoin file
   * @param name name of the view, unique within the VSUM
   * @param description optional description of the view
   * @return the created and persisted {@link VsumView}
   * @throws NotFoundException if the given file does not exist or is not of type {@code NEO_JOIN}
   */
  @Transactional
  public VsumView create(Vsum vsum, Long fileStorageId, String name, String description) {
    FileStorage fileStorage = findNeoJoinFile(fileStorageId);

    VsumView vsumView =
        VsumView.builder()
            .vsum(vsum)
            .name(name)
            .description(description)
            .fileStorage(fileStorage)
            .build();

    VsumView saved = vsumViewRepository.save(vsumView);

    if (vsum.getViews() != null) {
      vsum.getViews().add(saved);
    }

    return saved;
  }

  /**
   * Changes the name, description and file of an existing {@link VsumView}; the view keeps its ID.
   *
   * @param vsumView the view to change
   * @param fileStorageId identifier of the NeoJoin file
   * @param name new name of the view, unique within the VSUM
   * @param description new description of the view, {@code null} for none
   * @return the saved {@link VsumView}
   * @throws NotFoundException if the given file does not exist or is not of type {@code NEO_JOIN}
   */
  @Transactional
  public VsumView update(VsumView vsumView, Long fileStorageId, String name, String description) {
    if (vsumView.getFileStorage() == null
        || !Objects.equals(vsumView.getFileStorage().getId(), fileStorageId)) {
      vsumView.setFileStorage(findNeoJoinFile(fileStorageId));
    }
    vsumView.setName(name);
    vsumView.setDescription(description);
    return vsumViewRepository.save(vsumView);
  }

  /**
   * Loads a NeoJoin file a view can be backed by.
   *
   * @param fileStorageId identifier of the NeoJoin file
   * @return the file
   * @throws NotFoundException if the given file does not exist or is not of type {@code NEO_JOIN}
   */
  public FileStorage findNeoJoinFile(Long fileStorageId) {
    return fileStorageRepository
        .findByIdAndType(fileStorageId, FileEnumType.NEO_JOIN)
        .orElseThrow(() -> new NotFoundException(VIEW_FILE_ID_NOT_FOUND_ERROR));
  }

  /**
   * Deletes the specified view and removes it from the in-memory VSUM collection if present.
   *
   * <p>Metamodel associations ({@code VsumViewMetaModel}) for the view are deleted before the view
   * itself to satisfy foreign key constraints.
   *
   * @param vsum target VSUM
   * @param vsumView view to delete
   */
  @Transactional
  public void delete(Vsum vsum, VsumView vsumView) {
    vsumViewMetaModelRepository.deleteAllByVsumView(vsumView);
    vsumViewRepository.delete(vsumView);

    if (vsum.getViews() != null) {
      vsum.getViews().remove(vsumView);
    }
  }

  /**
   * Deletes all views associated with the given VSUM.
   *
   * <p>Metamodel associations ({@code VsumViewMetaModel}) for all views are deleted before the
   * views themselves to satisfy foreign key constraints.
   *
   * @param vsum target VSUM
   */
  @Transactional
  public void deleteByVsum(Vsum vsum) {
    List<VsumView> views = vsumViewRepository.findAllByVsum(vsum);
    if (!views.isEmpty()) {
      vsumViewMetaModelRepository.deleteAllByVsumViewIn(views);
    }
    vsumViewRepository.deleteAllByVsum(vsum);

    if (vsum.getViews() != null) {
      vsum.getViews().clear();
    }
  }
}
