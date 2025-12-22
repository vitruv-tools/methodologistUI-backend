package tools.vitruv.methodologist.vsum.service;

import static tools.vitruv.methodologist.messages.Error.METAMODEL_IDS_NOT_FOUND_IN_THIS_VSUM_NOT_FOUND_ERROR;
import static tools.vitruv.methodologist.messages.Error.REACTION_FILE_IDS_ID_NOT_FOUND_ERROR;
import static tools.vitruv.methodologist.messages.Error.USER_DOSE_NOT_HAVE_ACCESS;
import static tools.vitruv.methodologist.messages.Error.VSUM_ID_NOT_FOUND_ERROR;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.vitruv.methodologist.exception.BuildArtifactCreationException;
import tools.vitruv.methodologist.exception.NotFoundException;
import tools.vitruv.methodologist.exception.UnauthorizedException;
import tools.vitruv.methodologist.general.model.FileStorage;
import tools.vitruv.methodologist.user.model.User;
import tools.vitruv.methodologist.user.model.repository.UserRepository;
import tools.vitruv.methodologist.vsum.VsumRole;
import tools.vitruv.methodologist.vsum.build.BuildCoordinator;
import tools.vitruv.methodologist.vsum.build.BuildKey;
import tools.vitruv.methodologist.vsum.build.InputsFingerprint;
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
import tools.vitruv.methodologist.vsum.model.MetaModel;
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
  BuildCoordinator buildCoordinator;

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
   * Updates a VSUM owned by the caller and synchronizes its meta-models and meta-model relations
   * with the provided request.
   *
   * <p>Associations not present in the request are removed; missing associations are created. If
   * collections in the request are null or empty, the corresponding associations are cleared. The
   * operation is transactional.
   *
   * @param callerEmail the authenticated user's email; must own the VSUM
   * @param id the VSUM identifier to update
   * @param vsumSyncChangesPutRequest the desired state (meta-model IDs and relation definitions)
   * @return the persisted, updated {@link Vsum}
   * @throws tools.vitruv.methodologist.exception.NotFoundException if the VSUM does not exist, is
   *     removed, or does not belong to the caller
   */
  @Transactional
  public Vsum update(
      String callerEmail, Long id, VsumSyncChangesPutRequest vsumSyncChangesPutRequest) {
    VsumUser vsumUser =
        vsumUserRepository
            .findByVsum_IdAndUser_EmailAndUser_RemovedAtIsNullAndVsum_RemovedAtIsNull(
                id, callerEmail)
            .orElseThrow(() -> new NotFoundException(VSUM_ID_NOT_FOUND_ERROR));

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
        metaModelRelationRepository.findAllByVsum(vsumUser.getVsum());

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
    List<VsumMetaModel> existingVsumMetaModel =
        vsumMetaModelRepository.findAllByVsum(vsumUser.getVsum());

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

    if (!toRemoveMetaModelRelation.isEmpty()
        || !toRemoveVsumMetaModelIds.isEmpty()
        || !toAddVsumMetaModelIds.isEmpty()
        || !toAddMetaModelRelation.isEmpty()) {
      vsumHistoryService.create(vsumUser.getVsum(), vsumUser.getUser());
    }

    if (!toRemoveMetaModelRelation.isEmpty()) {
      List<MetaModelRelation> deletions =
          toRemoveMetaModelRelation.stream().map(existingByPair::get).toList();
      metaModelRelationService.delete(deletions);
      deletions.forEach(vsumUser.getVsum().getMetaModelRelations()::remove);
    }

    if (!toRemoveVsumMetaModelIds.isEmpty()) {
      List<VsumMetaModel> toDeleteVsumMetaModel =
          existingVsumMetaModel.stream()
              .filter(
                  vsumMetaModel ->
                      toRemoveVsumMetaModelIds.contains(
                          vsumMetaModel.getMetaModel().getSource().getId()))
              .toList();
      vsumMetaModelService.delete(vsumUser.getVsum(), toDeleteVsumMetaModel);
      toDeleteVsumMetaModel.forEach(vsumUser.getVsum().getVsumMetaModels()::remove);
    }

    if (!toAddVsumMetaModelIds.isEmpty()) {
      vsumMetaModelService.create(vsumUser.getVsum(), toAddVsumMetaModelIds);
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
      metaModelRelationService.create(vsumUser.getVsum(), creations);
    }
    vsumRepository.save(vsumUser.getVsum());
    return vsumUser.getVsum();
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
   * Builds the VSUM project (if necessary) and returns a ZIP archive containing the generated fat
   * JAR and a standard Dockerfile.
   *
   * <p>Access is restricted to users who are members of the given VSUM. If the caller does not have
   * access, an {@link AccessDeniedException} is thrown.
   *
   * <p>The returned ZIP always contains:
   *
   * <ul>
   *   <li><b>app.jar</b> – the VSUM fat JAR with dependencies
   *   <li><b>Dockerfile</b> – a minimal Dockerfile to run the JAR
   * </ul>
   *
   * <p>The build process is delegated to {@link #buildOrThrow(Vsum)}. If a valid artifact already
   * exists, it may be reused; otherwise a new build is triggered.
   *
   * @param callerEmail email address of the requesting user
   * @param id the VSUM identifier
   * @return ZIP archive bytes containing the fat JAR and Dockerfile
   * @throws AccessDeniedException if the user is not authorized for this VSUM
   * @throws tools.vitruv.methodologist.exception.VsumBuildingException if the build process fails
   */
  public byte[] getJarfat(String callerEmail, Long id) {
    VsumUser vsumUser =
        vsumUserRepository
            .findByVsum_IdAndUser_EmailAndUser_RemovedAtIsNullAndVsum_RemovedAtIsNull(
                id, callerEmail)
            .orElseThrow(() -> new AccessDeniedException(USER_DOSE_NOT_HAVE_ACCESS));

    Vsum vsum = vsumUser.getVsum();
    BuildKey key = buildKey(callerEmail, id, vsum);

    byte[] jarBytes = buildCoordinator.runOncePerKey(key, () -> buildOrThrow(vsum));

    return zipJarAndDockerfile(jarBytes);
  }

  /**
   * Creates a ZIP archive that contains the provided JAR bytes and a Dockerfile.
   *
   * <p>The produced ZIP contains two entries: \- "findGoodname.jar" with the raw {@code jarBytes}
   * \- "Dockerfile" with the {@code dockerfile} content encoded as UTF\-8
   *
   * @param jarBytes the JAR bytes to include in the archive; must not be {@code null}
   * @return a byte array containing the ZIP archive
   * @throws RuntimeException if an I/O error occurs while creating the ZIP archive
   */
  private byte[] zipJarAndDockerfile(byte[] jarBytes) {
    try {

      ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
      try (ZipOutputStream zipOutputStream = new ZipOutputStream(byteArrayOutputStream)) {

        ZipEntry jarEntry =
            new ZipEntry("methodologisttemplate.vsum-0.1.0-SNAPSHOT-jar-with-dependencies.jar");
        zipOutputStream.putNextEntry(jarEntry);
        zipOutputStream.write(jarBytes);
        zipOutputStream.closeEntry();

        zipOutputStream.putNextEntry(new ZipEntry("Dockerfile"));
        zipOutputStream.write(dockerfileBytes());
        zipOutputStream.closeEntry();
      }
      return byteArrayOutputStream.toByteArray();
    } catch (IOException e) {
      throw new BuildArtifactCreationException(e.getMessage());
    }
  }

  /**
   * Provides a standard Dockerfile for running the generated VSUM fat JAR.
   *
   * <p>The Dockerfile uses a minimal Java 17 runtime image, copies the application JAR into the
   * container, exposes port 8080, and starts the application using {@code java -jar}.
   *
   * <p>This Dockerfile is intentionally static and deterministic:
   *
   * <p>The client is expected to build the image with:
   *
   * <pre>{@code
   * docker build -t my-app .
   * }</pre>
   *
   * @return Dockerfile content as UTF-8 encoded bytes
   */
  public byte[] dockerfileBytes() {
    String dockerfile =
        """
            FROM eclipse-temurin:17-jre-alpine

            WORKDIR /app

            COPY app.jar /app/app.jar

            EXPOSE 8080

            ENTRYPOINT ["java", "-jar", "/app/app.jar"]
            """;

    return dockerfile.getBytes(StandardCharsets.UTF_8);
  }

  /**
   * Builds a VSUM distribution by collecting all required metamodel Ecore/GenModel pairs and
   * reaction files referenced by the given {@link Vsum} and invoking the external Vitruv
   * integration to produce a fat JAR.
   *
   * <p>The method deduplicates metamodel files, validates that at least one Ecore/GenModel pair and
   * at least one reaction file are present, and delegates the actual build to {@link
   * #metaModelVitruvIntegrationService}.
   *
   * @param vsum the VSUM aggregate containing meta-model relations and reaction file references
   * @return a {@link java.lang.Byte} containing the generated fat JAR bytes and a Dockerfile
   *     content string
   * @throws tools.vitruv.methodologist.exception.NotFoundException if required files (meta-models
   *     or reactions) are missing
   * @throws tools.vitruv.methodologist.exception.VsumBuildingException if the external build
   *     process fails or IO errors occur during build
   */
  public byte[] buildOrThrow(Vsum vsum) {
    if (vsum.getMetaModelRelations() == null || vsum.getMetaModelRelations().isEmpty()) {
      throw new NotFoundException(REACTION_FILE_IDS_ID_NOT_FOUND_ERROR);
    }

    Map<String, FileStorage> ecores = new LinkedHashMap<>();
    Map<String, FileStorage> genmodels = new LinkedHashMap<>();
    List<FileStorage> reactions = new ArrayList<>();

    for (MetaModelRelation relation : vsum.getMetaModelRelations()) {
      if (relation == null) {
        throw new NotFoundException(REACTION_FILE_IDS_ID_NOT_FOUND_ERROR);
      }

      MetaModel source = relation.getSource();
      MetaModel target = relation.getTarget();

      if (source != null) {
        putPair(ecores, genmodels, source.getEcoreFile(), source.getGenModelFile());
      }
      if (target != null) {
        putPair(ecores, genmodels, target.getEcoreFile(), target.getGenModelFile());
      }

      FileStorage reaction = relation.getReactionFileStorage();
      if (reaction != null) {
        reactions.add(reaction);
      }
    }

    if (ecores.isEmpty() || genmodels.isEmpty()) {
      throw new NotFoundException(METAMODEL_IDS_NOT_FOUND_IN_THIS_VSUM_NOT_FOUND_ERROR);
    }
    if (reactions.isEmpty()) {
      throw new NotFoundException(REACTION_FILE_IDS_ID_NOT_FOUND_ERROR);
    }

    List<FileStorage> ecoreList = new ArrayList<>(ecores.values());
    List<FileStorage> genList = new ArrayList<>(genmodels.values());

    return metaModelVitruvIntegrationService.runVitruvAndGetFatJarBytes(
        ecoreList, genList, reactions);
  }

  /**
   * Associates an Ecore/GenModel pair into the provided maps using stable keys.
   *
   * <p>If either {@code ecore} or {@code genmodel} is {@code null} the pair is ignored. Existing
   * entries in the maps are preserved (first seen wins).
   *
   * @param ecores map to populate with Ecore FileStorage instances keyed by {@link
   *     #key(FileStorage)}
   * @param genmodels map to populate with GenModel FileStorage instances keyed by {@link
   *     #key(FileStorage)}
   * @param ecore the Ecore file storage; may be {@code null}
   * @param genmodel the GenModel file storage; may be {@code null}
   */
  private void putPair(
      Map<String, FileStorage> ecores,
      Map<String, FileStorage> genmodels,
      FileStorage ecore,
      FileStorage genmodel) {

    if (ecore == null || genmodel == null) {
      return;
    }

    String keyE = key(ecore);
    String keyG = key(genmodel);

    ecores.putIfAbsent(keyE, ecore);
    genmodels.putIfAbsent(keyG, genmodel);
  }

  /**
   * Produces a stable lookup key for a {@link FileStorage}.
   *
   * <p>If the storage has a non-{@code null} id the returned key is {@code "id:<id>"}, otherwise it
   * is {@code "name:<filename>"}. A {@code null} filename is treated as an empty string.
   *
   * @param fs the FileStorage instance; must not be {@code null}
   * @return a stable string key suitable for map lookups
   */
  private String key(FileStorage fs) {
    if (fs.getId() != null) {
      return "id:" + fs.getId();
    }
    return "name:" + (fs.getFilename() == null ? "" : fs.getFilename());
  }

  /**
   * Builds a {@link BuildKey} that uniquely identifies a build request for the given VSUM.
   *
   * <p>The method collects a deterministic set of Ecore/GenModel pairs and the first reaction file
   * referenced by the provided {@code vsum} and computes an inputs fingerprint using {@link
   * InputsFingerprint#fingerprint(List, List, FileStorage)}. The returned {@link BuildKey} encodes
   * the requesting user's email, the VSUM id and the computed fingerprint so concurrent or repeated
   * requests with identical inputs can be deduplicated.
   *
   * <p>Important details:
   *
   * <ul>
   *   <li>Pairs are deduplicated using stable keys (insertion order is preserved via {@link
   *       java.util.LinkedHashMap} to make fingerprinting deterministic).
   *   <li>The fingerprint is computed from the list of Ecore files, the list of GenModel files, and
   *       the first reaction file found in the VSUM relations.
   *   <li>The method performs validation and will throw {@link
   *       tools.vitruv.methodologist.exception.NotFoundException} when required input data is
   *       missing or relations are malformed.
   * </ul>
   *
   * @param callerEmail the email of the caller that will be associated with the build key; may be
   *     used to scope keys per-user
   * @param vsumId the identifier of the VSUM for which the build is requested
   * @param vsum the VSUM aggregate containing meta-model relations and reaction file references;
   *     must not be {@code null} and must contain at least one valid meta-model pair and one
   *     reaction file reference
   * @return a new {@link BuildKey} composed of the caller email, VSUM id and an inputs fingerprint
   * @throws tools.vitruv.methodologist.exception.NotFoundException if {@code
   *     vsum.getMetaModelRelations()} is {@code null} or empty, or if any relation is {@code null},
   *     or if no valid Ecore/GenModel pairs or reaction files are available
   */
  private BuildKey buildKey(String callerEmail, Long vsumId, Vsum vsum) {
    Map<String, FileStorage> ecores = new LinkedHashMap<>();
    Map<String, FileStorage> genmodels = new LinkedHashMap<>();
    List<FileStorage> reactions = new ArrayList<>();

    if (vsum.getMetaModelRelations() == null || vsum.getMetaModelRelations().isEmpty()) {
      throw new NotFoundException(REACTION_FILE_IDS_ID_NOT_FOUND_ERROR);
    }

    for (MetaModelRelation relation : vsum.getMetaModelRelations()) {
      if (relation == null) {
        throw new NotFoundException(REACTION_FILE_IDS_ID_NOT_FOUND_ERROR);
      }

      MetaModel source = relation.getSource();
      MetaModel target = relation.getTarget();

      if (source != null) {
        putPair(ecores, genmodels, source.getEcoreFile(), source.getGenModelFile());
      }
      if (target != null) {
        putPair(ecores, genmodels, target.getEcoreFile(), target.getGenModelFile());
      }

      FileStorage reaction = relation.getReactionFileStorage();
      if (reaction != null) {
        reactions.add(reaction);
      }
    }

    if (ecores.isEmpty() || genmodels.isEmpty()) {
      throw new NotFoundException(METAMODEL_IDS_NOT_FOUND_IN_THIS_VSUM_NOT_FOUND_ERROR);
    }
    if (reactions.isEmpty()) {
      throw new NotFoundException(REACTION_FILE_IDS_ID_NOT_FOUND_ERROR);
    }

    String fingerprint =
        InputsFingerprint.fingerprint(
            new ArrayList<>(ecores.values()),
            new ArrayList<>(genmodels.values()),
            reactions.get(0));

    return new BuildKey(callerEmail, vsumId, fingerprint);
  }
}
