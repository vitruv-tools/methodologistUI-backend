package tools.vitruv.methodologist.vsum.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
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

  /**
   * Creates {@link VsumMetaModel} links for the given vsum and metamodel IDs. Each metamodel is
   * cloned before being linked to the vsum.
   *
   * @param vsum the parent vsum
   * @param metaModelIds IDs of metamodels to associate
   */
  @Transactional
  public void create(Vsum vsum, Set<Long> metaModelIds) {
    List<MetaModel> metaModels =
        metaModelRepository.findAllByIdInAndUserAndSourceIsNull(metaModelIds, vsum.getUser());

    List<VsumMetaModel> links = new ArrayList<>();
    for (MetaModel metaModel : metaModels) {
      MetaModel cloned = metaModelService.clone(metaModel);
      links.add(VsumMetaModel.builder().vsum(vsum).metaModel(cloned).build());
    }
    vsumMetaModelRepository.saveAll(links);
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
  @Transactional
  public void delete(Vsum vsum, List<VsumMetaModel> vsumMetaModels) {
    vsumMetaModelRepository.deleteAll(vsumMetaModels);
    vsum.getVsumMetaModels().removeAll(vsumMetaModels);
    metaModelService.deleteCloned(
        vsumMetaModels.stream().map(VsumMetaModel::getMetaModel).toList());
  }

  /**
   * Synchronizes the metamodel associations of a given {@link Vsum} with the provided list of
   * metamodel IDs.
   *
   * <p>This method ensures that the {@link Vsum} contains exactly the metamodels referenced in the
   * given ID list:
   *
   * <ul>
   *   <li>Removes any {@link VsumMetaModel} links whose original (source) metamodel IDs are not in
   *       the provided list.
   *   <li>Creates new {@link VsumMetaModel} links for metamodel IDs that are missing in the current
   *       associations.
   * </ul>
   *
   * @param vsum the {@link Vsum} entity whose metamodel links should be synchronized
   * @param metaModelIds the list of desired metamodel IDs; if {@code null}, all existing
   *     associations will be removed
   */
  @Transactional
  public void sync(Vsum vsum, List<Long> metaModelIds) {
    Set<Long> desiredIds =
        metaModelIds == null
            ? Set.of()
            : metaModelIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());

    List<VsumMetaModel> existingLinks = vsumMetaModelRepository.findAllByVsum(vsum);
    Set<Long> existingIds =
        existingLinks.stream()
            .map(vsumMetaModel -> vsumMetaModel.getMetaModel().getSource().getId())
            .collect(Collectors.toSet());

    Set<Long> toRemoveIds = new HashSet<>(existingIds);
    toRemoveIds.removeAll(desiredIds);
    if (!toRemoveIds.isEmpty()) {
      List<VsumMetaModel> toDelete =
          existingLinks.stream()
              .filter(
                  vsumMetaModel ->
                      toRemoveIds.contains(vsumMetaModel.getMetaModel().getSource().getId()))
              .toList();
      this.delete(vsum, toDelete);
    }

    Set<Long> toAddIds = new HashSet<>(desiredIds);
    toAddIds.removeAll(existingIds);

    if (!toAddIds.isEmpty()) {
      create(vsum, toAddIds);
    }
  }
}
