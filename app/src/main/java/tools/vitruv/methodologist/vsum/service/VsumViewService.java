package tools.vitruv.methodologist.vsum.service;

import static tools.vitruv.methodologist.messages.Error.VIEW_FILE_ID_NOT_FOUND_ERROR;

import java.util.List;
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
   * @return the created and persisted {@link VsumView}
   * @throws NotFoundException if the given file does not exist or is not of type {@code NEO_JOIN}
   */
  @Transactional
  public VsumView create(Vsum vsum, Long fileStorageId) {
    FileStorage fileStorage =
        fileStorageRepository
            .findByIdAndType(fileStorageId, FileEnumType.NEO_JOIN)
            .orElseThrow(() -> new NotFoundException(VIEW_FILE_ID_NOT_FOUND_ERROR));

    VsumView vsumView = VsumView.builder().vsum(vsum).fileStorage(fileStorage).build();

    VsumView saved = vsumViewRepository.save(vsumView);

    if (vsum.getViews() != null) {
      vsum.getViews().add(saved);
    }

    return saved;
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
