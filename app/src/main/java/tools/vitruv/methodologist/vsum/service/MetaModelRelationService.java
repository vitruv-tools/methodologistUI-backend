package tools.vitruv.methodologist.vsum.service;

import static tools.vitruv.methodologist.messages.Error.METAMODEL_IDS_NOT_FOUND_IN_THIS_VSUM_NOT_FOUND_ERROR;
import static tools.vitruv.methodologist.messages.Error.REACTION_FILE_IDS_ID_NOT_FOUND_ERROR;

import java.util.ArrayList;
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
   * Creates relations for the given requests; requires non-null reactionFileId for new relations.
   */
  @Transactional
  public void create(Vsum vsum, List<MetaModelRelationRequest> requests) {
    if (requests == null || requests.isEmpty()) {
      return;
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

    record Key(long sourceId, long targetId, long reactionFileId) {}

    Set<Key> seen = new HashSet<>();

    List<MetaModelRelation> toSave = new ArrayList<>(requests.size());
    for (MetaModelRelationRequest metaModelRelationRequest : requests) {
      Long sourceId = metaModelRelationRequest.getSourceId();
      Long targetId = metaModelRelationRequest.getTargetId();
      Long reactionFileId = metaModelRelationRequest.getReactionFileId();

      Key k = new Key(sourceId, targetId, reactionFileId);
      if (!seen.add(k)) {
        continue;
      }

      MetaModel source = metaModelBySourceId.get(sourceId);
      MetaModel target = metaModelBySourceId.get(targetId);
      FileStorage reactionFile = reactionFileById.get(reactionFileId);

      toSave.add(
          MetaModelRelation.builder()
              .vsum(vsum)
              .source(source)
              .target(target)
              .reactionFileStorage(reactionFile)
              .build());
    }

    if (toSave.isEmpty()) {
      return;
    }

    // (Optional) If you must avoid duplicates already existing in DB,
    // todo: either enforce a unique constraint (vsum_id, source_id, target_id, reaction_file_id)
    // or query & filter here before saving.

    metaModelRelationRepository.saveAll(toSave);
  }

  /** Deletes the provided relations in batch. */
  @Transactional
  public void delete(List<MetaModelRelation> relations) {
    metaModelRelationRepository.deleteAll(relations);
  }
}
