package tools.vitruv.methodologist.vsum.service;

import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
   * vsumMetaModels} are removed using {@link MetaModelService#deleteCloned(List)}. Then, the
   * provided {@link VsumMetaModel} associations are deleted from the repository and detached from
   * the {@link Vsum}.
   *
   * @param vsum the parent {@link Vsum} whose associations are being recreated
   * @param vsumMetaModels the list of {@link VsumMetaModel} associations to remove before creation
   */
  public void delete(Vsum vsum, List<VsumMetaModel> vsumMetaModels) {
    vsumMetaModelRepository.deleteAll(vsumMetaModels);
    vsum.getVsumMetaModels().removeAll(vsumMetaModels);
    metaModelService.deleteCloned(
        vsumMetaModels.stream().map(VsumMetaModel::getMetaModel).toList());
  }

  /**
   * Deletes all {@link VsumMetaModel} associations and their cloned {@link MetaModel} instances
   * linked to the specified {@link Vsum}.
   *
   * @param vsum the VSUM whose metamodel associations and cloned metamodels should be deleted
   */
  public void delete(Vsum vsum) {
    List<MetaModel> metaModels =
        vsum.getVsumMetaModels().stream().map(VsumMetaModel::getMetaModel).toList();
    vsumMetaModelRepository.deleteVsumMetaModelByVsum(vsum);
    metaModelService.deleteCloned(metaModels);
  }
}
