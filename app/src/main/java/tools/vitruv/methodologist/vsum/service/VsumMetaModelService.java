package tools.vitruv.methodologist.vsum.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
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
  public void delete(Vsum vsum, List<VsumMetaModel> vsumMetaModels) {
    vsumMetaModelRepository.deleteAll(vsumMetaModels);
    vsum.getVsumMetaModels().removeAll(vsumMetaModels);
    metaModelService.deleteCloned(
        vsumMetaModels.stream().map(VsumMetaModel::getMetaModel).toList());
  }

  /**
   * Deletes all {@link VsumMetaModel} associations linked to the specified {@link Vsum}.
   *
   * @param vsum the VSUM whose metamodel associations should be deleted
   */
  public void delete(Vsum vsum) {
    vsumMetaModelRepository.deleteVsumMetaModelByVsum(vsum);
  }
}
