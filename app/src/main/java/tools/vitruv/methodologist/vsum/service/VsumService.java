package tools.vitruv.methodologist.vsum.service;

import static tools.vitruv.methodologist.messages.Error.REACTION_FILE_IDS_ID_NOT_FOUND_ERROR;
import static tools.vitruv.methodologist.messages.Error.USER_DOSE_NOT_HAVE_ACCESS;
import static tools.vitruv.methodologist.messages.Error.VSUM_ID_NOT_FOUND_ERROR;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.vitruv.methodologist.exception.NotFoundException;
import tools.vitruv.methodologist.exception.UnauthorizedException;
import tools.vitruv.methodologist.general.model.FileStorage;
import tools.vitruv.methodologist.user.model.User;
import tools.vitruv.methodologist.user.model.repository.UserRepository;
import tools.vitruv.methodologist.vsum.VsumRole;
import tools.vitruv.methodologist.vsum.controller.dto.request.MetaModelRelationRequest;
import tools.vitruv.methodologist.vsum.controller.dto.request.VsumPostRequest;
import tools.vitruv.methodologist.vsum.controller.dto.request.VsumPutRequest;
import tools.vitruv.methodologist.vsum.controller.dto.request.VsumSyncChangesPutRequest;
import tools.vitruv.methodologist.vsum.controller.dto.response.MetaModelRelationResponse;
import tools.vitruv.methodologist.vsum.controller.dto.response.MetaModelResponse;
import tools.vitruv.methodologist.vsum.controller.dto.response.VsumMetaModelResponse;
import tools.vitruv.methodologist.vsum.controller.dto.response.VsumResponse;
import tools.vitruv.methodologist.vsum.mapper.MetaModelMapper;
import tools.vitruv.methodologist.vsum.mapper.MetaModelRelationMapper;
import tools.vitruv.methodologist.vsum.mapper.VsumMapper;
import tools.vitruv.methodologist.vsum.model.MetaModelRelation;
import tools.vitruv.methodologist.vsum.model.Vsum;
import tools.vitruv.methodologist.vsum.model.VsumMetaModel;
import tools.vitruv.methodologist.vsum.model.VsumUser;
import tools.vitruv.methodologist.vsum.model.repository.MetaModelRelationRepository;
import tools.vitruv.methodologist.vsum.model.repository.VsumMetaModelRepository;
import tools.vitruv.methodologist.vsum.model.repository.VsumRepository;
import tools.vitruv.methodologist.vsum.model.repository.VsumUserRepository;

/**
 * Service class for managing VSUM (Virtual Single Underlying Model) operations. Handles the
 * business logic for VSUM creation, updates, retrieval, and removal while ensuring proper
 * validation and persistence.
 *
 * @see tools.vitruv.methodologist.vsum.model.Vsum
 * @see tools.vitruv.methodologist.vsum.model.repository.VsumRepository
 * @see tools.vitruv.methodologist.vsum.mapper.VsumMapper
 */
