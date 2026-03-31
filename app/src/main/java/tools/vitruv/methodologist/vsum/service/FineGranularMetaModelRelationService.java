package tools.vitruv.methodologist.vsum.service;

import static tools.vitruv.methodologist.messages.Error.NO_TEMPLATE_PROVIDED_ERROR;
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
import tools.vitruv.methodologist.exception.NotFoundException;
import tools.vitruv.methodologist.general.MemoizedSupplier;
import tools.vitruv.methodologist.general.model.repository.FileStorageRepository;
import tools.vitruv.methodologist.vsum.controller.dto.request.FineGranularMetaModelRelationRequest;
import tools.vitruv.methodologist.vsum.controller.dto.request.MetaModelRelationRequest;
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
   * @param requestMap the map of requests to relations
   * @throws Exception if an error occurs during creation
   */
  @Transactional
  public void create(
      String callerUserEmail,
      Map<FineGranularMetaModelRelationRequest, MetaModelRelation> requestMap)
      throws Exception {
    createOrUpdate(callerUserEmail, requestMap, false);
  }

  /**
   * Updates fine-granular relations for the given requests.
   *
   * @param callerUserEmail the email of the caller
   * @param requestMap the map of requests to relations
   * @throws Exception if an error occurs during update
   */
  @Transactional
  public void update(
      String callerUserEmail,
      Map<FineGranularMetaModelRelationRequest, MetaModelRelation> requestMap)
      throws Exception {
    createOrUpdate(callerUserEmail, requestMap, true);
  }

  /**
   * Updates relations for the given requests; requires non-null reactionFileId or template for new
   * relations.
   *
   * @param callerEmail the email of the caller
   * @param metaModelRelationRequestToRelation the map of requests to relations
   * @param vsumHistorySaveSupplier the supplier to save VSUM history
   * @throws Exception if an error occurs during update
   */
  @Transactional
  public void update(
      String callerEmail,
      Map<MetaModelRelationRequest, MetaModelRelation> metaModelRelationRequestToRelation,
      MemoizedSupplier<Boolean> vsumHistorySaveSupplier)
      throws Exception {
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

    for (var metaModelRelationRequest : metaModelRelationRequests) {
      for (var toAdd : metaModelRelationRequest.getFineGranularMetaModelRelationSet()) {
        var exactMatch =
            toRemoveFineGranularMmr.keySet().stream()
                .filter(remove -> toAdd.equals(lowCodeReactionRequestMapper, remove))
                .findFirst();
        if (exactMatch.isPresent()) {
          // No update required, so no removal either
          toRemoveFineGranularMmr.remove(exactMatch.get());
        } else if (toAdd.getId() == null) {
          // There is no id, so we create a new fine-granular metamodel relation
          toAddFineGranularMmr.put(toAdd, metaModelRelationRequest);
        } else {
          FineGranularMetaModelRelation toUpdate =
              toRemoveFineGranularMmr.keySet().stream()
                  .filter(key -> Objects.equals(key.getId(), toAdd.getId()))
                  .findFirst()
                  .orElseThrow();
          // There is an id and we don't have a matching relation, so it must be an update
          toUpdateFineGranularMmr.put(toAdd, toUpdate);
          toRemoveFineGranularMmr.remove(toUpdate);
        }
      }
      var existing =
          existingMetaModelRelation.stream()
              .filter(
                  metaModelRelation ->
                      Objects.equals(
                              metaModelRelation.getSource().getSource().getId(),
                              metaModelRelationRequest.getSourceId())
                          && Objects.equals(
                              metaModelRelation.getTarget().getSource().getId(),
                              metaModelRelationRequest.getTargetId()))
              .findFirst();
      existing.ifPresent(
          metaModelRelation ->
              metaModelRelationRequestToRelation.put(metaModelRelationRequest, metaModelRelation));
    }

    // Save vsum to history if it wasn't already (with this supplier)
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
   * Creates relations for the given requests; requires non-null reactionFileId or template for new
   * relations.
   */
  @Transactional
  protected void createOrUpdate(
      String callerUserEmail,
      Map<FineGranularMetaModelRelationRequest, MetaModelRelation> requestMap,
      boolean allowUpdate)
      throws Exception {
    if (requestMap == null || requestMap.isEmpty()) {
      return;
    }

    List<FineGranularMetaModelRelation> toSave = new ArrayList<>();

    for (var kv : requestMap.entrySet()) {
      var request = kv.getKey();
      var relation = kv.getValue();
      var lowCodeRequest = request.getLowCodeReactionRequestBase();
      var builder =
          FineGranularMetaModelRelation.builder()
              .metaModelRelation(relation)
              .sourceId(request.getSourceId())
              .targetId(request.getTargetId());
      if (request.getId() != null) {
        if (!allowUpdate) {
          throw new RuntimeException(
              "Not allowed to update fine-granular relation with id " + request.getId());
        }
        var optExistingFgmmr =
            relation.getFineGranularMetaModelRelationSet().stream()
                .filter(fgmr -> fgmr.getId().equals(request.getId()))
                .findFirst();
        if (optExistingFgmmr.isPresent()) {
          builder = builder.id(optExistingFgmmr.get().getId());
        } else {
          throw new RuntimeException(
              "Cannot update fine-granular relation with id "
                  + request.getId()
                  + " because it does not exist!");
        }
      }
      if (request.getReactionFileStorageId() != null) {
        // If just a reaction file id is provided, we expect it to exist in the database.
        var optFileStorage = fileStorageRepository.findById(request.getReactionFileStorageId());
        if (optFileStorage.isEmpty()) {
          throw new NotFoundException(REACTION_FILE_IDS_ID_NOT_FOUND_ERROR);
        }
        if (lowCodeRequest != null && lowCodeRequest.isRegenerate()) {
          // We never persist the request to regenerate to the database
          lowCodeRequest.setRegenerate(false);
          builder =
              builder
                  .reactionFileStorage(
                      lowCodeReactionService.generateAndSaveReaction(
                          callerUserEmail, lowCodeRequest, optFileStorage.get()))
                  .lowCodeReactionTemplate(lowCodeRequest.getName())
                  .lowCodeReactionTemplateParams(lowCodeRequest.toTemplateData());
        } else {
          builder = builder.reactionFileStorage(optFileStorage.get());
        }
      } else {
        if (lowCodeRequest == null) {
          // Either a file has to be provided or a template
          throw new RuntimeException(NO_TEMPLATE_PROVIDED_ERROR);
        }
        // We never persist the request to regenerate to the database
        lowCodeRequest.setRegenerate(false);
        builder =
            builder
                .reactionFileStorage(
                    lowCodeReactionService.generateAndSaveReaction(
                        callerUserEmail, lowCodeRequest, null))
                .lowCodeReactionTemplate(lowCodeRequest.getName())
                .lowCodeReactionTemplateParams(lowCodeRequest.toTemplateData());
      }
      var result = builder.build();
      toSave.add(result);
    }

    // Remove old from metamodel relation explicitly in case of update
    toSave.forEach(
        rel -> rel.getMetaModelRelation().getFineGranularMetaModelRelationSet().remove(rel));
    // Add to metamodel relation explicitly
    toSave.forEach(
        rel -> rel.getMetaModelRelation().getFineGranularMetaModelRelationSet().add(rel));
    fineGranularMetaModelRelationRepository.saveAll(toSave);
  }

  /** Deletes the provided relations in batch. */
  @Transactional
  public void delete(List<FineGranularMetaModelRelation> relations) {
    // Remove from metamodel relation explicitly
    relations.forEach(
        rel -> rel.getMetaModelRelation().getFineGranularMetaModelRelationSet().remove(rel));
    fineGranularMetaModelRelationRepository.deleteAll(relations);
  }
}
