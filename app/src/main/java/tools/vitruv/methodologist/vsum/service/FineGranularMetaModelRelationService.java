package tools.vitruv.methodologist.vsum.service;

import static tools.vitruv.methodologist.messages.Error.FINE_GRANULAR_RELATION_ID_NOT_FOUND_ERROR;
import static tools.vitruv.methodologist.messages.Error.FINE_GRANULAR_RELATION_UPDATE_NOT_ALLOWED_ERROR;
import static tools.vitruv.methodologist.messages.Error.REACTION_FILE_IDS_ID_NOT_FOUND_ERROR;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
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
import tools.vitruv.methodologist.exception.MetaModelRelationCreationException;
import tools.vitruv.methodologist.exception.NoTemplateProvidedException;
import tools.vitruv.methodologist.exception.NotFoundException;
import tools.vitruv.methodologist.general.MemoizedSupplier;
import tools.vitruv.methodologist.general.model.FileStorage;
import tools.vitruv.methodologist.general.model.repository.FileStorageRepository;
import tools.vitruv.methodologist.vsum.controller.dto.request.FineGranularMetaModelRelationRequest;
import tools.vitruv.methodologist.vsum.controller.dto.request.MetaModelRelationRequest;
import tools.vitruv.methodologist.vsum.lowcode.reactions.template.dto.request.LowCodeReactionRequestBase;
import tools.vitruv.methodologist.vsum.lowcode.reactions.template.service.LowCodeReactionService;
import tools.vitruv.methodologist.vsum.mapper.LowCodeReactionRequestMapper;
import tools.vitruv.methodologist.vsum.model.FineGranularMetaModelRelation;
import tools.vitruv.methodologist.vsum.model.MetaModelRelation;
import tools.vitruv.methodologist.vsum.model.repository.FineGranularMetaModelRelationRepository;

