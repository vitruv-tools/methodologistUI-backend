package tools.vitruv.methodologist.vsum.service;

import static tools.vitruv.methodologist.messages.Error.METAMODEL_IDS_NOT_FOUND_IN_THIS_VSUM_NOT_FOUND_ERROR;
import static tools.vitruv.methodologist.messages.Error.USER_DOES_NOT_HAVE_ACCESS;
import static tools.vitruv.methodologist.messages.Error.VSUM_ID_NOT_FOUND_ERROR;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.vitruv.methodologist.exception.NotFoundException;
import tools.vitruv.methodologist.exception.UnauthorizedException;
import tools.vitruv.methodologist.user.model.User;
import tools.vitruv.methodologist.user.model.repository.UserRepository;
import tools.vitruv.methodologist.vsum.VsumRole;
import tools.vitruv.methodologist.vsum.controller.dto.request.VsumPostRequest;
import tools.vitruv.methodologist.vsum.controller.dto.request.VsumPutRequest;
import tools.vitruv.methodologist.vsum.controller.dto.request.VsumSyncChangesPutRequest;
import tools.vitruv.methodologist.vsum.controller.dto.response.MetaModelRelationResponse;
import tools.vitruv.methodologist.vsum.controller.dto.response.MetaModelResponse;
import tools.vitruv.methodologist.vsum.controller.dto.response.ViewsResponse;
import tools.vitruv.methodologist.vsum.controller.dto.response.VsumMetaModelResponse;
import tools.vitruv.methodologist.vsum.controller.dto.response.VsumResponse;
import tools.vitruv.methodologist.vsum.mapper.LowCodeReactionRequestMapper;
import tools.vitruv.methodologist.vsum.mapper.MetaModelMapper;
import tools.vitruv.methodologist.vsum.mapper.MetaModelRelationMapper;
import tools.vitruv.methodologist.vsum.mapper.VsumMapper;
import tools.vitruv.methodologist.vsum.mapper.VsumViewMapper;
import tools.vitruv.methodologist.vsum.model.MetaModel;
import tools.vitruv.methodologist.vsum.model.MetaModelRelation;
import tools.vitruv.methodologist.vsum.model.Vsum;
import tools.vitruv.methodologist.vsum.model.VsumMetaModel;
import tools.vitruv.methodologist.vsum.model.VsumUser;
import tools.vitruv.methodologist.vsum.model.repository.VsumMetaModelRepository;
import tools.vitruv.methodologist.vsum.model.repository.VsumRepository;
import tools.vitruv.methodologist.vsum.model.repository.VsumUserRepository;
import tools.vitruv.methodologist.vsum.model.repository.VsumViewRepository;

