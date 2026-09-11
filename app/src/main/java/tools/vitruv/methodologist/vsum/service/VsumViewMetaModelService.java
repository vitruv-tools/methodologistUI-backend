package tools.vitruv.methodologist.vsum.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.StreamSupport;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.vitruv.methodologist.vsum.model.MetaModel;
import tools.vitruv.methodologist.vsum.model.VsumMetaModel;
import tools.vitruv.methodologist.vsum.model.VsumView;
import tools.vitruv.methodologist.vsum.model.VsumViewMetaModel;
import tools.vitruv.methodologist.vsum.model.repository.MetaModelRepository;
import tools.vitruv.methodologist.vsum.model.repository.VsumMetaModelRepository;
import tools.vitruv.methodologist.vsum.model.repository.VsumViewMetaModelRepository;

/**
 * Service for managing associations between {@link VsumView} and {@link MetaModel}.
 *
 * <p>Each {@link VsumViewMetaModel} row links one VSUM view to one metamodel. A single view may be
 * associated with multiple metamodels.
 */
@Service
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class VsumViewMetaModelService {

  VsumViewMetaModelRepository vsumViewMetaModelRepository;
  MetaModelRepository metaModelRepository;
  VsumMetaModelRepository vsumMetaModelRepository;

  /**
   * Creates associations between a view and the provided metamodel ids.
   *
   * <p>Only source metamodels are considered valid for association. A {@code null} or empty {@code
   * metaModelIds} set results in an empty list being returned without querying the database.
   *
   * @param vsumView target view
   * @param metaModelIds metamodel ids to associate; may be {@code null} (treated as empty)
   * @return persisted join entities
   */
  @Transactional
  public List<VsumViewMetaModel> create(VsumView vsumView, Set<Long> metaModelIds) {
    if (metaModelIds == null || metaModelIds.isEmpty()) {
      return List.of();
    }

    List<VsumMetaModel> vsumMetaModels =
        vsumMetaModelRepository.findAllByVsumAndMetaModel_source_idIn(
            vsumView.getVsum(), metaModelIds);

    List<VsumViewMetaModel> entities = new ArrayList<>(vsumMetaModels.size());
    for (VsumMetaModel vsumMetaModel : vsumMetaModels) {
      entities.add(
          VsumViewMetaModel.builder()
              .vsumView(vsumView)
              .metaModel(vsumMetaModel.getMetaModel())
              .build());
    }

    return StreamSupport.stream(vsumViewMetaModelRepository.saveAll(entities).spliterator(), false)
        .toList();
  }

  /**
   * Deletes the provided associations.
   *
   * @param viewMetaModels associations to delete
   */
  @Transactional
  public void delete(List<VsumViewMetaModel> viewMetaModels) {
    vsumViewMetaModelRepository.deleteAll(viewMetaModels);
  }

  /**
   * Deletes all metamodel associations of the given view.
   *
   * @param vsumView target view
   */
  @Transactional
  public void deleteByVsumView(VsumView vsumView) {
    vsumViewMetaModelRepository.deleteAllByVsumView(vsumView);
  }
}