/** Service for managing fine-granular meta-model relations. */
@Service
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class FineGranularMetaModelRelationService {
  FineGranularMetaModelRelationRepository fineGranularMetaModelRelationRepository;
  FileStorageRepository fileStorageRepository;
  LowCodeReactionService lowCodeReactionService;
  LowCodeReactionRequestMapper lowCodeReactionRequestMapper;

  /**
   * Creates fine-granular relations for the given requests.
   *
   * @param callerUserEmail the email of the caller
   * @param requestMap the map of requests to parent relations
   */
  @Transactional
  public void create(
      String callerUserEmail,
      Map<FineGranularMetaModelRelationRequest, MetaModelRelation> requestMap) {
    createOrUpdate(callerUserEmail, requestMap, false);
  }

  /**
   * Updates fine-granular relations for the given requests.
   *
   * @param callerUserEmail the email of the caller
   * @param requestMap the map of requests to parent relations
   */
  @Transactional
  public void update(
      String callerUserEmail,
      Map<FineGranularMetaModelRelationRequest, MetaModelRelation> requestMap) {
    createOrUpdate(callerUserEmail, requestMap, true);
  }

  /**
   * Diffs nested fine-granular children of the given coarse relations and applies create, update,
   * and delete. Snapshots history at most once via {@code vsumHistorySaveSupplier}.
   *
   * @param callerEmail the email of the caller
   * @param metaModelRelationRequestToRelation remaining coarse requests mapped to persisted
   *     relations
   * @param vsumHistorySaveSupplier memoized history snapshot
   */
  @Transactional
  public void update(
      String callerEmail,
      Map<MetaModelRelationRequest, MetaModelRelation> metaModelRelationRequestToRelation,
      MemoizedSupplier<Boolean> vsumHistorySaveSupplier) {
    if (metaModelRelationRequestToRelation == null
        || metaModelRelationRequestToRelation.isEmpty()) {
      return;
    }
    Collection<MetaModelRelation> existingMetaModelRelation =
        metaModelRelationRequestToRelation.values();
    Set<MetaModelRelationRequest> metaModelRelationRequests =
        metaModelRelationRequestToRelation.keySet();
    HashMap<FineGranularMetaModelRelationRequest, MetaModelRelationRequest> toAddFineGranularMmr =
        new HashMap<>();
    HashMap<FineGranularMetaModelRelationRequest, FineGranularMetaModelRelation>
        toUpdateFineGranularMmr = new HashMap<>();
    Map<FineGranularMetaModelRelation, MetaModelRelationRequest> toRemoveFineGranularMmr =
        metaModelRelationRequestToRelation.entrySet().stream()
            .flatMap(
                kv ->
                    kv.getValue().getFineGranularMetaModelRelationSet().stream()
                        .map(fgmmr -> new AbstractMap.SimpleEntry<>(fgmmr, kv.getKey())))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    for (MetaModelRelationRequest metaModelRelationRequest : metaModelRelationRequests) {
      for (FineGranularMetaModelRelationRequest toAdd :
          metaModelRelationRequest.getFineGranularMetaModelRelationSet()) {
        var exactMatch =
            toRemoveFineGranularMmr.keySet().stream()
                .filter(remove -> toAdd.equals(lowCodeReactionRequestMapper, remove))
                .findFirst();
        if (exactMatch.isPresent()) {
          toRemoveFineGranularMmr.remove(exactMatch.get());
        } else if (toAdd.getId() == null) {
          toAddFineGranularMmr.put(toAdd, metaModelRelationRequest);
        } else {
          FineGranularMetaModelRelation toUpdate =
              toRemoveFineGranularMmr.keySet().stream()
                  .filter(key -> Objects.equals(key.getId(), toAdd.getId()))
                  .findFirst()
                  .orElseThrow(
                      () -> new NotFoundException(FINE_GRANULAR_RELATION_ID_NOT_FOUND_ERROR));
          toUpdateFineGranularMmr.put(toAdd, toUpdate);
          toRemoveFineGranularMmr.remove(toUpdate);
        }
      }
      existingMetaModelRelation.stream()
          .filter(
              metaModelRelation ->
                  Objects.equals(
                          metaModelRelation.getSource().getSource().getId(),
                          metaModelRelationRequest.getSourceId())
                      && Objects.equals(
                          metaModelRelation.getTarget().getSource().getId(),
                          metaModelRelationRequest.getTargetId()))
          .findFirst()
          .ifPresent(
              metaModelRelation ->
                  metaModelRelationRequestToRelation.put(
                      metaModelRelationRequest, metaModelRelation));
    }

    if (!toRemoveFineGranularMmr.isEmpty()
        || !toAddFineGranularMmr.isEmpty()
        || !toUpdateFineGranularMmr.isEmpty()) {
      vsumHistorySaveSupplier.get();
    }

    if (!toRemoveFineGranularMmr.isEmpty()) {
      this.delete(toRemoveFineGranularMmr.keySet().stream().toList());
      toRemoveFineGranularMmr.forEach(
          (key, value) ->
              value
                  .getFineGranularMetaModelRelationSet()
                  .removeIf(fgmmr -> Objects.equals(fgmmr.getId(), key.getId())));
    }

    if (!toAddFineGranularMmr.isEmpty()) {
      var map =
          toAddFineGranularMmr.entrySet().stream()
              .collect(
                  Collectors.toMap(
                      Map.Entry::getKey,
                      e -> metaModelRelationRequestToRelation.get(e.getValue())));
      this.create(callerEmail, map);
    }

    if (!toUpdateFineGranularMmr.isEmpty()) {
      var map =
          toUpdateFineGranularMmr.entrySet().stream()
              .collect(
                  Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getMetaModelRelation()));
      this.update(callerEmail, map);
    }
  }

  /**
   * Creates or updates relations for the given requests. Requires a reaction file id or a low-code
   * template.
   *
   * @param callerUserEmail the email of the caller
   * @param requestMap the map of requests to parent relations
   * @param allowUpdate whether an existing id may be updated
   */
  @Transactional
  protected void createOrUpdate(
      String callerUserEmail,
      Map<FineGranularMetaModelRelationRequest, MetaModelRelation> requestMap,
      boolean allowUpdate) {
    if (requestMap == null || requestMap.isEmpty()) {
      return;
    }

    List<FineGranularMetaModelRelation> toSave = new ArrayList<>();

    for (var kv : requestMap.entrySet()) {
      FineGranularMetaModelRelationRequest request = kv.getKey();
      MetaModelRelation relation = kv.getValue();
      LowCodeReactionRequestBase lowCodeRequest = request.getLowCodeReactionRequestBase();
      var builder =
          FineGranularMetaModelRelation.builder()
              .metaModelRelation(relation)
              .sourceId(request.getSourceId())
              .targetId(request.getTargetId());
      if (request.getId() != null) {
        if (!allowUpdate) {
          throw new MetaModelRelationCreationException(
              FINE_GRANULAR_RELATION_UPDATE_NOT_ALLOWED_ERROR);
        }
        var optExistingFgmmr =
            relation.getFineGranularMetaModelRelationSet().stream()
                .filter(fgmr -> Objects.equals(fgmr.getId(), request.getId()))
                .findFirst();
        if (optExistingFgmmr.isPresent()) {
          builder = builder.id(optExistingFgmmr.get().getId());
        } else {
          throw new NotFoundException(FINE_GRANULAR_RELATION_ID_NOT_FOUND_ERROR);
        }
      }
      if (request.getReactionFileStorageId() != null) {
        FileStorage existingFile =
            fileStorageRepository
                .findById(request.getReactionFileStorageId())
                .orElseThrow(() -> new NotFoundException(REACTION_FILE_IDS_ID_NOT_FOUND_ERROR));
        if (lowCodeRequest != null && lowCodeRequest.isRegenerate()) {
          lowCodeRequest.setRegenerate(false);
          builder
              .reactionFileStorage(
                  lowCodeReactionService.generateAndSaveReaction(
                      callerUserEmail, lowCodeRequest, existingFile))
              .lowCodeReactionTemplate(lowCodeRequest.getName())
              .lowCodeReactionTemplateParams(lowCodeRequest.toTemplateData());
        } else {
          builder.reactionFileStorage(existingFile);
        }
      } else {
        if (lowCodeRequest == null) {
          throw new NoTemplateProvidedException();
        }
        lowCodeRequest.setRegenerate(false);
        builder
            .reactionFileStorage(
                lowCodeReactionService.generateAndSaveReaction(
                    callerUserEmail, lowCodeRequest, null))
            .lowCodeReactionTemplate(lowCodeRequest.getName())
            .lowCodeReactionTemplateParams(lowCodeRequest.toTemplateData());
      }
      toSave.add(builder.build());
    }

    toSave.forEach(
        rel -> {
          if (rel.getId() != null) {
            rel.getMetaModelRelation()
                .getFineGranularMetaModelRelationSet()
                .removeIf(existing -> Objects.equals(existing.getId(), rel.getId()));
          }
          rel.getMetaModelRelation().getFineGranularMetaModelRelationSet().add(rel);
        });
    fineGranularMetaModelRelationRepository.saveAll(toSave);
  }

  /**
   * Deletes the provided fine-granular relations in batch.
   *
   * @param relations the relations to delete
   */
  @Transactional
  public void delete(List<FineGranularMetaModelRelation> relations) {
    relations.forEach(
        rel -> rel.getMetaModelRelation().getFineGranularMetaModelRelationSet().remove(rel));
    fineGranularMetaModelRelationRepository.deleteAll(relations);
  }
}
