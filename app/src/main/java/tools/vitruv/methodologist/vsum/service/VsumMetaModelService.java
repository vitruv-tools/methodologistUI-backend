package tools.vitruv.methodologist.vsum.service;

import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.vitruv.methodologist.general.model.FileStorage;
import tools.vitruv.methodologist.general.service.FileStorageService;
import tools.vitruv.methodologist.vsum.model.MetaModel;
import tools.vitruv.methodologist.vsum.model.Vsum;
import tools.vitruv.methodologist.vsum.model.VsumMetaModel;
import tools.vitruv.methodologist.vsum.model.repository.MetaModelRepository;
import tools.vitruv.methodologist.vsum.model.repository.VsumMetaModelRepository;

/**
 * Service layer for managing {@link tools.vitruv.methodologist.vsum.model.VsumMetaModel}
 * operations. Handles the business logic around creating and persisting {@link
 * tools.vitruv.methodologist.vsum.model.Vsum} instances and their associations with metamodels.
 *
 * <p>Uses {@link tools.vitruv.methodologist.vsum.model.repository.VsumMetaModelRepository} for
 * persistence operations.
 */
@Service
@Slf4j
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class VsumMetaModelService {
  VsumMetaModelRepository vsumMetaModelRepository;
  MetaModelService metaModelService;
  MetaModelRepository metaModelRepository;
  EntityManager entityManager;
  FileStorageService fileStorageService;

  /**
   * Finds all metamodels accessible by a given project.
   *
   * <p>This method retrieves:
   *
   * <ul>
   *   <li>All metamodels associated with the project via VSUM (if vsumId is provided)
   * </ul>
   *
   * <p>Duplicates are automatically removed by using a Set internally. This method is primarily
   * used by the Language Server to determine which metamodels should be loaded for a specific
   * editing session.
   *
   * @param vsumId the ID of the project/VSUM whose metamodels should be included (can be null)
   * @return a list of unique MetaModel instances accessible by the user and/or project
   */
  @Transactional(readOnly = true)
  public List<MetaModel> findAccessibleByProject(Long vsumId) {
    if (vsumId == null) {
      log.debug("No VSUM given, so no metamodels are accessible");
      return List.of();
    }

    List<VsumMetaModel> vsumMetaModels = vsumMetaModelRepository.findByVsumId(vsumId);

    Set<MetaModel> metamodels = new LinkedHashSet<>();

    for (VsumMetaModel vmm : vsumMetaModels) {
      MetaModel mm = vmm.getMetaModel();
      if (mm.getSource() != null) {
        metamodels.add(mm.getSource());
      } else {
        metamodels.add(mm);
      }
    }

    log.debug("VSUM {} gives access to {} metamodels", vsumId, metamodels.size());
    return new ArrayList<>(metamodels);
  }

  /**
   * Creates {@link VsumMetaModel} links for the given vsum and metamodel IDs. Each metamodel is
   * cloned before being linked to the vsum.
   *
   * <p>Flushes before returning so that callers within the same transaction (for example {@link
   * MetaModelRelationService#create}, which looks these links up immediately afterward to resolve
   * relation endpoints) see the newly created rows rather than a stale, pre-flush view.
   *
   * @param vsum the parent vsum
   * @param metaModelIds IDs of metamodels to associate
   */
  @Transactional
  public void create(Vsum vsum, Set<Long> metaModelIds) {
    create(vsum, metaModelIds, Map.of());
  }

  /**
   * Creates {@link VsumMetaModel} links for the given VSUM and metamodel IDs with optional
   * project-specific names.
   *
   * <p>When no project-specific name is supplied, the library meta-model name is used.
   *
   * @param vsum the parent VSUM
   * @param metaModelIds IDs of metamodels to associate
   * @param metaModelNames project-specific names keyed by library meta-model ID
   */
  @Transactional
  public void create(Vsum vsum, Set<Long> metaModelIds, Map<Long, String> metaModelNames) {
    List<MetaModel> metaModels = metaModelRepository.findAllByIdInAndSourceIsNull(metaModelIds);
    Map<Long, String> names = metaModelNames == null ? Map.of() : metaModelNames;

    List<VsumMetaModel> links = new ArrayList<>();
    for (MetaModel metaModel : metaModels) {
      MetaModel cloned = metaModelService.clone(metaModel);
      String name = names.get(metaModel.getId());
      if (name == null || name.isBlank()) {
        name = metaModel.getName();
      }
      links.add(VsumMetaModel.builder().vsum(vsum).metaModel(cloned).name(name).build());
    }
    vsumMetaModelRepository.saveAll(links);
    entityManager.flush();
  }

  /**
   * Creates new {@link VsumMetaModel} associations for the given {@link Vsum}.
   *
   * <p>First, all previously cloned {@link MetaModel} instances linked to the given {@code
   * vsumMetaModels} are removed using {@link #deleteCloned(List)}. Then, the provided {@link
   * VsumMetaModel} associations are deleted from the repository and detached from the {@link Vsum}.
   *
   * @param vsum the parent {@link Vsum} whose associations are being recreated
   * @param vsumMetaModels the list of {@link VsumMetaModel} associations to remove before creation
   */
  @Transactional
  public void delete(Vsum vsum, List<VsumMetaModel> vsumMetaModels) {
    vsumMetaModelRepository.deleteAll(vsumMetaModels);
    vsum.getVsumMetaModels().removeAll(vsumMetaModels);
    deleteCloned(vsumMetaModels.stream().map(VsumMetaModel::getMetaModel).toList());
  }

  /**
   * Deletes all {@link VsumMetaModel} associations and their cloned {@link MetaModel} instances
   * linked to the specified {@link Vsum}.
   *
   * @param vsum the VSUM whose metamodel associations and cloned metamodels should be deleted
   */
  @Transactional
  public void delete(Vsum vsum) {
    List<MetaModel> metaModels =
        vsum.getVsumMetaModels().stream().map(VsumMetaModel::getMetaModel).toList();
    vsumMetaModelRepository.deleteVsumMetaModelByVsum(vsum);
    deleteCloned(metaModels);
  }

  /**
   * Deletes the file storage records associated with the given cloned {@link MetaModel} entities.
   *
   * <p>Collects all linked Ecore and GenModel files from the provided metamodels and removes them
   * from persistent storage using {@link
   * tools.vitruv.methodologist.general.service.FileStorageService}.
   *
   * @param metaModels the list of cloned {@link MetaModel} entities whose files should be deleted
   */
  void deleteCloned(List<MetaModel> metaModels) {
    metaModelRepository.deleteAll(metaModels);
    List<FileStorage> fileStorages = new ArrayList<>();
    fileStorages.addAll(metaModels.stream().map(MetaModel::getEcoreFile).toList());
    fileStorages.addAll(metaModels.stream().map(MetaModel::getGenModelFile).toList());
    fileStorageService.deleteFiles(fileStorages);
  }

  /**
   * Filename and content of a metamodel's Ecore file, detached from the persistence context.
   *
   * @param filename the stored filename
   * @param data the file content
   */
  public record EcoreFile(String filename, byte[] data) {}

  /**
   * Returns the Ecore files of all metamodels linked to the given VSUM. The files are read inside
   * the transaction, so callers outside of it (e.g. WebSocket handlers) can use them safely.
   *
   * @param vsumId the ID of the VSUM
   * @return the VSUM's Ecore files; empty if the VSUM has no metamodels
   */
  @Transactional(readOnly = true)
  public List<EcoreFile> findEcoreFiles(Long vsumId) {
    return vsumMetaModelRepository.findAllWithEcoreFileByVsum_Id(vsumId).stream()
        .map(vsumMetaModel -> vsumMetaModel.getMetaModel().getEcoreFile())
        .map(file -> new EcoreFile(file.getFilename(), file.getData()))
        .toList();
  }
}
