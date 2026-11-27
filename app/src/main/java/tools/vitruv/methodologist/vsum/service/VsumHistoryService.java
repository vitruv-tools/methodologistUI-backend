package tools.vitruv.methodologist.vsum.service;

import static tools.vitruv.methodologist.messages.Error.USER_DOES_NOT_HAVE_ACCESS;
import static tools.vitruv.methodologist.messages.Error.VSUM_HISTORY_ID_NOT_FOUND_ERROR;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.vitruv.methodologist.exception.NotFoundException;
import tools.vitruv.methodologist.general.event.VsumChangedByUserEvent;
import tools.vitruv.methodologist.user.model.User;
import tools.vitruv.methodologist.user.model.repository.UserRepository;
import tools.vitruv.methodologist.vsum.VsumRepresentation;
import tools.vitruv.methodologist.vsum.controller.dto.request.FineGranularMetaModelRelationRequest;
import tools.vitruv.methodologist.vsum.controller.dto.request.MetaModelRelationRequest;
import tools.vitruv.methodologist.vsum.controller.dto.request.ViewRequest;
import tools.vitruv.methodologist.vsum.controller.dto.request.VsumSyncChangesPutRequest;
import tools.vitruv.methodologist.vsum.controller.dto.response.VsumHistoryResponse;
import tools.vitruv.methodologist.vsum.mapper.LowCodeReactionRequestMapper;
import tools.vitruv.methodologist.vsum.mapper.VsumHistoryMapper;
import tools.vitruv.methodologist.vsum.model.Vsum;
import tools.vitruv.methodologist.vsum.model.VsumHistory;
import tools.vitruv.methodologist.vsum.model.VsumUser;
import tools.vitruv.methodologist.vsum.model.repository.VsumHistoryRepository;
import tools.vitruv.methodologist.vsum.model.repository.VsumUserRepository;

/**
 * Application service that creates and persists VSUM history snapshots.
 *
 * <p>Builds a {@link VsumHistory} from a domain {@link Vsum} via {@link VsumHistoryMapper} and
 * stores it using {@link VsumHistoryRepository}. A snapshot holds the VSUM's meta models (by model
 * library ID, with their names in the VSUM), meta-model relations with their fine-granular
 * mappings, and views, from before a change, and reverting to it restores them. It holds neither
 * the layout nor the Ecore content of the VSUM's meta model copies, so a revert keeps both, and a
 * meta model it adds back is cloned from the model library again.
 */
@Service
@Slf4j
public class VsumHistoryService {
  private final VsumHistoryRepository vsumHistoryRepository;
  private final VsumHistoryMapper vsumHistoryMapper;
  private final Long historyLimit;
  private final Duration collaborationInterval;
  private final UserRepository userRepository;
  private final VsumUserRepository vsumUserRepository;
  private final VsumSyncService vsumSyncService;
  private final LowCodeReactionRequestMapper lowCodeReactionRequestMapper;
  private final ApplicationEventPublisher applicationEventPublisher;

  private final Clock clock;

  /**
   * Constructs a {@link VsumHistoryService} with required dependencies.
   *
   * @param vsumHistoryRepository repository for persisting history records
   * @param vsumHistoryMapper mapper for converting VSUM entities to representations
   * @param historyLimit number of history snapshots a VSUM may have before its oldest is deleted
   * @param collaborationInterval minimum age of a VSUM's latest snapshot taken before a writeback
   *     of the collaboration server, before the next writeback takes another one, see {@link
   *     #createBeforeCollaborationWriteback(Vsum)}
   * @param applicationEventPublisher publishes a {@link VsumChangedByUserEvent} after a revert
   */
  @Autowired
  public VsumHistoryService(
      VsumHistoryRepository vsumHistoryRepository,
      VsumHistoryMapper vsumHistoryMapper,
      @Value("${vsum.history.limit}") Long historyLimit,
      @Value("${vsum.history.collaboration-interval:5m}") Duration collaborationInterval,
      UserRepository userRepository,
      VsumUserRepository vsumUserRepository,
      @Lazy VsumSyncService vsumSyncService,
      LowCodeReactionRequestMapper lowCodeReactionRequestMapper,
      ApplicationEventPublisher applicationEventPublisher) {
    this(
        vsumHistoryRepository,
        vsumHistoryMapper,
        historyLimit,
        collaborationInterval,
        userRepository,
        vsumUserRepository,
        vsumSyncService,
        lowCodeReactionRequestMapper,
        applicationEventPublisher,
        Clock.systemUTC());
  }

