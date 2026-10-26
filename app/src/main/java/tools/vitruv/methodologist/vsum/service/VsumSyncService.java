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
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.vitruv.methodologist.general.MemoizedSupplier;
import tools.vitruv.methodologist.user.model.User;
import tools.vitruv.methodologist.vsum.controller.dto.request.MetaModelRelationRequest;
import tools.vitruv.methodologist.vsum.controller.dto.request.ViewRequest;
import tools.vitruv.methodologist.vsum.controller.dto.request.VsumSyncChangesPutRequest;
import tools.vitruv.methodologist.vsum.mapper.LowCodeReactionRequestMapper;
import tools.vitruv.methodologist.vsum.model.MetaModelRelation;
import tools.vitruv.methodologist.vsum.model.Vsum;
import tools.vitruv.methodologist.vsum.model.VsumMetaModel;
import tools.vitruv.methodologist.vsum.model.VsumView;
import tools.vitruv.methodologist.vsum.model.VsumViewMetaModel;
import tools.vitruv.methodologist.vsum.model.repository.MetaModelRelationRepository;
import tools.vitruv.methodologist.vsum.model.repository.VsumMetaModelRepository;
import tools.vitruv.methodologist.vsum.model.repository.VsumRepository;
import tools.vitruv.methodologist.vsum.model.repository.VsumViewMetaModelRepository;
import tools.vitruv.methodologist.vsum.model.repository.VsumViewRepository;

