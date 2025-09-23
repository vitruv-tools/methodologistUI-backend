package tools.vitruv.methodologist.vsum.service;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
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

  /**
   * Synchronizes persisted relations for the given {@link Vsum} and source {@link MetaModel} to
   * match the provided desired target meta models.
   *
   * <p>Loads existing relations, computes differences by target id, deletes relations for targets
   * no longer desired, and creates relations for newly desired targets. Ignores {@code null} and
   * duplicate entries in {@code desiredTargets}. A {@code null} {@code desiredTargets} is treated
   * as an empty list (removing all existing targets).
   *
   * @param vsum the VSUM context whose relations are synchronized; must not be {@code null}
   * @param sourceMetaModel the source meta model whose outgoing relations are synchronized; must
   *     not be {@code null}
   * @param desiredTargets the desired set of target meta models; may be {@code null}, {@code null}
   *     elements are ignored
   */
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
   */
  public void create(Vsum vsum, MetaModel sourceMetaModel, List<MetaModel> targetMetaModels) {
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
  public void delete(Vsum vsum, MetaModel sourceMetaModel, List<MetaModel> targetMetaModels) {
    metaModelRelationRepository.deleteByVsumAndSourceAndTargetIn(
        vsum, sourceMetaModel, targetMetaModels);
  }
}