  /**
   * Like the public constructor, with the clock that dates the snapshots and the writeback
   * interval, for tests.
   */
  VsumHistoryService(
      VsumHistoryRepository vsumHistoryRepository,
      VsumHistoryMapper vsumHistoryMapper,
      Long historyLimit,
      Duration collaborationInterval,
      UserRepository userRepository,
      VsumUserRepository vsumUserRepository,
      VsumSyncService vsumSyncService,
      LowCodeReactionRequestMapper lowCodeReactionRequestMapper,
      ApplicationEventPublisher applicationEventPublisher,
      Clock clock) {
    this.vsumHistoryRepository = vsumHistoryRepository;
    this.vsumHistoryMapper = vsumHistoryMapper;
    this.historyLimit = historyLimit;
    this.collaborationInterval = collaborationInterval;
    this.userRepository = userRepository;
    this.vsumUserRepository = vsumUserRepository;
    this.vsumSyncService = vsumSyncService;
    this.lowCodeReactionRequestMapper = lowCodeReactionRequestMapper;
    this.applicationEventPublisher = applicationEventPublisher;
    this.clock = clock;
  }

  /**
   * Creates and persists a history snapshot for the given VSUM, taken before a user's change.
   *
   * <p>Constructs a JSON\-serializable representation using the mapper and saves the history record
   * within a transactional boundary. If the VSUM has more snapshots than the history limit, its
   * oldest one is deleted first.
   *
   * @param vsum the aggregate whose state is snapshotted; must not be {@code null}
   * @param creator the user who initiated the snapshot; must not be {@code null}
   * @return the persisted {@link VsumHistory} entity
   */
  public VsumHistory create(Vsum vsum, User creator) {
    return save(vsum, creator, false, null);
  }

  /**
   * Snapshots the VSUM before a writeback of the collaboration server changes it, unless the VSUM's
   * latest snapshot was also taken before a writeback and is younger than {@code
   * vsum.history.collaboration-interval} (default 5 minutes).
   *
   * <p>The collaboration server writes back every few seconds while users edit, so a snapshot
   * before every writeback would quickly push the older ones out of the history limit. With the
   * interval, the writeback snapshots are at least that far apart. The changes between two of them
   * are recorded only together, even if users paused in between: a writeback within the interval
   * after the latest writeback snapshot takes none, also when it starts a new editing session. A
   * snapshot taken before a user's change or revert holds the state before that change, so the next
   * writeback always takes one, which holds the state right after it. The writeback's changes
   * cannot be attributed to one user, so the VSUM's owner is the snapshot's creator.
   *
   * @param vsum the VSUM the collaboration server is about to change
   */
  public void createBeforeCollaborationWriteback(Vsum vsum) {
    if (isCollaborationSnapshotDue(vsum)) {
      save(vsum, vsum.getUser(), true, null);
    }
  }

  private boolean isCollaborationSnapshotDue(Vsum vsum) {
    Instant intervalStart = clock.instant().minus(collaborationInterval);
    return vsumHistoryRepository
        .findTopByVsumAndCreatedAtNotNullOrderByCreatedAtDescIdDesc(vsum)
        .map(
            latest ->
                !latest.isCollaborationWriteback() || !latest.getCreatedAt().isAfter(intervalStart))
        .orElse(true);
  }

  /**
   * Stores a snapshot of the VSUM's current state, dated by the clock. If the VSUM has more
   * snapshots than the history limit, its oldest one other than {@code keptId} is deleted first.
   */
  private VsumHistory save(Vsum vsum, User creator, boolean collaborationWriteback, Long keptId) {
    long existsHistoryCount = vsumHistoryRepository.countByVsum(vsum);

    if (existsHistoryCount > historyLimit) {
      (keptId == null
              ? vsumHistoryRepository.findTopByVsumOrderByCreatedAtAscIdAsc(vsum)
              : vsumHistoryRepository.findTopByVsumAndIdNotOrderByCreatedAtAscIdAsc(vsum, keptId))
          .ifPresent(vsumHistoryRepository::delete);
    }

    VsumHistory vsumHistory =
        VsumHistory.builder()
            .creator(creator)
            .vsum(vsum)
            .representation(vsumHistoryMapper.toVsumRepresentation(vsum))
            .collaborationWriteback(collaborationWriteback)
            .createdAt(clock.instant())
            .build();
    vsumHistoryRepository.save(vsumHistory);

    return vsumHistory;
  }