/**
 * Service class for managing VSUM (Virtual Single Underlying Model) operations. Handles the
 * business logic for VSUM creation, updates, retrieval, and removal while ensuring proper
 * validation and persistence.
 *
 * <p>Synchronizing the contents of a VSUM, packaging deployment bundles and permanently deleting
 * VSUMs are handled by {@link VsumSyncService}, {@link VsumDeploymentBundleService} and {@link
 * VsumCleanupService}.
 *
 * @see tools.vitruv.methodologist.vsum.model.Vsum
 * @see tools.vitruv.methodologist.vsum.model.repository.VsumRepository
 * @see tools.vitruv.methodologist.vsum.mapper.VsumMapper
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class VsumService {

  VsumMapper vsumMapper;
  VsumRepository vsumRepository;
  MetaModelMapper metaModelMapper;
  UserRepository userRepository;
  VsumUserRepository vsumUserRepository;
  VsumUserService vsumUserService;
  MetaModelRelationMapper metaModelRelationMapper;
  VsumMetaModelRepository vsumMetaModelRepository;
  VsumHistoryService vsumHistoryService;
  private final VsumViewRepository vsumViewRepository;
  private final VsumViewMapper vsumViewMapper;
  LowCodeReactionRequestMapper lowCodeReactionRequestMapper;
  VsumBuildService vsumBuildService;
  MetaModelService metaModelService;
  VsumSyncService vsumSyncService;
  VsumCleanupService vsumCleanupService;

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
   * VsumSyncService#applySyncChanges(Vsum, User, VsumSyncChangesPutRequest, boolean)}; when changes
   * occur a history entry may be created.
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

    if (vsumUser.getRole() == VsumRole.VIEWER) {
      throw new AccessDeniedException(USER_DOES_NOT_HAVE_ACCESS);
    }

    return vsumSyncService.applySyncChanges(
        vsumUser.getVsum(), vsumUser.getUser(), vsumSyncChangesPutRequest, true);
  }

  /**
   * Updates the name of a meta model within a VSUM without changing the corresponding model-library
   * entry.
   *
   * @param callerEmail the email of the VSUM member requesting the change
   * @param vsumId the VSUM containing the meta model
   * @param sourceMetaModelId the ID of the source meta model in the model library
   * @param name the new project-specific name
   * @throws NotFoundException if the VSUM membership or meta model link does not exist
   * @throws AccessDeniedException if the caller is a VSUM viewer
   */
  @Transactional
  public void updateMetaModelName(
      String callerEmail, Long vsumId, Long sourceMetaModelId, String name) {
    VsumUser vsumUser = findEditingVsumUser(callerEmail, vsumId);
    VsumMetaModel vsumMetaModel = findVsumMetaModel(vsumUser.getVsum(), sourceMetaModelId);

    if (Objects.equals(vsumMetaModel.getName(), name)) {
      return;
    }

    vsumHistoryService.create(vsumUser.getVsum(), vsumUser.getUser());
    vsumMetaModel.setName(name);
    vsumMetaModelRepository.save(vsumMetaModel);
  }

  /**
   * Replaces the Ecore content of a meta model within a VSUM.
   *
   * <p>Only the VSUM's own copy of the meta model is changed, and its GenModel is regenerated to
   * match. The model-library entry it was cloned from is not affected.
   *
   * @param callerEmail the email of the VSUM member requesting the change
   * @param vsumId the VSUM containing the meta model
   * @param sourceMetaModelId the ID of the source meta model in the model library
   * @param ecore the complete new Ecore content
   * @throws NotFoundException if the VSUM membership or meta model link does not exist
   * @throws AccessDeniedException if the caller is a VSUM viewer
   * @throws tools.vitruv.methodologist.exception.SetupServiceException if the GenModel cannot be
   *     generated
   */
  @Transactional
  public void updateMetaModelEcore(
      String callerEmail, Long vsumId, Long sourceMetaModelId, String ecore) {
    VsumUser vsumUser = findEditingVsumUser(callerEmail, vsumId);
    MetaModel projectMetaModel =
        findVsumMetaModel(vsumUser.getVsum(), sourceMetaModelId).getMetaModel();
    metaModelService.updateProjectEcore(projectMetaModel, ecore.getBytes(StandardCharsets.UTF_8));
  }

  private VsumUser findEditingVsumUser(String callerEmail, Long vsumId) {
    VsumUser vsumUser =
        vsumUserRepository
            .findByVsum_IdAndUser_EmailAndUser_RemovedAtIsNullAndVsum_RemovedAtIsNull(
                vsumId, callerEmail)
            .orElseThrow(() -> new NotFoundException(VSUM_ID_NOT_FOUND_ERROR));

    if (vsumUser.getRole() == VsumRole.VIEWER) {
      throw new AccessDeniedException(USER_DOES_NOT_HAVE_ACCESS);
    }
    return vsumUser;
  }

  private VsumMetaModel findVsumMetaModel(Vsum vsum, Long sourceMetaModelId) {
    return vsumMetaModelRepository
        .findByVsumAndMetaModel_Source_Id(vsum, sourceMetaModelId)
        .orElseThrow(
            () -> new NotFoundException(METAMODEL_IDS_NOT_FOUND_IN_THIS_VSUM_NOT_FOUND_ERROR));
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
   * Fetches a VSUM owned by the caller and returns its details together with the mapped meta-models
   * and views. Throws {@code NotFoundException} if the VSUM does not exist or does not belong to
   * the caller.
   *
   * @param callerEmail the authenticated user's email (owner of the VSUM)
   * @param id the VSUM id to fetch
   * @return a response DTO with VSUM data, meta-models, and views
   * @throws NotFoundException if no matching VSUM is found
   */
  @Transactional(readOnly = true)
  public VsumMetaModelResponse findVsumWithDetails(String callerEmail, Long id) {
    User user =
        userRepository
            .findByEmailIgnoreCaseAndRemovedAtIsNull(callerEmail)
            .orElseThrow(() -> new AccessDeniedException(USER_DOES_NOT_HAVE_ACCESS));

    Vsum vsum =
        vsumRepository
            .findByIdAndRemovedAtIsNull(id)
            .orElseThrow(() -> new NotFoundException(VSUM_ID_NOT_FOUND_ERROR));

    vsum.getVsumUsers().stream()
        .filter(vsumUser -> vsumUser.getUser().equals(user))
        .findFirst()
        .orElseThrow(() -> new AccessDeniedException(USER_DOES_NOT_HAVE_ACCESS));

    VsumMetaModelResponse response = vsumMapper.toVsumMetaModelResponse(vsum);

    List<VsumMetaModel> vsumMetaModels =
        vsum.getVsumMetaModels() == null ? List.of() : List.copyOf(vsum.getVsumMetaModels());
    Map<Long, String> projectMetaModelNames =
        vsumMetaModels.stream()
            .collect(
                Collectors.toMap(
                    vsumMetaModel -> vsumMetaModel.getMetaModel().getId(),
                    this::projectMetaModelName,
                    (first, second) -> first));

    List<MetaModelResponse> metaModels =
        vsumMetaModels.stream().map(this::toProjectMetaModelResponse).toList();
    response.setMetaModels(metaModels);

    List<MetaModelRelationResponse> metaModelRelation =
        (vsum.getMetaModelRelations() == null
                ? List.<MetaModelRelation>of()
                : vsum.getMetaModelRelations())
            .stream()
                .map(
                    relation ->
                        metaModelRelationMapper.toMetaModelRelationResponse(
                            relation, lowCodeReactionRequestMapper))
                .toList();
    response.setMetaModelsRelation(metaModelRelation);

    List<ViewsResponse> views =
        vsumViewRepository.findAllByVsum(vsum).stream()
            .map(vsumViewMapper::toViewsResponse)
            .toList();
    applyProjectMetaModelNames(views, projectMetaModelNames);
    response.setViews(views);

    return response;
  }

  private MetaModelResponse toProjectMetaModelResponse(VsumMetaModel vsumMetaModel) {
    MetaModelResponse response = metaModelMapper.toMetaModelResponse(vsumMetaModel.getMetaModel());
    response.setName(projectMetaModelName(vsumMetaModel));
    return response;
  }

  private String projectMetaModelName(VsumMetaModel vsumMetaModel) {
    return vsumMetaModel.getName() == null
        ? vsumMetaModel.getMetaModel().getName()
        : vsumMetaModel.getName();
  }

  private void applyProjectMetaModelNames(
      List<ViewsResponse> views, Map<Long, String> projectMetaModelNames) {
    for (ViewsResponse view : views) {
      if (view.getAssignedModels() == null) {
        continue;
      }
      for (MetaModelResponse assignedModel : view.getAssignedModels()) {
        String projectMetaModelName = projectMetaModelNames.get(assignedModel.getId());
        if (projectMetaModelName != null) {
          assignedModel.setName(projectMetaModelName);
        }
      }
    }
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
   * Permanently deletes a {@link Vsum} from the trash of the calling owner, without waiting for the
   * 30-day cleanup.
   *
   * <p>Runs the same cleanup as {@link VsumCleanupService#delete()}: the project's user
   * relationships, history, meta-model copies, relations and builds are removed. Meta models of the
   * model library that were used only by this project can be deleted afterwards. The operation
   * cannot be undone.
   *
   * @param callerEmail the email of the authenticated user; must own the VSUM
   * @param id the identifier of the VSUM to delete permanently
   * @throws NotFoundException if the VSUM does not exist, is not in the trash, or is not owned by
   *     the caller
   */
  @Transactional
  public void deletePermanently(String callerEmail, Long id) {
    Vsum vsum =
        vsumRepository
            .findByIdAndUser_EmailAndUser_RemovedAtIsNullAndRemovedAtIsNotNull(id, callerEmail)
            .orElseThrow(() -> new NotFoundException(VSUM_ID_NOT_FOUND_ERROR));

    vsumCleanupService.deleteProjectData(vsum);
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
   * Builds the VSUM and returns the generated fat JAR.
   *
   * <p>The build itself is handled by {@link VsumBuildService}: an up-to-date build of the same
   * input files is reused, otherwise a new one is started and this call waits for it. Access is
   * restricted to members of the VSUM.
   *
   * @param callerEmail email address of the requesting user
   * @param id the VSUM identifier
   * @return the generated fat JAR bytes
   * @throws AccessDeniedException if the user is not authorized for this VSUM
   * @throws tools.vitruv.methodologist.exception.NotFoundException if required files (meta-models
   *     or reactions) are missing
   * @throws tools.vitruv.methodologist.exception.SetupServiceException if the build fails
   * @throws tools.vitruv.methodologist.exception.VsumBuildingException if the build does not finish
   *     within the configured wait timeout or the build queue is full
   */
  public byte[] getJarfat(String callerEmail, Long id) {
    return vsumBuildService.buildAndWait(callerEmail, id);
  }
}
