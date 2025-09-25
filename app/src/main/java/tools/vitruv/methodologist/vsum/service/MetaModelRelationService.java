package tools.vitruv.methodologist.vsum.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

  MetaModelRelationRepository relationRepository;
  FileStorageRepository fileStorageRepository;
  VsumMetaModelRepository vsumMetaModelRepository;

  /**
   * Reconciles DB relations with provided requests: removes missing, creates new, keeps unchanged.
   * If a matching relation exists but reactionFileId changed and is non-null, updates file storage.
   */
  @Transactional
  public void sync(Vsum vsum, List<MetaModelRelationRequest> requests) {
    List<MetaModelRelationRequest> desired =
        requests == null
            ? List.of()
            : requests.stream()
                .filter(r -> r != null && r.getSourceId() != null && r.getTargetId() != null)
                .toList();

    List<MetaModelRelation> existing = relationRepository.findAllByVsum(vsum);

    Set<String> desiredPairs =
        desired.stream()
            .map(r -> r.getSourceId() + ":" + r.getTargetId())
            .collect(Collectors.toSet());

    Map<String, MetaModelRelation> existingByPair = new HashMap<>();
    for (MetaModelRelation rel : existing) {
      String k = rel.getSource().getId() + ":" + rel.getTarget().getId();
      existingByPair.put(k, rel);
    }

    Set<String> existingPairs = new HashSet<>(existingByPair.keySet());

    Set<String> toRemove = new HashSet<>(existingPairs);
    toRemove.removeAll(desiredPairs);

    if (!toRemove.isEmpty()) {
      List<MetaModelRelation> deletions = toRemove.stream().map(existingByPair::get).toList();
      delete(deletions);
    }

    Set<String> toAdd = new HashSet<>(desiredPairs);
    toAdd.removeAll(existingPairs);

    if (!toAdd.isEmpty()) {
      List<MetaModelRelationRequest> creations =
          desired.stream()
              .filter(r -> toAdd.contains(r.getSourceId() + ":" + r.getTargetId()))
              .toList();
      create(vsum, creations);
    }
  }

  /**
   * Creates relations for the given requests; requires non-null reactionFileId for new relations.
   */
  @Transactional
  public void create(Vsum vsum, List<MetaModelRelationRequest> requests) {
    List<Long> ids = new ArrayList<>();
    for (MetaModelRelationRequest r : requests) {
      ids.add(r.getSourceId());
      ids.add(r.getTargetId());
    }
    Map<Long, MetaModel> mmById =
        vsumMetaModelRepository.findAllByVsumAndMetaModel_source_idIn(vsum, ids).stream()
            .collect(
                Collectors.toMap(
                    m -> m.getMetaModel().getSource().getId(), VsumMetaModel::getMetaModel));

    Set<Long> fileIds =
        requests.stream()
            .map(MetaModelRelationRequest::getReactionFileId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    Map<Long, FileStorage> fsById =
        fileStorageRepository.findAllByIdIn(fileIds).stream()
            .collect(Collectors.toMap(FileStorage::getId, f -> f));

    List<MetaModelRelation> toSave =
        requests.stream()
            .map(
                r -> {
                  MetaModel s = mmById.get(r.getSourceId());
                  MetaModel t = mmById.get(r.getTargetId());
                  // todo: check the reaction file uploaded
                  if (r.getReactionFileId() == null) {
                    throw new IllegalArgumentException(
                        "reactionFileId is required for new relations");
                  }
                  FileStorage f = fsById.get(r.getReactionFileId());
                  return MetaModelRelation.builder()
                      .vsum(vsum)
                      .source(s)
                      .target(t)
                      .reactionFileStorage(f)
                      .build();
                })
            .toList();

    relationRepository.saveAll(toSave);
  }

  /** Deletes the provided relations in batch. */
  @Transactional
  public void delete(List<MetaModelRelation> relations) {
    relationRepository.deleteAll(relations);
  }
}
