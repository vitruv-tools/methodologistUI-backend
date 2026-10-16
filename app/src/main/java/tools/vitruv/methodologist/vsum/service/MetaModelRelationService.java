package tools.vitruv.methodologist.vsum.service;

import static tools.vitruv.methodologist.messages.Error.METAMODEL_IDS_NOT_FOUND_IN_THIS_VSUM_NOT_FOUND_ERROR;
import static tools.vitruv.methodologist.messages.Error.METAMODEL_RELATION_REACTION_OR_FG_REQUIRED_ERROR;
import static tools.vitruv.methodologist.messages.Error.METAMODEL_RELATION_UPDATE_NOT_ALLOWED_ERROR;
import static tools.vitruv.methodologist.messages.Error.REACTION_FILE_IDS_ID_NOT_FOUND_ERROR;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
import tools.vitruv.methodologist.general.model.FileStorage;
import tools.vitruv.methodologist.general.model.repository.FileStorageRepository;
import tools.vitruv.methodologist.vsum.controller.dto.request.MetaModelRelationRequest;
import tools.vitruv.methodologist.vsum.model.MetaModel;
import tools.vitruv.methodologist.vsum.model.MetaModelRelation;
import tools.vitruv.methodologist.vsum.model.Vsum;
import tools.vitruv.methodologist.vsum.model.VsumMetaModel;
import tools.vitruv.methodologist.vsum.model.repository.MetaModelRelationRepository;
import tools.vitruv.methodologist.vsum.model.repository.VsumMetaModelRepository;

/** Syncs MetaModel relations in a VSUM using full-state input. */
@Service
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MetaModelRelationService {

  MetaModelRelationRepository metaModelRelationRepository;
  FileStorageRepository fileStorageRepository;
  VsumMetaModelRepository vsumMetaModelRepository;

  /**
   * Creates relations for the given requests. Requires a reaction file or a non-empty fine-granular
   * set.
   *
   * @param vsum the VSUM to create relations for
   * @param requests the list of requests
   * @return a map of requests to created relations
   */
  @Transactional
  public Map<MetaModelRelationRequest, MetaModelRelation> create(
      Vsum vsum, List<MetaModelRelationRequest> requests) {
    Map<MetaModelRelationRequest, MetaModelRelation> map = new HashMap<>();
    if (requests != null) {
      requests.forEach(r -> map.put(r, null));
    }
    return createOrUpdate(vsum, map, false);
  }

  /**
   * Updates relations for the given requests. Requires a reaction file or a non-empty fine-granular
   * set.
   *
   * @param vsum the VSUM to update relations for
   * @param metaModelRelationRequestToRelation the map of requests to existing relations
   * @return a map of requests to updated relations
   */
  @Transactional
  public Map<MetaModelRelationRequest, MetaModelRelation> update(
      Vsum vsum,
      Map<MetaModelRelationRequest, MetaModelRelation> metaModelRelationRequestToRelation) {
    return createOrUpdate(vsum, metaModelRelationRequestToRelation, true);
  }

  /**
   * Creates or updates relations for the given requests. Requires a reaction file or a non-empty
   * fine-granular set.
   *
   * @param vsum the VSUM to create or update relations for
   * @param metaModelRelationRequestToRelation the map of requests to relations
   * @param allowUpdate whether to allow updates of existing relations
   * @return a map of requests to created or updated relations
   */
  @Transactional
  protected Map<MetaModelRelationRequest, MetaModelRelation> createOrUpdate(
      Vsum vsum,
      Map<MetaModelRelationRequest, MetaModelRelation> metaModelRelationRequestToRelation,
      boolean allowUpdate) {
    var result = new HashMap<MetaModelRelationRequest, MetaModelRelation>();
    if (metaModelRelationRequestToRelation == null
        || metaModelRelationRequestToRelation.isEmpty()) {
      return result;
    }
    var requests = metaModelRelationRequestToRelation.keySet();

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
        reactionFileIds.isEmpty()
            ? Map.of()
            : fileStorageRepository
                .findAllByIdInAndType(reactionFileIds, FileEnumType.REACTION)
                .stream()
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

      if (reactionFileId == null
          && metaModelRelationRequest.getFineGranularMetaModelRelationSet().isEmpty()) {
        throw new MetaModelRelationCreationException(
            METAMODEL_RELATION_REACTION_OR_FG_REQUIRED_ERROR);
      }

      Key k = new Key(sourceId, targetId, reactionFileId);
      if (!seen.add(k)) {
        continue;
      }

      MetaModel source = metaModelBySourceId.get(sourceId);
      MetaModel target = metaModelBySourceId.get(targetId);
      FileStorage reactionFile =
          reactionFileId == null ? null : reactionFileById.get(reactionFileId);

      MetaModelRelation metaModelRelation;
      if (allowUpdate) {
        metaModelRelation = metaModelRelationRequestToRelation.get(metaModelRelationRequest);
        if (metaModelRelation == null) {
          throw new MetaModelRelationCreationException(METAMODEL_RELATION_UPDATE_NOT_ALLOWED_ERROR);
        }
        if (metaModelRelationRequest.getId() != null
            && !Objects.equals(metaModelRelation.getId(), metaModelRelationRequest.getId())) {
          throw new MetaModelRelationCreationException(METAMODEL_RELATION_UPDATE_NOT_ALLOWED_ERROR);
        }
        metaModelRelation.setSource(source);
        metaModelRelation.setTarget(target);
        metaModelRelation.setReactionFileStorage(reactionFile);
      } else {
        if (metaModelRelationRequest.getId() != null) {
          throw new MetaModelRelationCreationException(METAMODEL_RELATION_UPDATE_NOT_ALLOWED_ERROR);
        }
        metaModelRelation =
            MetaModelRelation.builder()
                .vsum(vsum)
                .source(source)
                .target(target)
                .reactionFileStorage(reactionFile)
                .fineGranularMetaModelRelationSet(new HashSet<>())
                .build();
      }

      result.put(metaModelRelationRequest, metaModelRelation);
      toSave.add(metaModelRelation);
    }

    if (toSave.isEmpty()) {
      return result;
    }

    metaModelRelationRepository.saveAll(toSave);
    return result;
  }

  /**
   * Deletes the provided relations in batch.
   *
   * @param relations the list of relations to delete
   */
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
}
