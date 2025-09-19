package tools.vitruv.methodologist.vsum.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.vitruv.methodologist.vsum.model.MetaModel;
import tools.vitruv.methodologist.vsum.model.Vsum;
import tools.vitruv.methodologist.vsum.model.VsumMetaModel;
import tools.vitruv.methodologist.vsum.model.repository.MetaModelRepository;
import tools.vitruv.methodologist.vsum.model.repository.VsumMetaModelRepository;

@Service
public class VsumMetaModelTransactionalService {

  private final VsumMetaModelRepository vsumMetaModelRepository;
  private final MetaModelService metaModelService;
  private final MetaModelRepository metaModelRepository;

  public VsumMetaModelTransactionalService(
      VsumMetaModelRepository vsumMetaModelRepository,
      MetaModelService metaModelService,
      MetaModelRepository metaModelRepository) {
    this.vsumMetaModelRepository = vsumMetaModelRepository;
    this.metaModelService = metaModelService;
    this.metaModelRepository = metaModelRepository;
  }

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

  @Transactional
  public void delete(Vsum vsum, List<VsumMetaModel> vsumMetaModels) {
    vsumMetaModelRepository.deleteAll(vsumMetaModels);
    vsum.getVsumMetaModels().removeAll(vsumMetaModels);
    metaModelService.deleteCloned(
        vsumMetaModels.stream().map(VsumMetaModel::getMetaModel).toList());
  }
}
