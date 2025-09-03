package tools.vitruv.methodologist.vsum.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.vitruv.methodologist.vsum.mapper.MetaModelMapper;
import tools.vitruv.methodologist.vsum.model.MetaModel;
import tools.vitruv.methodologist.vsum.model.Vsum;
import tools.vitruv.methodologist.vsum.model.VsumMetaModel;
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
public class VsumMetaModelService {
  private final VsumMetaModelRepository vsumMetaModelRepository;
  private final MetaModelMapper metaModelMapper;
  private final MetaModelService metaModelService;

  /**
   * Constructs a new service with the required repository dependency.
   *
   * @param vsumMetaModelRepository repository for accessing and persisting {@link
   *     tools.vitruv.methodologist.vsum.model.VsumMetaModel}
   */
  public VsumMetaModelService(
      VsumMetaModelRepository vsumMetaModelRepository,
      MetaModelMapper metaModelMapper,
      MetaModelService metaModelService) {
    this.vsumMetaModelRepository = vsumMetaModelRepository;
    this.metaModelMapper = metaModelMapper;
    this.metaModelService = metaModelService;
  }

  /**
   * Creates a new {@link VsumMetaModel} by cloning the given {@link MetaModel}, marking it as a
   * clone, persisting it, and linking it to the specified {@link Vsum}. The resulting association
   * is then saved and returned.
   *
   * @param vsum the parent Vsum entity
   * @param metaModel the original metamodel to clone and associate
   * @return the persisted VsumMetaModel linking the Vsum with the cloned MetaModel
   */
  @Transactional
  public VsumMetaModel create(Vsum vsum, MetaModel metaModel) {
    MetaModel clonedMetaModel = metaModelMapper.clone(metaModel);
    clonedMetaModel.setIsClone(true);
    metaModelService.save(clonedMetaModel);
    VsumMetaModel vsumMetaModel =
        VsumMetaModel.builder().vsum(vsum).metaModel(clonedMetaModel).build();

    vsumMetaModelRepository.save(vsumMetaModel);

    return vsumMetaModel;
  }
}
