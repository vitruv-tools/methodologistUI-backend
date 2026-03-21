package tools.vitruv.methodologist.vsum.service;

import static tools.vitruv.methodologist.messages.Error.USER_DOSE_NOT_HAVE_ACCESS;
import static tools.vitruv.methodologist.messages.Error.VSUM_HISTORY_ID_NOT_FOUND_ERROR;

import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.vitruv.methodologist.exception.NotFoundException;
import tools.vitruv.methodologist.user.model.User;
import tools.vitruv.methodologist.user.model.repository.UserRepository;
import tools.vitruv.methodologist.vsum.VsumRepresentation;
import tools.vitruv.methodologist.vsum.controller.dto.request.MetaModelRelationRequest;
import tools.vitruv.methodologist.vsum.controller.dto.request.VsumSyncChangesPutRequest;
import tools.vitruv.methodologist.vsum.controller.dto.response.VsumHistoryResponse;
import tools.vitruv.methodologist.vsum.mapper.VsumHistoryMapper;
import tools.vitruv.methodologist.vsum.model.Vsum;
import tools.vitruv.methodologist.vsum.model.VsumHistory;
import tools.vitruv.methodologist.vsum.model.repository.VsumHistoryRepository;
import tools.vitruv.methodologist.vsum.model.repository.VsumUserRepository;

/**
 * Application service that creates and persists VSUM history snapshots.
 *
 * <p>Builds a {@link VsumHistory} from a domain {@link Vsum} via {@link VsumHistoryMapper} and
 * stores it using {@link VsumHistoryRepository}.
 */
@Service
@Slf4j
public class VsumHistoryService {
  private final VsumHistoryRepository vsumHistoryRepository;
  private final VsumHistoryMapper vsumHistoryMapper;
  private final Long historyLimit;
  private final UserRepository userRepository;
  private final VsumUserRepository vsumUserRepository;
  private final VsumService vsumService;

  /**
   * Constructs a {@link VsumHistoryService} with required dependencies.
   *
   * @param vsumHistoryRepository repository for persisting history records
   * @param vsumHistoryMapper mapper for converting VSUM entities to representations
   * @param historyLimit maximum number of history snapshots to retain per VSUM
   */
  public VsumHistoryService(
      VsumHistoryRepository vsumHistoryRepository,
      VsumHistoryMapper vsumHistoryMapper,
      @Value("${vsum.history.limit}") Long historyLimit,
      UserRepository userRepository,
      VsumUserRepository vsumUserRepository,
      @Lazy VsumService vsumService) {
    this.vsumHistoryRepository = vsumHistoryRepository;
    this.vsumHistoryMapper = vsumHistoryMapper;
    this.historyLimit = historyLimit;
    this.userRepository = userRepository;
    this.vsumUserRepository = vsumUserRepository;
    this.vsumService = vsumService;
  }

  /**
   * Creates and persists a history snapshot for the given VSUM.
   *
   * <p>Constructs a JSON\-serializable representation using the mapper and saves the history record
   * within a transactional boundary.
   *
   * @param vsum the aggregate whose state is snapshotted; must not be {@code null}
   * @param creator the user who initiated the snapshot; must not be {@code null}
   * @return the persisted {@link VsumHistory} entity
   */
  public VsumHistory create(Vsum vsum, User creator) {
    long existsHistoryCount = vsumHistoryRepository.countByVsum(vsum);

    if (existsHistoryCount > historyLimit) {
      vsumHistoryRepository
          .findTopByVsumOrderByCreatedAtDesc(vsum)
          .ifPresent(vsumHistoryRepository::delete);
    }

    VsumHistory vsumHistory =
        VsumHistory.builder()
            .creator(creator)
            .vsum(vsum)
            .representation(vsumHistoryMapper.toVsumRepresentation(vsum))
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
   *   <li>Creates a new history snapshot for the current VSUM state (audit before revert).
   *   <li>Applies the recorded sync changes to the VSUM using {@link VsumService#applySyncChanges}.
   * </ol>
   *
   * @param callerEmail email of the user requesting the revert; used to validate access
   * @param id identifier of the history entry to revert to
   * @throws AccessDeniedException if the caller is not found or does not have access to the VSUM
   * @throws NotFoundException if the history entry with {@code id} does not exist
   */
  @Transactional
  public void revert(String callerEmail, Long id) {
    User user =
        userRepository
            .findByEmailIgnoreCaseAndRemovedAtIsNull(callerEmail)
            .orElseThrow(() -> new AccessDeniedException(USER_DOSE_NOT_HAVE_ACCESS));

    VsumHistory history =
        vsumHistoryRepository
            .findById(id)
            .orElseThrow(() -> new NotFoundException(VSUM_HISTORY_ID_NOT_FOUND_ERROR));

    Vsum vsum = history.getVsum();

    vsumUserRepository
        .findByVsum_IdAndUser_EmailAndUser_RemovedAtIsNullAndVsum_RemovedAtIsNull(
            vsum.getId(), callerEmail)
        .orElseThrow(() -> new AccessDeniedException(USER_DOSE_NOT_HAVE_ACCESS));

    create(vsum, user);

    VsumSyncChangesPutRequest vsumSyncChangesPutRequest =
        toSyncRequest(history.getRepresentation());
    vsumService.applySyncChanges(vsum, user, vsumSyncChangesPutRequest, false);
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
                    return metaModelRelationRequest;
                  })
              .toList();

      vsumSyncChangesPutRequest.setMetaModelRelationRequests(metaModelRelationRequests);
    }

    return vsumSyncChangesPutRequest;
  }
}