  /**
   * Deletes all {@link VsumHistory} records associated with the specified {@link Vsum}.
   *
   * @param vsum the VSUM whose history records should be deleted
   */
  public void delete(Vsum vsum) {
    vsumHistoryRepository.deleteVsumHistoryByVsum(vsum);
  }

  /**
   * Retrieves VSUM history snapshots for the specified VSUM id and caller email, filters out
   * records for removed users or removed VSUMs, orders results by {@code createdAt} ascending, and
   * maps each entity to a {@link VsumHistoryResponse} DTO.
   *
   * @param callerEmail the email address of the VSUM owner used to filter history records; must not
   *     be {@code null}
   * @param vsumId the VSUM id to filter history records by; must not be {@code null}
   * @return a list of {@link VsumHistoryResponse} ordered by {@code createdAt} ascending; never
   *     {@code null} (may be empty)
   */
  public List<VsumHistoryResponse> findAllByVsumId(String callerEmail, Long vsumId) {
    List<VsumHistory> vsumHistories = vsumHistoryRepository.getVsumHistories(vsumId, callerEmail);
    return vsumHistories.stream().map(vsumHistoryMapper::toVsumHistoryResponse).toList();
  }

  /**
   * Reverts the specified VSUM to the state captured by the given history entry.
   *
   * <p>This operation performs the following steps:
   *
   * <ol>
   *   <li>Verifies the caller exists and is active.
   *   <li>Loads the {@link VsumHistory} identified by {@code id}.
   *   <li>Verifies that the caller has access to the VSUM referenced by the history entry.
   *   <li>Creates a new history snapshot for the current VSUM state (audit before revert). If the
   *       VSUM has too many snapshots, the oldest one other than the given entry is deleted, so the
   *       state reverted to stays in the history.
   *   <li>Applies the recorded sync changes to the VSUM using {@link
   *       VsumSyncService#applySyncChanges}.
   *   <li>Publishes a {@link VsumChangedByUserEvent}, so the collaboration server reloads the VSUM
   *       once the revert is committed.
   * </ol>
   *
   * @param callerEmail email of the user requesting the revert; used to validate access
   * @param id identifier of the history entry to revert to
   * @throws AccessDeniedException if the caller is not found, is not a member of the VSUM, or is a
   *     viewer
   * @throws NotFoundException if the history entry with {@code id} does not exist
   */
  @Transactional
  public void revert(String callerEmail, Long id) {
    User user =
        userRepository
            .findByEmailIgnoreCaseAndRemovedAtIsNull(callerEmail)
            .orElseThrow(() -> new AccessDeniedException(USER_DOES_NOT_HAVE_ACCESS));

    VsumHistory history =
        vsumHistoryRepository
            .findById(id)
            .orElseThrow(() -> new NotFoundException(VSUM_HISTORY_ID_NOT_FOUND_ERROR));

    Vsum vsum = history.getVsum();

    VsumUser membership =
        vsumUserRepository
            .findByVsum_IdAndUser_EmailAndUser_RemovedAtIsNullAndVsum_RemovedAtIsNull(
                vsum.getId(), callerEmail)
            .orElseThrow(() -> new AccessDeniedException(USER_DOES_NOT_HAVE_ACCESS));
    if (!membership.getRole().canWrite()) {
      throw new AccessDeniedException(USER_DOES_NOT_HAVE_ACCESS);
    }

    save(vsum, user, false, history.getId());

    VsumSyncChangesPutRequest vsumSyncChangesPutRequest =
        toSyncRequest(history.getRepresentation());
    vsumSyncService.applySyncChanges(vsum, user, vsumSyncChangesPutRequest, false);
    applicationEventPublisher.publishEvent(new VsumChangedByUserEvent(vsum.getId()));
  }