/**
 * Service that synchronizes the meta models, meta-model relations and views of a {@link Vsum} with
 * a desired state.
 *
 * <p>Used when a member saves the changes made to a VSUM (see {@link VsumService}) and when a VSUM
 * is reverted to an earlier history entry (see {@link VsumHistoryService}).
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class VsumSyncService {

  VsumRepository vsumRepository;
  VsumMetaModelService vsumMetaModelService;
  VsumMetaModelRepository vsumMetaModelRepository;
  MetaModelRelationService metaModelRelationService;
  MetaModelRelationRepository metaModelRelationRepository;
  VsumHistoryService vsumHistoryService;
  VsumViewMetaModelService vsumViewMetaModelService;
  VsumViewService vsumViewService;
  VsumViewRepository vsumViewRepository;
  VsumViewMetaModelRepository vsumViewMetaModelRepository;
  FineGranularMetaModelRelationService fineGranularMetaModelRelationService;
  LowCodeReactionRequestMapper lowCodeReactionRequestMapper;

  /**
   * Synchronize the given {@link Vsum} to match the desired state described by the {@link
   * VsumSyncChangesPutRequest}.
   *
   * <p>The method will:
   *
   * <p>Change persistence and creation/deletion work is delegated to domain services. If {@code
   * createHistory} is {@code true} and any change is detected, a history entry for the provided
   * {@code user} is created. The updated {@link Vsum} is saved before being returned.
   *
   * <p>Runtime exceptions from delegated services (for example {@link
   * tools.vitruv.methodologist.exception.NotFoundException} or {@link
   * org.springframework.security.access.AccessDeniedException}) may be propagated.
   *
   * @param vsum the target {@link Vsum} to update; must not be {@code null}
   * @param user the acting {@link User} used for history creation when changes occur
   * @param vsumSyncChangesPutRequest DTO describing desired meta-model ids and relation requests;
   *     may be {@code null} and will be treated as empty
   * @param createHistory when {@code true}, create a history record if any modifications are made
   * @return the persisted, updated {@link Vsum}
   */
  @Transactional
  public Vsum applySyncChanges(
      Vsum vsum,
      User user,
      VsumSyncChangesPutRequest vsumSyncChangesPutRequest,
      boolean createHistory) {

    List<MetaModelRelationRequest> desiredMetaModelRelation =
        normalizeMetaModelRelationRequests(
            vsumSyncChangesPutRequest.getMetaModelRelationRequests());

    List<MetaModelRelation> existingMetaModelRelation =
        metaModelRelationRepository.findAllByVsum(vsum);

    Set<String> desiredMetaModelRelationPairs =
        desiredMetaModelRelation.stream()
            .map(request -> request.getSourceId() + ":" + request.getTargetId())
            .collect(Collectors.toSet());

    Map<String, MetaModelRelation> existingByPair = new HashMap<>();
    for (MetaModelRelation metaModelRelation : existingMetaModelRelation) {
      String key =
          metaModelRelation.getSource().getSource().getId()
              + ":"
              + metaModelRelation.getTarget().getSource().getId();
      existingByPair.put(key, metaModelRelation);
    }

    Set<String> existingMetaModelRelationPairs = new HashSet<>(existingByPair.keySet());

    Set<String> toRemoveMetaModelRelation = new HashSet<>(existingMetaModelRelationPairs);
    toRemoveMetaModelRelation.removeAll(desiredMetaModelRelationPairs);

    Set<String> toAddMetaModelRelation = new HashSet<>(desiredMetaModelRelationPairs);
    toAddMetaModelRelation.removeAll(existingMetaModelRelationPairs);

    Map<MetaModelRelationRequest, MetaModelRelation> toUpdateMetaModelRelation = new HashMap<>();
    for (MetaModelRelationRequest request : desiredMetaModelRelation) {
      String key = request.getSourceId() + ":" + request.getTargetId();
      MetaModelRelation existing = existingByPair.get(key);
      if (existing != null && !request.equals(lowCodeReactionRequestMapper, existing)) {
        toUpdateMetaModelRelation.put(request, existing);
      }
    }

    List<Long> metaModelIds = vsumSyncChangesPutRequest.getMetaModelIds();
    List<VsumMetaModel> existingVsumMetaModel = vsumMetaModelRepository.findAllByVsum(vsum);
    Map<Long, String> desiredMetaModelNames =
        normalizeMetaModelNames(vsumSyncChangesPutRequest.getMetaModelNames());

    Set<Long> existingVsumMetaModelIds =
        existingVsumMetaModel.stream()
            .map(vsumMetaModel -> vsumMetaModel.getMetaModel().getSource().getId())
            .collect(Collectors.toSet());

    Set<Long> desiredMetaModelIds =
        metaModelIds == null
            ? Set.of()
            : metaModelIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());

    Set<Long> toRemoveVsumMetaModelIds = new HashSet<>(existingVsumMetaModelIds);
    toRemoveVsumMetaModelIds.removeAll(desiredMetaModelIds);

    Set<Long> toAddVsumMetaModelIds = new HashSet<>(desiredMetaModelIds);
    toAddVsumMetaModelIds.removeAll(existingVsumMetaModelIds);

    List<VsumMetaModel> renamedVsumMetaModels =
        existingVsumMetaModel.stream()
            .filter(
                vsumMetaModel -> {
                  Long sourceId = vsumMetaModel.getMetaModel().getSource().getId();
                  String name = desiredMetaModelNames.get(sourceId);
                  return desiredMetaModelIds.contains(sourceId)
                      && name != null
                      && !name.equals(projectMetaModelName(vsumMetaModel));
                })
            .toList();

    List<ViewRequest> desiredViewRequests =
        normalizeViewRequests(vsumSyncChangesPutRequest.getViewRequests());

    ViewSyncPlan viewSyncPlan = buildViewSyncPlan(vsum, desiredViewRequests);

    boolean hasAnyChanges =
        !toRemoveMetaModelRelation.isEmpty()
            || !toRemoveVsumMetaModelIds.isEmpty()
            || !toAddVsumMetaModelIds.isEmpty()
            || !renamedVsumMetaModels.isEmpty()
            || !toAddMetaModelRelation.isEmpty()
            || !toUpdateMetaModelRelation.isEmpty()
            || viewSyncPlan.hasChanges();

    MemoizedSupplier<Boolean> vsumHistorySaveSupplier =
        new MemoizedSupplier<>(
            () -> {
              if (createHistory) {
                vsumHistoryService.create(vsum, user);
              }
              return true;
            });

    if (hasAnyChanges) {
      vsumHistorySaveSupplier.get();
    }

    if (!toRemoveMetaModelRelation.isEmpty()) {
      List<MetaModelRelation> deletions =
          toRemoveMetaModelRelation.stream().map(existingByPair::get).toList();
      metaModelRelationService.delete(deletions);
      if (vsum.getMetaModelRelations() != null) {
        deletions.forEach(vsum.getMetaModelRelations()::remove);
      }
    }

    if (!toRemoveVsumMetaModelIds.isEmpty()) {
      List<VsumMetaModel> toDeleteVsumMetaModel =
          existingVsumMetaModel.stream()
              .filter(
                  vsumMetaModel ->
                      toRemoveVsumMetaModelIds.contains(
                          vsumMetaModel.getMetaModel().getSource().getId()))
              .toList();
      vsumMetaModelService.delete(vsum, toDeleteVsumMetaModel);
      if (vsum.getVsumMetaModels() != null) {
        toDeleteVsumMetaModel.forEach(vsum.getVsumMetaModels()::remove);
      }
    }

    if (!toAddVsumMetaModelIds.isEmpty()) {
      if (desiredMetaModelNames.isEmpty()) {
        vsumMetaModelService.create(vsum, toAddVsumMetaModelIds);
      } else {
        vsumMetaModelService.create(vsum, toAddVsumMetaModelIds, desiredMetaModelNames);
      }
    }

    if (!renamedVsumMetaModels.isEmpty()) {
      renamedVsumMetaModels.forEach(
          vsumMetaModel ->
              vsumMetaModel.setName(
                  desiredMetaModelNames.get(vsumMetaModel.getMetaModel().getSource().getId())));
      vsumMetaModelRepository.saveAll(renamedVsumMetaModels);
    }

    applyViewSyncPlan(vsum, viewSyncPlan);

    Map<MetaModelRelationRequest, MetaModelRelation> requestToRelation =
        new HashMap<>(toUpdateMetaModelRelation);

    if (!toAddMetaModelRelation.isEmpty()) {
      List<MetaModelRelationRequest> creations =
          desiredMetaModelRelation.stream()
              .filter(
                  request ->
                      toAddMetaModelRelation.contains(
                          request.getSourceId() + ":" + request.getTargetId()))
              .toList();
      Map<MetaModelRelationRequest, MetaModelRelation> created =
          metaModelRelationService.create(vsum, creations);
      if (created != null) {
        requestToRelation.putAll(created);
      }
    }

    if (!toUpdateMetaModelRelation.isEmpty()) {
      Map<MetaModelRelationRequest, MetaModelRelation> updated =
          metaModelRelationService.update(vsum, toUpdateMetaModelRelation);
      if (updated != null) {
        requestToRelation.putAll(updated);
      }
    }

    fineGranularMetaModelRelationService.update(
        user.getEmail(), requestToRelation, vsumHistorySaveSupplier);

    vsumRepository.save(vsum);
    return vsum;
  }

  private String projectMetaModelName(VsumMetaModel vsumMetaModel) {
    return vsumMetaModel.getName() == null
        ? vsumMetaModel.getMetaModel().getName()
        : vsumMetaModel.getName();
  }

  private List<MetaModelRelationRequest> normalizeMetaModelRelationRequests(
      List<MetaModelRelationRequest> requests) {
    if (requests == null) {
      return List.of();
    }

    return requests.stream()
        .filter(Objects::nonNull)
        .filter(request -> request.getSourceId() != null && request.getTargetId() != null)
        .toList();
  }

  private Map<Long, String> normalizeMetaModelNames(Map<Long, String> metaModelNames) {
    if (metaModelNames == null || metaModelNames.isEmpty()) {
      return Map.of();
    }

    return metaModelNames.entrySet().stream()
        .filter(entry -> entry.getKey() != null && entry.getValue() != null)
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private List<ViewRequest> normalizeViewRequests(List<ViewRequest> requests) {
    if (requests == null) {
      return List.of();
    }

    return requests.stream()
        .filter(Objects::nonNull)
        .map(this::normalizeViewRequest)
        .filter(request -> request.getFileStorageId() != null)
        .filter(request -> !request.getMetaModelIds().isEmpty())
        .toList();
  }

  private ViewRequest normalizeViewRequest(ViewRequest request) {
    List<Long> normalizedMetaModelIds =
        request.getMetaModelIds() == null
            ? List.of()
            : request.getMetaModelIds().stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();

    return ViewRequest.builder()
        .fileStorageId(request.getFileStorageId())
        .metaModelIds(normalizedMetaModelIds)
        .build();
  }

  private String toViewKey(ViewRequest request) {
    String metaModelPart =
        request.getMetaModelIds().stream().map(String::valueOf).collect(Collectors.joining(","));
    return request.getFileStorageId() + "|" + metaModelPart;
  }

  private String toViewKey(VsumView vsumView, List<VsumViewMetaModel> relations) {
    String metaModelPart =
        relations.stream()
            .map(VsumViewMetaModel::getMetaModel)
            .map(
                metaModel ->
                    metaModel.getSource() != null
                        ? metaModel.getSource().getId()
                        : metaModel.getId())
            .filter(Objects::nonNull)
            .distinct()
            .sorted()
            .map(String::valueOf)
            .collect(Collectors.joining(","));

    return vsumView.getFileStorage().getId() + "|" + metaModelPart;
  }

  private ViewSyncPlan buildViewSyncPlan(Vsum vsum, List<ViewRequest> desiredViewRequests) {
    List<VsumView> existingViews = vsumViewRepository.findAllByVsum(vsum);

    Map<Long, List<VsumViewMetaModel>> relationsByViewId = new HashMap<>();
    if (!existingViews.isEmpty()) {
      List<VsumViewMetaModel> allRelations =
          vsumViewMetaModelRepository.findAllByVsumViewIn(existingViews);
      for (VsumViewMetaModel relation : allRelations) {
        relationsByViewId
            .computeIfAbsent(relation.getVsumView().getId(), k -> new ArrayList<>())
            .add(relation);
      }
    }

    Map<String, VsumView> existingByKey = new HashMap<>();
    for (VsumView existingView : existingViews) {
      List<VsumViewMetaModel> viewRelations =
          relationsByViewId.getOrDefault(existingView.getId(), List.of());
      existingByKey.put(toViewKey(existingView, viewRelations), existingView);
    }

    Set<String> existingKeys = new HashSet<>(existingByKey.keySet());

    Set<String> desiredKeys =
        desiredViewRequests.stream().map(this::toViewKey).collect(Collectors.toSet());

    Set<String> toRemoveKeys = new HashSet<>(existingKeys);
    toRemoveKeys.removeAll(desiredKeys);

    Set<String> toAddKeys = new HashSet<>(desiredKeys);
    toAddKeys.removeAll(existingKeys);

    List<ViewRequest> toCreateRequests =
        desiredViewRequests.stream()
            .filter(request -> toAddKeys.contains(toViewKey(request)))
            .toList();

    List<VsumView> toDeleteViews = toRemoveKeys.stream().map(existingByKey::get).toList();

    return new ViewSyncPlan(toDeleteViews, toCreateRequests);
  }

  private void applyViewSyncPlan(Vsum vsum, ViewSyncPlan plan) {
    if (!plan.toDeleteViews().isEmpty()) {
      for (VsumView vsumView : plan.toDeleteViews()) {
        vsumViewMetaModelService.deleteByVsumView(vsumView);
        vsumViewService.delete(vsum, vsumView);
      }
    }

    if (!plan.toCreateRequests().isEmpty()) {
      for (ViewRequest request : plan.toCreateRequests()) {
        VsumView createdView = vsumViewService.create(vsum, request.getFileStorageId());
        vsumViewMetaModelService.create(createdView, new HashSet<>(request.getMetaModelIds()));
      }
    }
  }

  private record ViewSyncPlan(List<VsumView> toDeleteViews, List<ViewRequest> toCreateRequests) {
    private boolean hasChanges() {
      return !toDeleteViews.isEmpty() || !toCreateRequests.isEmpty();
    }
  }
}
