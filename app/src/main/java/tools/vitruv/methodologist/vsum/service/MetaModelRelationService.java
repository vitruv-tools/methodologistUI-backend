package tools.vitruv.methodologist.vsum.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
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

    metaModelRelationRepository.saveAll(toSave);
  }

  /** Deletes the provided relations in batch. */
  public void delete(List<MetaModelRelation> relations) {
    metaModelRelationRepository.deleteAll(relations);
  }
}
