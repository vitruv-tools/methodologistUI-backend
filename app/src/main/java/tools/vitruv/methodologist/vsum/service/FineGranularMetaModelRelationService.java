package tools.vitruv.methodologist.vsum.service;

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
import tools.vitruv.methodologist.vsum.model.FineGranularMetaModelRelation;
import tools.vitruv.methodologist.vsum.model.MetaModelRelation;
import tools.vitruv.methodologist.vsum.model.repository.FineGranularMetaModelRelationRepository;

import java.util.*;
import java.util.stream.Collectors;

import static tools.vitruv.methodologist.messages.Error.NO_TEMPLATE_PROVIDED_ERROR;
import static tools.vitruv.methodologist.messages.Error.REACTION_FILE_IDS_ID_NOT_FOUND_ERROR;

@Service
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class FineGranularMetaModelRelationService {
    FineGranularMetaModelRelationRepository fineGranularMetaModelRelationRepository;
    FileStorageRepository fileStorageRepository;
    LowCodeReactionService lowCodeReactionService;

    /**
     * Creates relations for the given requests; requires non-null reactionFileId or template for new relations.
     */
    @Transactional
    public void create(String callerUserEmail, Map<FineGranularMetaModelRelationRequest, MetaModelRelation> requestMap) throws Exception {
        createOrUpdate(callerUserEmail, requestMap, false);
    }

    /**
     * Updates relations for the given requests; requires non-null reactionFileId or template for new relations.
     */
    @Transactional
    public void update(String callerUserEmail, Map<FineGranularMetaModelRelationRequest, MetaModelRelation> requestMap) throws Exception {
        createOrUpdate(callerUserEmail, requestMap, true);
    }

    /**
     * Creates relations for the given requests; requires non-null reactionFileId or template for new relations.
     */
    @Transactional
    protected void createOrUpdate(String callerUserEmail, Map<FineGranularMetaModelRelationRequest, MetaModelRelation> requestMap, boolean allowUpdate) throws Exception {
        if (requestMap == null || requestMap.isEmpty()) {
            return;
        }

        List<FineGranularMetaModelRelation> toSave = new ArrayList<>();

        for (var kv : requestMap.entrySet()) {
            var request = kv.getKey();
            var relation = kv.getValue();
            var lowCodeRequest = request.getLowCodeReactionRequestBase();
            var builder = FineGranularMetaModelRelation
                    .builder()
                    .metaModelRelation(relation)
                    .sourceId(request.getSourceId())
                    .targetId(request.getTargetId());
            if (request.getId() != null) {
                if (!allowUpdate) {
                    throw new RuntimeException("Not allowed to update fine-granular relation with id " + request.getId());
                }
                var optExistingFGMMR = relation.getFineGranularMetaModelRelationSet().stream().filter(fgmr -> fgmr.getId().equals(request.getId())).findFirst();
                if (optExistingFGMMR.isPresent()) {
                    builder = builder.id(optExistingFGMMR.get().getId());
                } else {
                    throw new RuntimeException("Cannot update fine-granular relation with id " + request.getId() + " because it does not exist!");
                }
            }
            if (request.getReactionFileStorageId() != null) {
                // If just a reaction file id is provided, we expect it to exist in the database.
                var optFileStorage = fileStorageRepository.findById(request.getReactionFileStorageId());
                if (optFileStorage.isEmpty()) {
                    throw new NotFoundException(REACTION_FILE_IDS_ID_NOT_FOUND_ERROR);
                }
                if (lowCodeRequest != null && lowCodeRequest.isRegenerate()) {
                    builder = builder
                            .reactionFileStorage(lowCodeReactionService.generateAndSaveReaction(callerUserEmail, lowCodeRequest, optFileStorage.get()))
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
                builder = builder
                        .reactionFileStorage(lowCodeReactionService.generateAndSaveReaction(callerUserEmail, lowCodeRequest, null))
                        .lowCodeReactionTemplate(lowCodeRequest.getName())
                        .lowCodeReactionTemplateParams(lowCodeRequest.toTemplateData());
            }
            var result = builder.build();
            toSave.add(result);
        }

        fineGranularMetaModelRelationRepository.saveAll(toSave);
    }

    /**
     * Deletes the provided relations in batch.
     */
    @Transactional
    public void delete(List<FineGranularMetaModelRelation> relations) {
        fineGranularMetaModelRelationRepository.deleteAll(relations);
    }

    @Transactional
    public void update(String callerEmail, Map<MetaModelRelationRequest, MetaModelRelation> metaModelRelationRequestToRelation, MemoizedSupplier<Boolean> vsumHistorySaveSupplier) throws Exception {
        var existingMetaModelRelation = metaModelRelationRequestToRelation.values();
        var metaModelRelationRequests = metaModelRelationRequestToRelation.keySet();
        var toAddFineGranularMMR = new HashMap<FineGranularMetaModelRelationRequest, MetaModelRelationRequest>();
        var toUpdateFineGranularMMR = new HashMap<FineGranularMetaModelRelationRequest, MetaModelRelationRequest>();
        var toRemoveFineGranularMMR = metaModelRelationRequestToRelation
                .entrySet()
                .stream()
                .flatMap(kv -> kv.getValue().getFineGranularMetaModelRelationSet()
                .stream()
                .map(fgmmr -> new AbstractMap.SimpleEntry<>(fgmmr, kv.getKey())))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        for (var metaModelRelationRequest : metaModelRelationRequests) {
            for (var toAdd : metaModelRelationRequest.getFineGranularMetaModelRelationSet()) {
                var match = toRemoveFineGranularMMR.keySet().stream().filter(toAdd::equals).findFirst();
                if (match.isPresent()) {
                    // No update required, so no removal either
                    toRemoveFineGranularMMR.remove(match.get());
                } else if(toAdd.getId() == null) {
                    // There is no id, so we create a new fine-granular metamodel relation
                    toAddFineGranularMMR.put(toAdd, metaModelRelationRequest);
                } else {
                    // There is an id and we don't have a matching relation, so it must be an update
                    toUpdateFineGranularMMR.put(toAdd, metaModelRelationRequest);
                }
            }
            var existing =
                    existingMetaModelRelation.stream()
                            .filter(
                                    metaModelRelation ->
                                            Objects.equals(metaModelRelation.getSource().getSource().getId(), metaModelRelationRequest.getSourceId())
                                                    && Objects.equals(metaModelRelation.getTarget().getSource().getId(), metaModelRelationRequest.getTargetId()))
                            .findFirst();
            existing.ifPresent(metaModelRelation -> metaModelRelationRequestToRelation.put(metaModelRelationRequest, metaModelRelation));
        }

        // Save vsum to history if it wasn't already (with this supplier)
        if (!toRemoveFineGranularMMR.isEmpty() || !toAddFineGranularMMR.isEmpty() || !toUpdateFineGranularMMR.isEmpty()) {
            vsumHistorySaveSupplier.get();
        }

        if (!toRemoveFineGranularMMR.isEmpty()) {
            this.delete(toRemoveFineGranularMMR.keySet().stream().toList());
            toRemoveFineGranularMMR.entrySet().forEach(kv -> kv.getValue().getFineGranularMetaModelRelationSet().remove(kv.getKey()));
        }

        if (!toAddFineGranularMMR.isEmpty()) {
            var map = toAddFineGranularMMR.entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            e -> metaModelRelationRequestToRelation.get(e.getValue())
                    ));
            this.create(callerEmail, map);
        }

        if (!toUpdateFineGranularMMR.isEmpty()) {
            var map = toAddFineGranularMMR.entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            e -> metaModelRelationRequestToRelation.get(e.getValue())
                    ));
            this.update(callerEmail, map);
        }
    }
}