@Service
@Slf4j
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class VsumService {
  VsumMapper vsumMapper;
  VsumRepository vsumRepository;
  MetaModelMapper metaModelMapper;
  VsumMetaModelService vsumMetaModelService;
  UserRepository userRepository;
  VsumUserRepository vsumUserRepository;
  VsumUserService vsumUserService;
  MetaModelRelationService metaModelRelationService;
  MetaModelRelationMapper metaModelRelationMapper;
  VsumMetaModelRepository vsumMetaModelRepository;
  MetaModelRelationRepository metaModelRelationRepository;
  VsumHistoryService vsumHistoryService;
  MetaModelVitruvIntegrationService metaModelVitruvIntegrationService;

  /**
   * Creates a new VSUM with the specified details.
   *
   * @param vsumPostRequest DTO containing the VSUM creation details
   * @return the created Vsum entity
   */
  @Transactional
  public Vsum create(String callerEmail, VsumPostRequest vsumPostRequest) {
    User user =
        userRepository
            .findByEmailIgnoreCaseAndRemovedAtIsNull(callerEmail)
            .orElseThrow(UnauthorizedException::new);
    Vsum vsum = vsumMapper.toVsum(vsumPostRequest);
    vsum.setUser(user);
    vsum = vsumRepository.save(vsum);
    vsumUserService.create(vsum, user, VsumRole.OWNER);
    return vsum;
  }

  /**
   * Updates the specified VSUM entity owned by the caller with the provided request data.
   *
   * <p>Only mutable fields defined in {@link VsumPutRequest} are updated. The operation is
   * transactional. Throws {@link NotFoundException} if the VSUM does not exist, is removed, or does
   * not belong to the caller.
   *
   * @param callerEmail the authenticated user's email; must own the VSUM
   * @param id the identifier of the VSUM to update
   * @param vsumPutRequest the request containing updated VSUM fields
   * @return the updated {@link VsumResponse}
   * @throws tools.vitruv.methodologist.exception.NotFoundException if the VSUM is not found or
   *     unauthorized
   */
  @Transactional
  public VsumResponse update(String callerEmail, Long id, VsumPutRequest vsumPutRequest) {
    Vsum vsum =
        vsumRepository
            .findByIdAndUser_emailAndRemovedAtIsNull(id, callerEmail)
            .orElseThrow(() -> new NotFoundException(VSUM_ID_NOT_FOUND_ERROR));
    vsumMapper.updateByVsumPutRequest(vsumPutRequest, vsum);
    vsumRepository.save(vsum);
    return vsumMapper.toVsumResponse(vsum);
  }

  /**
   * Update a VSUM owned by the caller and synchronize its meta-models and relations to match the
   * provided {@link VsumSyncChangesPutRequest}.
   *
   * <p>The method verifies the caller's membership and throws a {@link NotFoundException} when the
   * VSUM or membership is not found. Actual synchronization and persistence are delegated to {@link
   * #applySyncChanges(Vsum, User, VsumSyncChangesPutRequest, boolean)}; when changes occur a
   * history entry may be created.
   *
   * @param callerEmail the authenticated user's email; must be a member of the VSUM
   * @param id the identifier of the VSUM to update
   * @param vsumSyncChangesPutRequest DTO describing desired meta-model ids and relation
   *     definitions; may be {@code null}
   * @return the persisted, updated {@link Vsum}
   * @throws NotFoundException if the VSUM or the caller's membership is not found
   */
  @Transactional
  public Vsum update(
      String callerEmail, Long id, VsumSyncChangesPutRequest vsumSyncChangesPutRequest) {
    VsumUser vsumUser =
        vsumUserRepository
            .findByVsum_IdAndUser_EmailAndUser_RemovedAtIsNullAndVsum_RemovedAtIsNull(
                id, callerEmail)
            .orElseThrow(() -> new NotFoundException(VSUM_ID_NOT_FOUND_ERROR));

    return applySyncChanges(
        vsumUser.getVsum(), vsumUser.getUser(), vsumSyncChangesPutRequest, true);
  }

  /**
   * Retrieves a VSUM by its ID.
   *
   * @param id the ID of the VSUM to retrieve
   * @return VsumResponse DTO containing the VSUM details
   * @throws tools.vitruv.methodologist.exception.NotFoundException if the VSUM ID is not found or
   *     is marked as removed
   */
  @Transactional
  public VsumResponse findById(String callerEmail, Long id) {
    Vsum vsum =
        vsumRepository
            .findByIdAndUser_emailAndRemovedAtIsNull(id, callerEmail)
            .orElseThrow(() -> new NotFoundException(VSUM_ID_NOT_FOUND_ERROR));
    return vsumMapper.toVsumResponse(vsum);
  }

  /**
   * Marks a VSUM as removed by setting its removal timestamp.
   *
   * @param id the ID of the VSUM to remove
   * @return the removed Vsum entity
   * @throws tools.vitruv.methodologist.exception.NotFoundException if the VSUM ID is not found or
   *     is marked as removed
   */
  @Transactional
  public Vsum remove(String callerEmail, Long id) {
    Vsum vsum =
        vsumRepository
            .findByIdAndUser_emailAndRemovedAtIsNull(id, callerEmail)
            .orElseThrow(() -> new NotFoundException(VSUM_ID_NOT_FOUND_ERROR));
    vsum.setRemovedAt(Instant.now());
    vsumRepository.save(vsum);
    return vsum;
  }

  /**
   * Fetches a VSUM owned by the caller and returns its details together with the mapped
   * meta-models. Throws {@code NotFoundException} if the VSUM does not exist or does not belong to
   * the caller.
   *
   * @param callerEmail the authenticated user's email (owner of the VSUM)
   * @param id the VSUM id to fetch
   * @return a response DTO with VSUM data and its meta-models
   * @throws NotFoundException if no matching VSUM is found
   */
  @Transactional(readOnly = true)
  public VsumMetaModelResponse findVsumWithDetails(String callerEmail, Long id) {
    User user =
        userRepository
            .findByEmailIgnoreCaseAndRemovedAtIsNull(callerEmail)
            .orElseThrow(() -> new AccessDeniedException(USER_DOSE_NOT_HAVE_ACCESS));

    Vsum vsum =
        vsumRepository
            .findByIdAndRemovedAtIsNull(id)
            .orElseThrow(() -> new NotFoundException(VSUM_ID_NOT_FOUND_ERROR));

    vsum.getVsumUsers().stream()
        .filter(vsumUser -> vsumUser.getUser().equals(user))
        .findFirst()
        .orElseThrow(() -> new AccessDeniedException(USER_DOSE_NOT_HAVE_ACCESS));

    VsumMetaModelResponse response = vsumMapper.toVsumMetaModelResponse(vsum);

    List<MetaModelResponse> metaModels =
        (vsum.getVsumMetaModels() == null
                ? List.<tools.vitruv.methodologist.vsum.model.VsumMetaModel>of()
                : vsum.getVsumMetaModels())
            .stream()
                .map(metaModel -> metaModelMapper.toMetaModelResponse(metaModel.getMetaModel()))
                .toList();
    response.setMetaModels(metaModels);

    List<MetaModelRelationResponse> metaModelRelation =
        (vsum.getMetaModelRelations() == null
                ? List.<MetaModelRelation>of()
                : vsum.getMetaModelRelations())
            .stream().map(metaModelRelationMapper::toMetaModelRelationResponse).toList();
    response.setMetaModelsRelation(metaModelRelation);

    return response;
  }

  /**
   * Retrieves all active VSUMs associated with the specified user, optionally filtered by VSUM
   * name, with pagination.
   *
   * <p>If {@code name} is provided and not blank, only VSUMs whose names contain the substring
   * (case-insensitive) are returned. Otherwise, all active VSUMs for the user are fetched.
   *
   * @param callerEmail the email of the user whose VSUMs to retrieve
   * @param name optional substring to filter VSUM names (case-insensitive)
   * @param pageable pagination information
   * @return list of {@link VsumResponse} DTOs representing the user's active VSUMs
   */
  @Transactional(readOnly = true)
  public List<VsumResponse> findAllByUser(String callerEmail, String name, Pageable pageable) {
    final boolean hasName = name != null && !name.isBlank();

    List<VsumUser> vsumUsers =
        hasName
            ? vsumUserRepository
                .findAllByUser_EmailAndVsum_NameContainingIgnoreCaseAndVsum_RemovedAtIsNull(
                    callerEmail, name, pageable)
            : vsumUserRepository.findAllByUser_EmailAndVsum_RemovedAtIsNull(callerEmail, pageable);

    Map<Long, VsumUser> vsumUserMap =
        vsumUsers.stream()
            .collect(Collectors.toMap(vsumUser -> vsumUser.getVsum().getId(), Function.identity()));
    Map<Long, VsumResponse> response =
        vsumUsers.stream()
            .map(VsumUser::getVsum)
            .map(vsumMapper::toVsumResponse)
            .collect(Collectors.toMap(VsumResponse::getId, Function.identity()));

    vsumUserMap
        .keySet()
        .forEach(aLong -> response.get(aLong).setRole(vsumUserMap.get(aLong).getRole()));
    return response.values().stream().toList();
  }

  /**
   * Retrieves removed VSUMs associated with the specified user and maps them to response DTOs.
   *
   * <p>Returns VSUMs where the related {@code Vsum.removedAt} is not {@code null}. Results are
   * fetched via {@code vsumUserRepository.findAllByUser_EmailAndVsum_RemovedAtIsNotNull(...)} and
   * mapped to {@link VsumResponse} instances using {@code vsumMapper}.
   *
   * @param callerEmail the email of the user whose removed VSUMs should be retrieved
   * @param pageable pagination information
   * @return a list of {@link VsumResponse} DTOs for the user's removed VSUMs
   */
  @Transactional(readOnly = true)
  public List<VsumResponse> findAllRemoved(String callerEmail, Pageable pageable) {
    List<VsumUser> vsumUsers =
        vsumUserRepository.findAllByUser_EmailAndVsum_RemovedAtIsNotNull(callerEmail, pageable);

    return vsumUsers.stream().map(VsumUser::getVsum).map(vsumMapper::toVsumResponse).toList();
  }

  /**
   * Scheduled task that deletes all {@link Vsum} entities marked as removed for over 30 days, along
   * with their associated user relationships, metamodels, and metamodel relations.
   *
   * <p>Runs daily at midnight.
   */
  @Transactional
  @Scheduled(cron = "0 0 0 * * ?")
  public void delete() {
    Instant cutoff = Instant.now().minus(30, ChronoUnit.DAYS);
    List<Vsum> oldVsums = vsumRepository.findAllByRemovedAtBefore(cutoff);

    oldVsums.forEach(
        vsum -> {
          vsumHistoryService.delete(vsum);
          vsumUserService.delete(vsum);
          vsumMetaModelService.delete(vsum);
          metaModelRelationService.deleteByVsum(vsum);
        });
  }

  /**
   * Restores a previously removed {@link Vsum} owned by the active user by clearing its removal
   * timestamp.
   *
   * @param callerEmail the email of the authenticated user requesting recovery
   * @param id the identifier of the removed {@link Vsum} to recover
   * @throws tools.vitruv.methodologist.exception.NotFoundException if no matching removed VSUM is
   *     found
   */
  @Transactional
  public void recovery(String callerEmail, Long id) {
    Vsum vsum =
        vsumRepository
            .findByIdAndUser_EmailAndUser_RemovedAtIsNullAndRemovedAtIsNotNull(id, callerEmail)
            .orElseThrow(() -> new NotFoundException(VSUM_ID_NOT_FOUND_ERROR));

    vsum.setRemovedAt(null);
    vsumRepository.save(vsum);
  }

  /**
   * Builds/synchronizes a VSUM via Vitruv-CLI.
   *
   * @param callerEmail authenticated user's email (must be member of the VSUM)
   * @param id VSUM identifier
   * @throws AccessDeniedException if caller is not a member of the VSUM
   * @throws NotFoundException if VSUM or its relations/files are not properly defined
   */
  public void buildOrThrow(String callerEmail, Long id) {
    VsumUser vsumUser =
        vsumUserRepository
            .findByVsum_IdAndUser_EmailAndUser_RemovedAtIsNullAndVsum_RemovedAtIsNull(
                id, callerEmail)
            .orElseThrow(() -> new AccessDeniedException(USER_DOSE_NOT_HAVE_ACCESS));

    Vsum vsum = vsumUser.getVsum();

    if (vsum.getMetaModelRelations() == null || vsum.getMetaModelRelations().isEmpty()) {
      throw new NotFoundException(REACTION_FILE_IDS_ID_NOT_FOUND_ERROR);
    }

    List<FileStorage> ecoreFiles = new java.util.ArrayList<>();
    List<FileStorage> genModelFiles = new java.util.ArrayList<>();
    List<FileStorage> reactionFiles = new java.util.ArrayList<>();

    for (MetaModelRelation relation : vsum.getMetaModelRelations()) {
      if (relation == null) {
        continue;
      }

      var source = relation.getSource();
      var target = relation.getTarget();
      var reaction = relation.getReactionFileStorage();

      if (reaction != null) {
        reactionFiles.add(reaction);
      }

      if (source != null && source.getEcoreFile() != null && source.getGenModelFile() != null) {
        ecoreFiles.add(source.getEcoreFile());
        genModelFiles.add(source.getGenModelFile());
      }

      if (target != null && target.getEcoreFile() != null && target.getGenModelFile() != null) {
        ecoreFiles.add(target.getEcoreFile());
        genModelFiles.add(target.getGenModelFile());
      }
    }

    if (ecoreFiles.isEmpty() || genModelFiles.isEmpty() || reactionFiles.isEmpty()) {
      throw new NotFoundException(REACTION_FILE_IDS_ID_NOT_FOUND_ERROR);
    }

    metaModelVitruvIntegrationService.runVitruvForMetaModels(
        ecoreFiles, genModelFiles, reactionFiles);
  }

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
  public Vsum applySyncChanges(
      Vsum vsum,
      User user,
      VsumSyncChangesPutRequest vsumSyncChangesPutRequest,
      boolean createHistory) {

    List<MetaModelRelationRequest> desiredMetaModelRelation =
        vsumSyncChangesPutRequest.getMetaModelRelationRequests() == null
            ? List.of()
            : vsumSyncChangesPutRequest.getMetaModelRelationRequests().stream()
                .filter(
                    metaModelRelationRequest ->
                        metaModelRelationRequest != null
                            && metaModelRelationRequest.getSourceId() != null
                            && metaModelRelationRequest.getTargetId() != null)
                .toList();

    List<MetaModelRelation> existingMetaModelRelation =
        metaModelRelationRepository.findAllByVsum(vsum);

    Set<String> desiredMetaModelRelationPairs =
        desiredMetaModelRelation.stream()
            .map(
                metaModelRelationRequest ->
                    metaModelRelationRequest.getSourceId()
                        + ":"
                        + metaModelRelationRequest.getTargetId())
            .collect(Collectors.toSet());

    Map<String, MetaModelRelation> existingByPair = new HashMap<>();
    for (MetaModelRelation metaModelRelation : existingMetaModelRelation) {
      String metaModelRelationPairKey =
          metaModelRelation.getSource().getSource().getId()
              + ":"
              + metaModelRelation.getTarget().getSource().getId();
      existingByPair.put(metaModelRelationPairKey, metaModelRelation);
    }

    Set<String> existingMetaModelRelationPairs = new HashSet<>(existingByPair.keySet());

    Set<String> toRemoveMetaModelRelation = new HashSet<>(existingMetaModelRelationPairs);
    toRemoveMetaModelRelation.removeAll(desiredMetaModelRelationPairs);

    List<Long> metaModelIds = vsumSyncChangesPutRequest.getMetaModelIds();
    List<VsumMetaModel> existingVsumMetaModel = vsumMetaModelRepository.findAllByVsum(vsum);

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

    Set<String> toAddMetaModelRelation = new HashSet<>(desiredMetaModelRelationPairs);
    toAddMetaModelRelation.removeAll(existingMetaModelRelationPairs);

    boolean hasAnyChanges =
        !toRemoveMetaModelRelation.isEmpty()
            || !toRemoveVsumMetaModelIds.isEmpty()
            || !toAddVsumMetaModelIds.isEmpty()
            || !toAddMetaModelRelation.isEmpty();

    if (createHistory && hasAnyChanges) {
      vsumHistoryService.create(vsum, user);
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
      vsumMetaModelService.create(vsum, toAddVsumMetaModelIds);
    }

    if (!toAddMetaModelRelation.isEmpty()) {
      List<MetaModelRelationRequest> creations =
          desiredMetaModelRelation.stream()
              .filter(
                  metaModelRelationRequest ->
                      toAddMetaModelRelation.contains(
                          metaModelRelationRequest.getSourceId()
                              + ":"
                              + metaModelRelationRequest.getTargetId()))
              .toList();
      metaModelRelationService.create(vsum, creations);
    }

    vsumRepository.save(vsum);
    return vsum;
  }
}
