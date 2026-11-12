package tools.vitruv.methodologist.vsum.service;

import static tools.vitruv.methodologist.general.service.FileStorageService.NEO_JOIN_EXTENSION;
import static tools.vitruv.methodologist.messages.Error.LAYOUT_MUST_BE_JSON_OBJECT_ERROR;
import static tools.vitruv.methodologist.messages.Error.LAYOUT_TOO_LARGE_ERROR;
import static tools.vitruv.methodologist.messages.Error.VIEW_FILE_REQUIRED_ERROR;
import static tools.vitruv.methodologist.messages.Error.VIEW_ID_NOT_UNIQUE_ERROR;
import static tools.vitruv.methodologist.messages.Error.VIEW_NAME_NOT_UNIQUE_ERROR;
import static tools.vitruv.methodologist.messages.Error.VIEW_NAME_TOO_LONG_ERROR;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.ws.rs.BadRequestException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.vitruv.methodologist.exception.InvalidLayoutException;
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

  static final int MAX_LAYOUT_BYTES = 1024 * 1024;

  /** Maximum length of a view name, the size of its column. */
  private static final int MAX_VIEW_NAME_LENGTH = 255;

  /** Name of a new view whose file name gives none. */
  private static final String DEFAULT_VIEW_NAME = "view";

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

    Optional<JsonNode> layoutToStore = validatedLayout(vsumSyncChangesPutRequest);

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
        normalizeViewRequests(vsumSyncChangesPutRequest.getViewRequests(), desiredMetaModelIds);

    ViewSyncPlan viewSyncPlan = buildViewSyncPlan(vsum, desiredViewRequests, desiredMetaModelIds);

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

    removeViews(vsum, viewSyncPlan);

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

    addViews(vsum, viewSyncPlan);

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

    layoutToStore.ifPresent(vsum::setLayout);

    vsumRepository.save(vsum);
    return vsum;
  }

  /**
   * Returns the layout to store, or empty if the request carries none (missing or JSON {@code
   * null}), in which case the stored layout is kept.
   *
   * @throws InvalidLayoutException if the layout is not a JSON object or is larger than 1 MB
   */
  private Optional<JsonNode> validatedLayout(VsumSyncChangesPutRequest request) {
    JsonNode layout = request.getLayout();
    if (layout == null || layout.isNull()) {
      return Optional.empty();
    }
    if (!layout.isObject()) {
      throw new InvalidLayoutException(LAYOUT_MUST_BE_JSON_OBJECT_ERROR);
    }
    if (layout.toString().getBytes(StandardCharsets.UTF_8).length > MAX_LAYOUT_BYTES) {
      throw new InvalidLayoutException(LAYOUT_TOO_LARGE_ERROR);
    }
    return Optional.of(layout);
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

  /**
   * Normalizes the view requests. A request that names a view (by ID or name) must have a file, a
   * request without either is dropped if it lacks a file or meta models, as before views had names.
   * The meta model IDs of a request are reduced to those the VSUM keeps: a view can only be linked
   * to meta models of its VSUM.
   */
  private List<ViewRequest> normalizeViewRequests(
      List<ViewRequest> requests, Set<Long> desiredMetaModelIds) {
    if (requests == null) {
      return List.of();
    }

    return requests.stream()
        .filter(Objects::nonNull)
        .map(request -> normalizeViewRequest(request, desiredMetaModelIds))
        .filter(Objects::nonNull)
        .toList();
  }

  /**
   * Returns the normalized request, or {@code null} if it has neither ID nor name and lacks a file
   * or meta models.
   *
   * @throws BadRequestException if it has an ID or a name but no file
   */
  private static ViewRequest normalizeViewRequest(
      ViewRequest request, Set<Long> desiredMetaModelIds) {
    List<Long> metaModelIds =
        request.getMetaModelIds() == null
            ? List.of()
            : request.getMetaModelIds().stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
    String name = blankToNull(request.getName());
    boolean identified = request.getId() != null || name != null;
    if (identified && request.getFileStorageId() == null) {
      throw new BadRequestException(
          String.format(VIEW_FILE_REQUIRED_ERROR, name != null ? name : request.getId()));
    }
    if (!identified && (request.getFileStorageId() == null || metaModelIds.isEmpty())) {
      return null;
    }
    return ViewRequest.builder()
        .id(request.getId())
        .name(name)
        .description(normalizeDescription(request.getDescription()))
        .fileStorageId(request.getFileStorageId())
        .metaModelIds(metaModelIds.stream().filter(desiredMetaModelIds::contains).toList())
        .build();
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }

  /** Keeps {@code null} (the description stays as it is), and turns a blank one into "". */
  private static String normalizeDescription(String description) {
    return description == null ? null : description.strip();
  }

  /**
   * Plans how the views of the VSUM become the desired ones. Each request is matched to an existing
   * view, which keeps its ID and is changed in place: first by ID, then by name, and a request
   * without a name by its file and meta models (the matching of requests from before views had
   * names). Requests that match no view create one; views that no request matches are deleted.
   */
  private ViewSyncPlan buildViewSyncPlan(
      Vsum vsum, List<ViewRequest> desiredViewRequests, Set<Long> desiredMetaModelIds) {
    List<VsumView> existingViews = vsumViewRepository.findAllByVsum(vsum);

    Map<Long, List<VsumViewMetaModel>> linksByViewId = new HashMap<>();
    if (!existingViews.isEmpty()) {
      List<VsumViewMetaModel> allLinks =
          vsumViewMetaModelRepository.findAllByVsumViewIn(existingViews);
      for (VsumViewMetaModel link : allLinks) {
        linksByViewId.computeIfAbsent(link.getVsumView().getId(), k -> new ArrayList<>()).add(link);
      }
    }

    List<VsumView> matches =
        matchViews(desiredViewRequests, existingViews, linksByViewId, desiredMetaModelIds);
    List<String> names = resolveViewNames(desiredViewRequests, matches);

    List<ViewUpdate> toUpdateViews = new ArrayList<>();
    List<ViewCreation> toCreateViews = new ArrayList<>();
    Set<Long> matchedIds = new HashSet<>();
    for (int i = 0; i < desiredViewRequests.size(); i++) {
      ViewRequest request = desiredViewRequests.get(i);
      VsumView match = matches.get(i);
      if (match == null) {
        toCreateViews.add(
            new ViewCreation(
                request.getFileStorageId(),
                names.get(i),
                resolveDescription(request, null),
                Set.copyOf(request.getMetaModelIds())));
      } else {
        matchedIds.add(match.getId());
        ViewUpdate update =
            planUpdate(
                match, linksByViewId.getOrDefault(match.getId(), List.of()), request, names.get(i));
        if (update != null) {
          toUpdateViews.add(update);
        }
      }
    }

    List<VsumView> toDeleteViews =
        existingViews.stream().filter(view -> !matchedIds.contains(view.getId())).toList();

    return new ViewSyncPlan(toDeleteViews, toUpdateViews, toCreateViews);
  }

  /** Returns the changes of an existing view, {@code null} if the request describes it as it is. */
  private static ViewUpdate planUpdate(
      VsumView view, List<VsumViewMetaModel> links, ViewRequest request, String name) {
    Set<Long> desiredIds = Set.copyOf(request.getMetaModelIds());
    Set<Long> keptIds = new HashSet<>();
    List<VsumViewMetaModel> removedLinks = new ArrayList<>();
    for (VsumViewMetaModel link : links) {
      Long sourceId = VsumViewMetaModelService.sourceMetaModelId(link);
      if (!desiredIds.contains(sourceId) || !keptIds.add(sourceId)) {
        removedLinks.add(link);
      }
    }
    Set<Long> addedIds = new HashSet<>(desiredIds);
    addedIds.removeAll(keptIds);

    String description = resolveDescription(request, view);
    boolean unchanged =
        removedLinks.isEmpty()
            && addedIds.isEmpty()
            && Objects.equals(view.getName(), name)
            && Objects.equals(view.getDescription(), description)
            && view.getFileStorage() != null
            && Objects.equals(view.getFileStorage().getId(), request.getFileStorageId());
    return unchanged
        ? null
        : new ViewUpdate(
            view, request.getFileStorageId(), name, description, removedLinks, addedIds);
  }

  /**
   * Returns the description a view gets: the requested one, none if it is blank, or the current one
   * of the view if the request has none.
   */
  private static String resolveDescription(ViewRequest request, VsumView view) {
    if (request.getDescription() == null) {
      return view == null ? null : view.getDescription();
    }
    return request.getDescription().isEmpty() ? null : request.getDescription();
  }

  /**
   * Returns the existing view each request describes, {@code null} for a new view. A request
   * without a name matches a view with its file whose links to the meta models the VSUM keeps are
   * the request's meta models.
   */
  private List<VsumView> matchViews(
      List<ViewRequest> requests,
      List<VsumView> existingViews,
      Map<Long, List<VsumViewMetaModel>> linksByViewId,
      Set<Long> desiredMetaModelIds) {
    List<VsumView> matches = new ArrayList<>(Collections.nCopies(requests.size(), null));
    Map<Long, VsumView> unmatched = new LinkedHashMap<>();
    existingViews.forEach(view -> unmatched.put(view.getId(), view));

    Set<Long> requestedIds = new HashSet<>();
    for (int i = 0; i < requests.size(); i++) {
      Long id = requests.get(i).getId();
      if (id != null && !requestedIds.add(id)) {
        throw new BadRequestException(String.format(VIEW_ID_NOT_UNIQUE_ERROR, id));
      }
      if (id != null) {
        matches.set(i, unmatched.remove(id));
      }
    }

    matchRemaining(
        requests,
        matches,
        unmatched,
        (request, view) -> request.getName() != null && request.getName().equals(view.getName()));
    matchRemaining(
        requests,
        matches,
        unmatched,
        (request, view) ->
            request.getName() == null
                && hasFileAndMetaModels(
                    view,
                    linksByViewId.getOrDefault(view.getId(), List.of()),
                    request,
                    desiredMetaModelIds));
    return matches;
  }

  /** Matches each request without a view to the first unmatched view that fits it. */
  private static void matchRemaining(
      List<ViewRequest> requests,
      List<VsumView> matches,
      Map<Long, VsumView> unmatched,
      BiPredicate<ViewRequest, VsumView> fits) {
    for (int i = 0; i < requests.size(); i++) {
      ViewRequest request = requests.get(i);
      if (matches.get(i) != null) {
        continue;
      }
      VsumView view =
          unmatched.values().stream().filter(v -> fits.test(request, v)).findFirst().orElse(null);
      if (view != null) {
        matches.set(i, unmatched.remove(view.getId()));
      }
    }
  }

  /**
   * Returns the name each requested view gets: the requested name, or else the current name of the
   * matched view, or else, for a new view, the name of its file without the extension, numbered if
   * that name is taken.
   *
   * @throws BadRequestException if a name is too long or two views would get the same name
   */
  private List<String> resolveViewNames(List<ViewRequest> requests, List<VsumView> matches) {
    List<String> names = new ArrayList<>(Collections.nCopies(requests.size(), null));
    Set<String> usedNames = new HashSet<>();
    for (int i = 0; i < requests.size(); i++) {
      String requested = requests.get(i).getName();
      if (requested != null && length(requested) > MAX_VIEW_NAME_LENGTH) {
        throw new BadRequestException(
            String.format(VIEW_NAME_TOO_LONG_ERROR, MAX_VIEW_NAME_LENGTH));
      }
      String name =
          requested != null || matches.get(i) == null ? requested : matches.get(i).getName();
      if (name != null && !usedNames.add(name)) {
        throw new BadRequestException(String.format(VIEW_NAME_NOT_UNIQUE_ERROR, name));
      }
      names.set(i, name);
    }

    for (int i = 0; i < requests.size(); i++) {
      if (names.get(i) == null && matches.get(i) == null) {
        String name = nameOfNewView(requests.get(i).getFileStorageId(), usedNames);
        usedNames.add(name);
        names.set(i, name);
      }
    }
    return names;
  }

  private String nameOfNewView(Long fileStorageId, Set<String> usedNames) {
    String fileName = vsumViewService.findNeoJoinFile(fileStorageId).getFilename();
    String base = fileName == null ? "" : fileName.strip();
    if (base.endsWith(NEO_JOIN_EXTENSION)) {
      base = base.substring(0, base.length() - NEO_JOIN_EXTENSION.length()).strip();
    }
    if (base.isEmpty()) {
      base = DEFAULT_VIEW_NAME;
    }

    String name = withSuffix(base, "");
    for (int counter = 2; usedNames.contains(name); counter++) {
      name = withSuffix(base, " (" + counter + ")");
    }
    return name;
  }

  /** Appends the suffix, shortening the base so that the result fits into a view name. */
  private static String withSuffix(String base, String suffix) {
    int room = MAX_VIEW_NAME_LENGTH - length(suffix);
    String fitted =
        length(base) > room ? base.substring(0, base.offsetByCodePoints(0, room)) : base;
    return fitted + suffix;
  }

  private static int length(String value) {
    return value.codePointCount(0, value.length());
  }

  private static boolean hasFileAndMetaModels(
      VsumView view,
      List<VsumViewMetaModel> links,
      ViewRequest request,
      Set<Long> desiredMetaModelIds) {
    return view.getFileStorage() != null
        && Objects.equals(view.getFileStorage().getId(), request.getFileStorageId())
        && links.stream()
            .map(VsumViewMetaModelService::sourceMetaModelId)
            .filter(desiredMetaModelIds::contains)
            .collect(Collectors.toSet())
            .equals(Set.copyOf(request.getMetaModelIds()));
  }

  /**
   * Deletes the views no request describes and the links to meta models views lose. Runs before the
   * VSUM's meta models are deleted, whose rows the links reference.
   */
  private void removeViews(Vsum vsum, ViewSyncPlan plan) {
    for (VsumView vsumView : plan.toDeleteViews()) {
      vsumViewMetaModelService.deleteByVsumView(vsumView);
      vsumViewService.delete(vsum, vsumView);
    }
    for (ViewUpdate update : plan.toUpdateViews()) {
      if (!update.removedLinks().isEmpty()) {
        vsumViewMetaModelService.delete(update.removedLinks());
      }
    }
  }

  /**
   * Changes the kept views, links them to the meta models they gain and creates the new views. Runs
   * after the VSUM's new meta models are added, which the links reference.
   */
  private void addViews(Vsum vsum, ViewSyncPlan plan) {
    for (ViewUpdate update : plan.toUpdateViews()) {
      vsumViewService.update(
          update.view(), update.fileStorageId(), update.name(), update.description());
      if (!update.addedMetaModelIds().isEmpty()) {
        vsumViewMetaModelService.create(update.view(), update.addedMetaModelIds());
      }
    }

    for (ViewCreation creation : plan.toCreateViews()) {
      VsumView createdView =
          vsumViewService.create(
              vsum, creation.fileStorageId(), creation.name(), creation.description());
      vsumViewMetaModelService.create(createdView, creation.metaModelIds());
    }
  }

  /**
   * An existing view that changes: its new file, name and description, the links it loses and the
   * source IDs of the meta models it gains.
   */
  private record ViewUpdate(
      VsumView view,
      Long fileStorageId,
      String name,
      String description,
      List<VsumViewMetaModel> removedLinks,
      Set<Long> addedMetaModelIds) {}

  /** A view to create, with the source IDs of its meta models. */
  private record ViewCreation(
      Long fileStorageId, String name, String description, Set<Long> metaModelIds) {}

  private record ViewSyncPlan(
      List<VsumView> toDeleteViews,
      List<ViewUpdate> toUpdateViews,
      List<ViewCreation> toCreateViews) {
    private boolean hasChanges() {
      return !toDeleteViews.isEmpty() || !toUpdateViews.isEmpty() || !toCreateViews.isEmpty();
    }
  }
}
