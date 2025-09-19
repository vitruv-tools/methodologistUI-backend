package tools.vitruv.methodologist.vsum.service;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.boot.model.naming.IllegalIdentifierException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.vitruv.methodologist.vsum.model.MetaModel;
import tools.vitruv.methodologist.vsum.model.MetaModelRelation;
import tools.vitruv.methodologist.vsum.model.Vsum;
import tools.vitruv.methodologist.vsum.model.repository.MetaModelRelationRepository;

/**
 * Service class for managing operations related to meta model relations. This class acts as a layer
 * between the controller and the repository, providing business logic and utilizing {@link
 * MetaModelRelationRepository} for data access.
 */
@Service
@Slf4j
public class MetaModelRelationService {
  private final MetaModelRelationRepository metaModelRelationRepository;

  /**
   * Constructs a new instance of {@code MetaModelRelationService}.
   *
   * @param metaModelRelationRepository the repository used for accessing meta model relation data
   */
  public MetaModelRelationService(MetaModelRelationRepository metaModelRelationRepository) {
    this.metaModelRelationRepository = metaModelRelationRepository;
  }

  @Transactional
  public void sync(Vsum vsum, MetaModel sourceMetaModel, List<MetaModel> desiredTargets) {
    List<MetaModel> desired =
        desiredTargets == null
            ? List.of()
            : desiredTargets.stream().filter(Objects::nonNull).distinct().toList();

    List<MetaModelRelation> existing =
        metaModelRelationRepository.findAllByVsumAndSource(vsum, sourceMetaModel);

    Set<Long> desiredIds = desired.stream().map(MetaModel::getId).collect(Collectors.toSet());
    Set<Long> existingIds =
        existing.stream().map(r -> r.getTarget().getId()).collect(Collectors.toSet());

    Set<Long> toRemoveIds = new HashSet<>(existingIds);
    toRemoveIds.removeAll(desiredIds);

    Set<Long> toAddIds = new HashSet<>(desiredIds);
    toAddIds.removeAll(existingIds);

    if (!toRemoveIds.isEmpty()) {
      List<MetaModel> toRemoveTargets =
          existing.stream()
              .map(MetaModelRelation::getTarget)
              .filter(t -> toRemoveIds.contains(t.getId()))
              .toList();
      delete(vsum, sourceMetaModel, toRemoveTargets);
    }

    if (!toAddIds.isEmpty()) {
      List<MetaModel> toAddTargets =
          desired.stream().filter(t -> toAddIds.contains(t.getId())).toList();
      create(vsum, sourceMetaModel, toAddTargets);
    }
  }

  /**
   * Creates and persists relations from the given source meta model to each provided target meta
   * model.
   *
   * <p>Validates that the source and all targets have a non\-null {@code source} before persisting.
   * Executes within a single transaction.
   *
   * @param sourceMetaModel the source {@link MetaModel} of the relations; must have a non\-null
   *     {@code source}
   * @param targetMetaModels the target {@link MetaModel} instances; each must have a non\-null
   *     {@code source}
   * @throws IllegalIdentifierException if the source or any target has a {@code null} {@code
   *     source}
   */
  @Transactional
  public void create(Vsum vsum, MetaModel sourceMetaModel, List<MetaModel> targetMetaModels) {
    if (sourceMetaModel.getSource() == null
        || targetMetaModels.stream().anyMatch(metaModel -> metaModel.getSource() == null)) {
      throw new IllegalIdentifierException("Choosen meta models are not valid");
    }

    List<MetaModelRelation> newMetaModelRelations =
        targetMetaModels.stream()
            .map(
                targetMetaModel ->
                    MetaModelRelation.builder()
                        .vsum(vsum)
                        .source(sourceMetaModel)
                        .target(targetMetaModel)
                        .build())
            .toList();
    metaModelRelationRepository.saveAll(newMetaModelRelations);
  }

  /**
   * Deletes all {@link MetaModelRelation} entries that belong to the given {@link Vsum}, have the
   * specified source {@link MetaModel}, and whose target is any of the provided targets.
   *
   * <p>Executes within a single transaction.
   *
   * @param vsum the VSUM context to match
   * @param sourceMetaModel the source meta model of the relations to delete
   * @param targetMetaModels the target meta models to match \(`IN` clause\)
   */
  @Transactional
  public void delete(Vsum vsum, MetaModel sourceMetaModel, List<MetaModel> targetMetaModels) {
    metaModelRelationRepository.deleteByVsumAndSourceAndTargetIn(
        vsum, sourceMetaModel, targetMetaModels);
  }
}
