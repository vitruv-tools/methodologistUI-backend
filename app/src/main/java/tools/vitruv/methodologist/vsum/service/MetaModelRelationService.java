package tools.vitruv.methodologist.vsum.service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.vitruv.methodologist.exception.MetaModelRelationCreationException;
import tools.vitruv.methodologist.exception.NotFoundException;
import tools.vitruv.methodologist.general.FileEnumType;
import tools.vitruv.methodologist.general.MemoizedSupplier;
import tools.vitruv.methodologist.general.model.FileStorage;
import tools.vitruv.methodologist.general.model.repository.FileStorageRepository;
import tools.vitruv.methodologist.vsum.controller.dto.request.MetaModelRelationRequest;
import tools.vitruv.methodologist.vsum.model.*;
import tools.vitruv.methodologist.vsum.model.repository.MetaModelRelationRepository;
import tools.vitruv.methodologist.vsum.model.repository.VsumMetaModelRepository;

import static tools.vitruv.methodologist.messages.Error.*;

/** Syncs MetaModel relations in a VSUM using full-state input. */
@Service
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MetaModelRelationService {
  MetaModelRelationRepository metaModelRelationRepository;
  FileStorageRepository fileStorageRepository;
  VsumMetaModelRepository vsumMetaModelRepository;
  FineGranularMetaModelRelationService fineGranularMetaModelRelationService;

  /**
   * Creates relations for the given requests; requires non-null reactionFileId or fine granular relations for new relations.
   */
  @Transactional
  public Map<MetaModelRelationRequest, MetaModelRelation> create(Vsum vsum, List<MetaModelRelationRequest> requests) {
    Map<MetaModelRelationRequest, MetaModelRelation> map = new HashMap<>();
    requests.forEach(r -> map.put(r, null));
    return createOrUpdate(vsum, map, false);
  }

  /**
   * Updates relations for the given requests; requires non-null reactionFileId or fine granular relations for new relations.
   */
  @Transactional
  public Map<MetaModelRelationRequest, MetaModelRelation> update(Vsum vsum, Map<MetaModelRelationRequest, MetaModelRelation> metaModelRelationRequestToRelation) {
    return createOrUpdate(vsum, metaModelRelationRequestToRelation, true);
  }

  /**
   * Creates or updates relations for the given requests; requires non-null reactionFileId or fine granular relations for new relations.
   */
  @Transactional
  protected Map<MetaModelRelationRequest, MetaModelRelation> createOrUpdate(Vsum vsum, Map<MetaModelRelationRequest, MetaModelRelation> metaModelRelationRequestToRelation, boolean allowUpdate) {
    var result = new HashMap<MetaModelRelationRequest, MetaModelRelation>();
    var requests = metaModelRelationRequestToRelation.keySet();
    if (metaModelRelationRequestToRelation.isEmpty()) {
      return result;
    }

    Set<Long> metaModelSourceIds =
            requests.stream()
                    .flatMap(r -> Stream.of(r.getSourceId(), r.getTargetId()))
                    .collect(Collectors.toSet());

    Map<Long, MetaModel> metaModelBySourceId =
            vsumMetaModelRepository
                    .findAllByVsumAndMetaModel_source_idIn(vsum, metaModelSourceIds)
                    .stream()
                    .map(VsumMetaModel::getMetaModel)
                    .collect(
                            Collectors.toMap(metaModel -> metaModel.getSource().getId(), Function.identity()));

    Set<Long> reactionFileIds =
            requests.stream()
                    .map(MetaModelRelationRequest::getReactionFileId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
    Map<Long, FileStorage> reactionFileById =
            fileStorageRepository.findAllByIdInAndType(reactionFileIds, FileEnumType.REACTION).stream()
                    .collect(Collectors.toMap(FileStorage::getId, fileStorage -> fileStorage));

    Set<Long> missingMM =
            requests.stream()
                    .flatMap(r -> Stream.of(r.getSourceId(), r.getTargetId()))
                    .filter(id -> !metaModelBySourceId.containsKey(id))
                    .collect(Collectors.toSet());
    if (!missingMM.isEmpty()) {
      throw new NotFoundException(METAMODEL_IDS_NOT_FOUND_IN_THIS_VSUM_NOT_FOUND_ERROR);
    }

    List<Long> missingFiles =
            reactionFileIds.stream().filter(id -> !reactionFileById.containsKey(id)).toList();
    if (!missingFiles.isEmpty()) {
      throw new NotFoundException(REACTION_FILE_IDS_ID_NOT_FOUND_ERROR);
    }

    record Key(long sourceId, long targetId, Long reactionFileId) {}

    Set<Key> seen = new HashSet<>();

    List<MetaModelRelation> toSave = new ArrayList<>(requests.size());
    for (MetaModelRelationRequest metaModelRelationRequest : requests) {
      Long sourceId = metaModelRelationRequest.getSourceId();
      Long targetId = metaModelRelationRequest.getTargetId();
      Long reactionFileId = metaModelRelationRequest.getReactionFileId();

      if (reactionFileId == null && metaModelRelationRequest.getFineGranularMetaModelRelationSet().isEmpty()) {
        throw new MetaModelRelationCreationException("Metamodel relation must have a reaction file or at least one fine-grained meta-model relation.");
      }

      Key k = new Key(sourceId, targetId, reactionFileId);
      if (!seen.add(k)) {
        continue;
      }

      MetaModel source = metaModelBySourceId.get(sourceId);
      MetaModel target = metaModelBySourceId.get(targetId);

      var builder = MetaModelRelation.builder();
      if (reactionFileId != null) {
        FileStorage reactionFile = reactionFileById.get(reactionFileId);
        builder.reactionFileStorage(reactionFile);
      }

      if (metaModelRelationRequest.getId() != null) {
        if (!allowUpdate) {
          throw new RuntimeException("Not allowed to update metamodel relation with id " + metaModelRelationRequest.getId());
        }
        MetaModelRelation metaModelRelation = metaModelRelationRequestToRelation.get(metaModelRelationRequest);
        if (metaModelRelation == null) {
          throw new RuntimeException("Cannot update metamodel relation with id " + metaModelRelationRequest.getId() + " because it does not exist!");
        }
        if (!Objects.equals(metaModelRelation.getId(), metaModelRelationRequest.getId())) {
          throw new RuntimeException("Cannot update metamodel relation with id " + metaModelRelation.getId() + " because of a mismatching request id " + metaModelRelationRequest.getId() + "!");
        }
        builder = builder.id(metaModelRelation.getId());
        builder.fineGranularMetaModelRelationSet(metaModelRelation.getFineGranularMetaModelRelationSet());
      } else {
        builder.fineGranularMetaModelRelationSet(new HashSet<>());
      }

      builder
              .vsum(vsum)
              .source(source)
              .target(target)
              .build();

      MetaModelRelation metaModelRelation = builder.build();
      result.put(metaModelRelationRequest, metaModelRelation);
      toSave.add(metaModelRelation);
    }

    if (toSave.isEmpty()) {
      return result;
    }

    metaModelRelationRepository.saveAll(toSave);
    return result;
  }

  /** Deletes the provided relations in batch. */
  @Transactional
  public void delete(List<MetaModelRelation> relations) {
    metaModelRelationRepository.deleteAll(relations);
  }

  /**
   * Deletes all {@link MetaModelRelation} associations linked to the specified {@link Vsum}.
   *
   * @param vsum the VSUM whose metamodel relations should be deleted
   */
  public void deleteByVsum(Vsum vsum) {
    metaModelRelationRepository.deleteMetaModelRelationByVsum(vsum);
  }

  @Transactional
  public Map<MetaModelRelationRequest, MetaModelRelation> update(String callerEmail, Vsum vsum, List<MetaModelRelationRequest> metaModelRelationRequests, MemoizedSupplier<Boolean> vsumHistorySaveSupplier) throws Exception {

    List<MetaModelRelationRequest> desiredMetaModelRelation = metaModelRelationRequests == null ? List.of() : metaModelRelationRequests.stream().filter(metaModelRelationRequest -> metaModelRelationRequest != null && metaModelRelationRequest.getSourceId() != null && metaModelRelationRequest.getTargetId() != null).toList();

    List<MetaModelRelation> existingMetaModelRelation =
            metaModelRelationRepository.findAllByVsum(vsum);

    Set<String> desiredMetaModelRelationPairs =
            desiredMetaModelRelation.stream()
                    .map(
                            metaModelRelationRequest ->
                                    metaModelRelationRequest.getSourceId()
                                            + ":"
                                            + metaModelRelationRequest.getTargetId())
                    .collect(Collectors.toSet());

    Map<String, MetaModelRelation> existingByPair = new HashMap<>();
    for (MetaModelRelation metaModelRelation : existingMetaModelRelation) {
      String metaModelRelationPairKey =
              metaModelRelation.getSource().getSource().getId()
                      + ":"
                      + metaModelRelation.getTarget().getSource().getId();
      existingByPair.put(metaModelRelationPairKey, metaModelRelation);
    }

    Set<String> existingMetaModelRelationPairs = new HashSet<>(existingByPair.keySet());

    Set<String> toRemoveMetaModelRelation = new HashSet<>(existingMetaModelRelationPairs);
    toRemoveMetaModelRelation.removeAll(desiredMetaModelRelationPairs);

    Set<String> toAddMetaModelRelation = new HashSet<>(desiredMetaModelRelationPairs);
    toAddMetaModelRelation.removeAll(existingMetaModelRelationPairs);

    Map<MetaModelRelationRequest, MetaModelRelation> toUpdateMetaModelRelation = new HashMap<>();
    for (var metaModelRelation : existingMetaModelRelation) {
      var desired =
              desiredMetaModelRelation.stream()
                      .filter(
                              metaModelRelationRequest ->
                                      Objects.equals(metaModelRelation.getSource().getSource().getId(), metaModelRelationRequest.getSourceId())
                                      && Objects.equals(metaModelRelation.getTarget().getSource().getId(), metaModelRelationRequest.getTargetId())
                                      && !metaModelRelationRequest.equals(metaModelRelation)
                      )
                      .findFirst();
      desired.ifPresent(metaModelRelationRequest -> toUpdateMetaModelRelation.put(metaModelRelationRequest, metaModelRelation));
    }
    Map<MetaModelRelationRequest, MetaModelRelation> metaModelRelationRequestToRelation = new HashMap<>(toUpdateMetaModelRelation);

    // Save vsum to history if it wasn't already (with this supplier)
    if (!toRemoveMetaModelRelation.isEmpty() || !toAddMetaModelRelation.isEmpty() || !toUpdateMetaModelRelation.isEmpty()) {
      vsumHistorySaveSupplier.get();
    }

    if (!toRemoveMetaModelRelation.isEmpty()) {
      List<MetaModelRelation> deletions =
              toRemoveMetaModelRelation.stream().map(existingByPair::get).toList();
      this.delete(deletions);
      deletions.forEach(vsum.getMetaModelRelations()::remove);
    }

    if (!toAddMetaModelRelation.isEmpty()) {
      List<MetaModelRelationRequest> creations =
              desiredMetaModelRelation.stream()
                      .filter(
                              metaModelRelationRequest ->
                                      toAddMetaModelRelation.contains(
                                              metaModelRelationRequest.getSourceId()
                                                      + ":"
                                                      + metaModelRelationRequest.getTargetId()))
                      .toList();
      metaModelRelationRequestToRelation.putAll(this.create(vsum, creations));
    }

    if (!toUpdateMetaModelRelation.isEmpty()) {
      metaModelRelationRequestToRelation.putAll(this.update(vsum, toUpdateMetaModelRelation));
    }

    fineGranularMetaModelRelationService.update(callerEmail, metaModelRelationRequestToRelation, vsumHistorySaveSupplier);

    return metaModelRelationRequestToRelation;
  }
}