  /**
   * Converts a persisted {@link VsumRepresentation} back into a mutable {@link
   * VsumSyncChangesPutRequest} used by the service to apply sync changes.
   *
   * <p>The method safely handles null collections: if the representation contains no meta model ids
   * or relations the corresponding request lists are set to empty lists. Each relation entry is
   * mapped to a {@link MetaModelRelationRequest} with {@code sourceId}, {@code targetId} and {@code
   * reactionFileId} populated from the representation.
   *
   * @param representation the persisted VSUM snapshot; may be {@code null}
   * @return a populated {@link VsumSyncChangesPutRequest}; never {@code null}
   */
  private VsumSyncChangesPutRequest toSyncRequest(VsumRepresentation representation) {
    VsumSyncChangesPutRequest vsumSyncChangesPutRequest = new VsumSyncChangesPutRequest();

    vsumSyncChangesPutRequest.setMetaModelIds(
        representation.getMetaModels() == null
            ? List.of()
            : List.copyOf(representation.getMetaModels()));
    vsumSyncChangesPutRequest.setMetaModelNames(
        representation.getMetaModelNames() == null
            ? Map.of()
            : new HashMap<>(representation.getMetaModelNames()));

    if (representation.getMetaModelsRealation() == null) {
      vsumSyncChangesPutRequest.setMetaModelRelationRequests(List.of());
    } else {
      List<MetaModelRelationRequest> metaModelRelationRequests =
          representation.getMetaModelsRealation().stream()
              .filter(Objects::nonNull)
              .map(
                  metaModelRelation -> {
                    MetaModelRelationRequest metaModelRelationRequest =
                        new MetaModelRelationRequest();
                    metaModelRelationRequest.setSourceId(metaModelRelation.getSourceId());
                    metaModelRelationRequest.setTargetId(metaModelRelation.getTargetId());
                    metaModelRelationRequest.setReactionFileId(
                        metaModelRelation.getRelationFileStorage());
                    metaModelRelationRequest.setFineGranularMetaModelRelationSet(
                        toFineGranularRequests(metaModelRelation));
                    return metaModelRelationRequest;
                  })
              .toList();

      vsumSyncChangesPutRequest.setMetaModelRelationRequests(metaModelRelationRequests);
    }

    if (representation.getViews() == null) {
      vsumSyncChangesPutRequest.setViewRequests(List.of());
    } else {
      List<ViewRequest> viewRequests =
          representation.getViews().stream()
              .filter(Objects::nonNull)
              .map(
                  view ->
                      ViewRequest.builder()
                          .id(view.getId())
                          .name(view.getName())
                          // A snapshot is the full state: a view without description has none.
                          // Snapshots from before views had names have no descriptions at all,
                          // so their views keep the current one, like their name.
                          .description(
                              view.getName() == null
                                  ? null
                                  : Objects.requireNonNullElse(view.getDescription(), ""))
                          .fileStorageId(view.getFileStorageId())
                          .metaModelIds(
                              view.getMetaModelIds() == null
                                  ? List.of()
                                  : List.copyOf(view.getMetaModelIds()))
                          .build())
              .toList();
      vsumSyncChangesPutRequest.setViewRequests(viewRequests);
    }

    return vsumSyncChangesPutRequest;
  }

  private Set<FineGranularMetaModelRelationRequest> toFineGranularRequests(
      VsumRepresentation.MetaModelRelation metaModelRelation) {
    if (metaModelRelation.getFineGranularMetaModelRelationSet() == null
        || metaModelRelation.getFineGranularMetaModelRelationSet().isEmpty()) {
      return new HashSet<>();
    }
    Set<FineGranularMetaModelRelationRequest> requests = new HashSet<>();
    for (VsumRepresentation.FineGranularMetaModelRelation fg :
        metaModelRelation.getFineGranularMetaModelRelationSet()) {
      if (fg == null) {
        continue;
      }
      requests.add(
          FineGranularMetaModelRelationRequest.builder()
              .sourceId(fg.getSourceId())
              .targetId(fg.getTargetId())
              .reactionFileStorageId(fg.getReactionFileStorageId())
              .lowCodeReactionRequestBase(
                  lowCodeReactionRequestMapper.map(
                      fg.getLowCodeReactionTemplate(), fg.getLowCodeReactionTemplateParams()))
              .build());
    }
    return requests;
  }
}
