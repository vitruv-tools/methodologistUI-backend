package tools.vitruv.methodologist.vsum.service;

import static tools.vitruv.methodologist.messages.Error.ECORE_FILE_ID_NOT_FOUND_ERROR;
import static tools.vitruv.methodologist.messages.Error.META_MODEL_ID_NOT_FOUND_ERROR;
import static tools.vitruv.methodologist.messages.Error.USER_DOSE_NOT_HAVE_ACCESS;
import static tools.vitruv.methodologist.messages.Error.USER_EMAIL_NOT_FOUND_ERROR;
import static tools.vitruv.methodologist.messages.Message.META_MODEL_CREATED_SUCCESSFULLY;

import jakarta.validation.Valid;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.vitruv.methodologist.apihandler.SetupServiceApiHandler;
import tools.vitruv.methodologist.apihandler.dto.response.GenModelInspectionResponse;
import tools.vitruv.methodologist.exception.CreateMwe2FileException;
import tools.vitruv.methodologist.exception.MetaModelUsedInVsumException;
import tools.vitruv.methodologist.exception.MetaModelVersionAlreadyExistsException;
import tools.vitruv.methodologist.exception.NotFoundException;
import tools.vitruv.methodologist.general.FileEnumType;
import tools.vitruv.methodologist.general.controller.responsedto.FileStorageResponse;
import tools.vitruv.methodologist.general.model.FileStorage;
import tools.vitruv.methodologist.general.model.repository.FileStorageRepository;
import tools.vitruv.methodologist.general.service.FileStorageService;
import tools.vitruv.methodologist.user.model.User;
import tools.vitruv.methodologist.user.model.repository.UserRepository;
import tools.vitruv.methodologist.vsum.controller.dto.request.MetaModelFilterRequest;
import tools.vitruv.methodologist.vsum.controller.dto.request.MetaModelPostRequest;
import tools.vitruv.methodologist.vsum.controller.dto.request.MetaModelPutRequest;
import tools.vitruv.methodologist.vsum.controller.dto.response.MetaModelResponse;
import tools.vitruv.methodologist.vsum.controller.dto.response.MetaModelVsumUsageResponse;
import tools.vitruv.methodologist.vsum.mapper.MetaModelMapper;
import tools.vitruv.methodologist.vsum.model.MetaModel;
import tools.vitruv.methodologist.vsum.model.Vsum;
import tools.vitruv.methodologist.vsum.model.VsumMetaModel;
import tools.vitruv.methodologist.vsum.model.repository.MetaModelRepository;
import tools.vitruv.methodologist.vsum.model.repository.MetaModelSpecifications;
import tools.vitruv.methodologist.vsum.model.repository.VsumMetaModelRepository;

/**
 * Service class for managing metamodel operations including creation and retrieval. Handles the
 * business logic for metamodel management while ensuring proper validation and relationships with
 * files and users.
 *
 * @see tools.vitruv.methodologist.vsum.model.MetaModel
 * @see tools.vitruv.methodologist.vsum.model.repository.MetaModelRepository
 * @see tools.vitruv.methodologist.general.model.repository.FileStorageRepository
 * @see tools.vitruv.methodologist.user.model.repository.UserRepository
 */
@Service
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MetaModelService {

  private static final String GEN_MODEL_EXTENSION = ".genmodel";
  private static final String DEFAULT_GEN_MODEL_BASE_NAME = "model";

  MetaModelService self;
  MetaModelMapper metaModelMapper;
  MetaModelRepository metaModelRepository;
  FileStorageRepository fileStorageRepository;
  UserRepository userRepository;
  MetamodelBuildService metamodelBuildService;
  FileStorageService fileStorageService;
  SetupServiceApiHandler setupServiceApiHandler;
  VsumMetaModelRepository vsumMetaModelRepository;

  /**
   * Constructs a new MetaModelService with all required dependencies.
   *
   * @param self lazy-loaded self-reference for transactional method calls
   * @param metaModelMapper mapper for converting between entities and DTOs
   * @param metaModelRepository repository for metamodel persistence
   * @param fileStorageRepository repository for file storage operations
   * @param userRepository repository for user data access
   * @param metamodelBuildService service for building metamodel artifacts
   * @param fileStorageService service for file storage management
   * @param setupServiceApiHandler handler for GenModel process/inspect calls to the setup-service
   * @param vsumMetaModelRepository repository for VSUM-metamodel relationships
   */
  public MetaModelService(
      @Lazy MetaModelService self,
      MetaModelMapper metaModelMapper,
      MetaModelRepository metaModelRepository,
      FileStorageRepository fileStorageRepository,
      UserRepository userRepository,
      MetamodelBuildService metamodelBuildService,
      FileStorageService fileStorageService,
      SetupServiceApiHandler setupServiceApiHandler,
      VsumMetaModelRepository vsumMetaModelRepository) {
    this.self = self;
    this.metaModelMapper = metaModelMapper;
    this.metaModelRepository = metaModelRepository;
    this.fileStorageRepository = fileStorageRepository;
    this.userRepository = userRepository;
    this.metamodelBuildService = metamodelBuildService;
    this.fileStorageService = fileStorageService;
    this.setupServiceApiHandler = setupServiceApiHandler;
    this.vsumMetaModelRepository = vsumMetaModelRepository;
  }

  /**
   * Loads the user, mapped metamodel, and referenced Ecore file required to create a metamodel.
   *
   * @param callerEmail authenticated user's email
   * @param req creation request
   * @return the fully prepared create context
   */
  private CreateContext prepareCreateContext(String callerEmail, MetaModelPostRequest req) {
    User user =
        userRepository
            .findByEmailIgnoreCaseAndRemovedAtIsNull(callerEmail)
            .orElseThrow(() -> new NotFoundException(USER_EMAIL_NOT_FOUND_ERROR));

    MetaModel metaModel = metaModelMapper.toMetaModel(req);

    FileStorage ecoreFile =
        fileStorageRepository
            .findByIdAndTypeAndUser_EmailAndUser_RemovedAtIsNull(
                req.getEcoreFileId(), FileEnumType.ECORE, callerEmail)
            .orElseThrow(() -> new NotFoundException(ECORE_FILE_ID_NOT_FOUND_ERROR));

    return new CreateContext(user, metaModel, ecoreFile);
  }

  /**
   * Rejects a library metamodel when this user already has the same name and version. A different
   * version of the same name is allowed. Cloned metamodels are not part of the check.
   *
   * @param user the owning user
   * @param name the metamodel name
   * @param version the metamodel version
   * @param excludedId the metamodel id to ignore when updating, or {@code null} when creating
   */
  private void rejectDuplicateLibraryVersion(
      User user, String name, String version, Long excludedId) {
    boolean duplicate =
        excludedId == null
            ? metaModelRepository.existsLibraryMetamodel(user, name, version)
            : metaModelRepository.existsOtherLibraryMetamodel(user, name, version, excludedId);
    if (duplicate) {
      throw new MetaModelVersionAlreadyExistsException();
    }
  }

  private MetaModel savePendingMetaModel(CreateContext createContext, FileStorage genModelFile) {
    MetaModel metaModel = createContext.metaModel();
    metaModel.setUser(createContext.user());
    metaModel.setEcoreFile(createContext.ecoreFile());
    metaModel.setGenModelFile(genModelFile);
    return metaModelRepository.save(metaModel);
  }

  /**
   * Generates the GenModel for the given Ecore file via the setup-service and stores it as a new
   * GenModel file owned by the caller.
   *
   * @param callerEmail authenticated user's email
   * @param ecoreFile the Ecore file to generate the GenModel from
   * @return the stored GenModel file
   * @throws tools.vitruv.methodologist.exception.SetupServiceException if the setup-service call
   *     fails
   */
  private FileStorage storeGeneratedGenModel(String callerEmail, FileStorage ecoreFile) {
    byte[] genModel = setupServiceApiHandler.generateGenModelOrThrow(ecoreFile);
    FileStorageResponse storedGenModel =
        fileStorageService.storeFile(
            callerEmail, genModel, genModelFilename(ecoreFile), null, FileEnumType.GEN_MODEL);
    return fileStorageService.getFile(storedGenModel.getId());
  }

  /**
   * Regenerates the GenModel from a changed Ecore file and writes it into the GenModel file of each
   * given metamodel, so the GenModel always matches the Ecore it belongs to.
   *
   * @param ecoreFile the new Ecore file
   * @param metaModels the metamodels that now use this Ecore file
   * @throws tools.vitruv.methodologist.exception.SetupServiceException if the setup-service call
   *     fails
   */
  private void regenerateGenModels(FileStorage ecoreFile, MetaModel... metaModels) {
    byte[] genModel = setupServiceApiHandler.generateGenModelOrThrow(ecoreFile);
    String filename = genModelFilename(ecoreFile);
    for (MetaModel metaModel : metaModels) {
      FileStorage genModelFile = metaModel.getGenModelFile();
      genModelFile.setFilename(filename);
      fileStorageService.overwriteStoredContent(genModelFile, genModel);
    }
  }

  /**
   * Derives the GenModel filename from the Ecore filename, for example {@code model.ecore} becomes
   * {@code model.genmodel}.
   *
   * @param ecoreFile the Ecore file
   * @return the GenModel filename
   */
  private static String genModelFilename(FileStorage ecoreFile) {
    String ecoreFilename = ecoreFile.getFilename();
    if (ecoreFilename == null || ecoreFilename.isBlank()) {
      return DEFAULT_GEN_MODEL_BASE_NAME + GEN_MODEL_EXTENSION;
    }
    int extensionStart = ecoreFilename.lastIndexOf('.');
    String baseName =
        extensionStart > 0 ? ecoreFilename.substring(0, extensionStart) : ecoreFilename;
    return baseName + GEN_MODEL_EXTENSION;
  }

  /**
   * Sends the given GenModel to the setup-service {@code /api/genmodel/process} endpoint, which
   * fixes it, and returns the processed GenModel file.
   *
   * @param genModelFile the GenModel file to process
   * @return the processed GenModel file as a byte array
   * @throws tools.vitruv.methodologist.exception.SetupServiceException if the setup-service call
   *     fails
   */
  public byte[] processGenModel(FileStorage genModelFile) {
    return setupServiceApiHandler.processGenModelOrThrow(genModelFile);
  }

  /**
   * Sends the given GenModel to the setup-service {@code /api/genmodel/inspect} endpoint and
   * returns the inspection result (planned changes on success, or the detected problem).
   *
   * @param genModelFile the GenModel file to inspect
   * @return the inspection result
   * @throws tools.vitruv.methodologist.exception.SetupServiceException if the setup-service call
   *     fails
   */
  public GenModelInspectionResponse inspectGenModel(FileStorage genModelFile) {
    return setupServiceApiHandler.inspectGenModelOrThrow(genModelFile);
  }

  /**
   * Creates a metamodel from the request.
   *
   * <p>Only the Ecore file is uploaded. The GenModel is generated from it by the setup-service
   * {@code generate} endpoint and stored as a new file, and the metamodel is then persisted and
   * built.
   *
   * @param callerEmail authenticated user's email
   * @param req creation request
   * @return the creation result holding the created metamodel and the success message
   */
  @Transactional
  public MetaModelCreationResult create(String callerEmail, MetaModelPostRequest req) {
    CreateContext createContext = prepareCreateContext(callerEmail, req);
    rejectDuplicateLibraryVersion(
        createContext.user(),
        createContext.metaModel().getName(),
        createContext.metaModel().getVersion(),
        null);

    FileStorage genModelFile = storeGeneratedGenModel(callerEmail, createContext.ecoreFile());
    MetaModel metaModel = savePendingMetaModel(createContext, genModelFile);

    MetamodelBuildService.BuildResult result =
        metamodelBuildService.buildAndValidate(
            MetamodelBuildService.MetamodelBuildInput.builder()
                .metaModelId(metaModel.getId())
                .ecoreBytes(metaModel.getEcoreFile().getData())
                .genModelBytes(metaModel.getGenModelFile().getData())
                .runMwe2(true)
                .build());

    if (!result.isSuccess()) {
      throw new CreateMwe2FileException(result.getReport());
    }

    return new MetaModelCreationResult(metaModel, META_MODEL_CREATED_SUCCESSFULLY);
  }

  /**
   * Retrieves a paginated list of metamodels belonging to the given user, applying optional
   * filtering criteria.
   *
   * <p>This method constructs a {@link Specification} based on the caller's email and the provided
   * filter request, executes the query with pagination, and maps the resulting entities to {@link
   * MetaModelResponse} DTOs.
   *
   * @param callerEmail the email of the user whose metamodels are being requested
   * @param metaModelFilterRequest filter criteria to apply when searching for metamodels
   * @param pageable pagination information including page size and sort order
   * @return a paginated list of metamodel responses matching the user and filters
   */
  @Transactional
  public List<MetaModelResponse> findAll(
      String callerEmail, MetaModelFilterRequest metaModelFilterRequest, Pageable pageable) {
    Specification<MetaModel> spec =
        Specification.where(
            MetaModelSpecifications.buildSpecification(callerEmail, metaModelFilterRequest));
    List<MetaModel> metaModels = metaModelRepository.findAll(spec, pageable);
    Map<Long, List<MetaModelVsumUsageResponse>> vsumsBySourceId =
        findVsumsUsing(metaModels, callerEmail);
    return metaModels.stream()
        .map(
            metaModel -> {
              MetaModelResponse response = metaModelMapper.toMetaModelResponse(metaModel);
              response.setVsums(vsumsBySourceId.getOrDefault(metaModel.getId(), List.of()));
              return response;
            })
        .toList();
  }

  /**
   * Finds the VSUMs that use the given library metamodels through their clones, using the same
   * relation as the deletion check in {@link #delete(String, Long)}, so trashed VSUMs are included.
   *
   * <p>Every using VSUM yields one entry, so the entry count always matches the deletion check.
   * Details are only exposed for VSUMs the caller is a member of; entries for other users' VSUMs
   * stay empty so their names are not disclosed.
   *
   * @param metaModels the library metamodels to look up
   * @param callerEmail the email of the requesting user
   * @return the using VSUMs grouped by library metamodel ID; metamodels without usage are absent
   */
  private Map<Long, List<MetaModelVsumUsageResponse>> findVsumsUsing(
      List<MetaModel> metaModels, String callerEmail) {
    if (metaModels.isEmpty()) {
      return Map.of();
    }
    List<Long> sourceIds = metaModels.stream().map(MetaModel::getId).toList();
    return vsumMetaModelRepository.findAllByMetaModel_Source_IdIn(sourceIds).stream()
        .collect(
            Collectors.groupingBy(
                vsumMetaModel -> vsumMetaModel.getMetaModel().getSource().getId(),
                Collectors.collectingAndThen(
                    Collectors.mapping(VsumMetaModel::getVsum, Collectors.toList()),
                    vsums -> toVsumUsageResponses(vsums, callerEmail))));
  }

  private static List<MetaModelVsumUsageResponse> toVsumUsageResponses(
      List<Vsum> vsums, String callerEmail) {
    Map<Boolean, List<Vsum>> byAccess =
        vsums.stream()
            .collect(Collectors.toMap(Vsum::getId, vsum -> vsum, (first, duplicate) -> first))
            .values()
            .stream()
            .collect(Collectors.partitioningBy(vsum -> isMember(vsum, callerEmail)));
    Stream<MetaModelVsumUsageResponse> visible =
        byAccess.get(true).stream()
            .sorted(Comparator.comparing(Vsum::getName, Comparator.nullsLast(String::compareTo)))
            .map(
                vsum ->
                    MetaModelVsumUsageResponse.builder()
                        .id(vsum.getId())
                        .name(vsum.getName())
                        .removedAt(vsum.getRemovedAt())
                        .build());
    Stream<MetaModelVsumUsageResponse> hidden =
        byAccess.get(false).stream().map(vsum -> new MetaModelVsumUsageResponse());
    return Stream.concat(visible, hidden).toList();
  }

  private static boolean isMember(Vsum vsum, String callerEmail) {
    return vsum.getVsumUsers().stream()
        .anyMatch(vsumUser -> callerEmail.equalsIgnoreCase(vsumUser.getUser().getEmail()));
  }

  /**
   * Clones an existing MetaModel instance, including its associated files, and marks the cloned
   * model as a clone. The cloned MetaModel is saved in the repository.
   *
   * @param metaModel the MetaModel instance to clone
   * @return the cloned MetaModel instance
   */
  public MetaModel clone(MetaModel metaModel) {
    MetaModel clonedMetaModel = metaModelMapper.clone(metaModel);
    FileStorage ecoreFile = fileStorageService.clone(metaModel.getEcoreFile());
    FileStorage genModelFile = fileStorageService.clone(metaModel.getGenModelFile());
    clonedMetaModel.setSource(metaModel);
    clonedMetaModel.setEcoreFile(ecoreFile);
    clonedMetaModel.setGenModelFile(genModelFile);
    metaModelRepository.save(clonedMetaModel);
    return clonedMetaModel;
  }

  /**
   * Deletes the file storage records associated with the given cloned {@link MetaModel} entities.
   *
   * <p>Collects all linked Ecore and GenModel files from the provided metamodels and removes them
   * from persistent storage using {@link
   * tools.vitruv.methodologist.general.service.FileStorageService}.
   *
   * @param metaModels the list of cloned {@link MetaModel} entities whose files should be deleted
   */
  @Transactional
  public void deleteCloned(List<MetaModel> metaModels) {
    metaModelRepository.deleteAll(metaModels);
    List<FileStorage> fileStorages = new ArrayList<>();
    fileStorages.addAll(metaModels.stream().map(MetaModel::getEcoreFile).toList());
    fileStorages.addAll(metaModels.stream().map(MetaModel::getGenModelFile).toList());
    fileStorageService.deleteFiles(fileStorages);
  }

  /**
   * Deletes a {@link MetaModel} owned by the specified user, along with its associated Ecore and
   * GenModel files.
   *
   * <p>If the metamodel is currently referenced by any VSUMs, deletion is prevented and a {@link
   * tools.vitruv.methodologist.exception.MetaModelUsedInVsumException} is thrown listing the
   * referencing VSUM names. VSUMs that are in the trash are marked with "(in trash)" and can be
   * deleted permanently to unblock the deletion.
   *
   * @param callerEmail the email address of the user who owns the metamodel
   * @param id the unique identifier of the metamodel to delete
   * @throws NotFoundException if no metamodel exists with the given ID for the specified user
   * @throws tools.vitruv.methodologist.exception.MetaModelUsedInVsumException if the metamodel is
   *     being used in any VSUM
   */
  @Transactional
  public void delete(String callerEmail, Long id) {
    MetaModel metaModel =
        metaModelRepository
            .findByIdAndUser_Email(id, callerEmail)
            .orElseThrow(() -> new NotFoundException(META_MODEL_ID_NOT_FOUND_ERROR));

    List<VsumMetaModel> vsums = vsumMetaModelRepository.findAllByMetaModel_Source(metaModel);
    if (!vsums.isEmpty()) {
      throw new MetaModelUsedInVsumException(
          vsums.stream()
              .map(VsumMetaModel::getVsum)
              .map(MetaModelService::describeBlockingVsum)
              .collect(Collectors.joining(", ")));
    }

    metaModelRepository.delete(metaModel);
    fileStorageService.deleteFiles(List.of(metaModel.getEcoreFile(), metaModel.getGenModelFile()));
  }

  private static String describeBlockingVsum(Vsum vsum) {
    String name = String.valueOf(vsum.getName());
    return vsum.getRemovedAt() == null ? name : name + " (in trash)";
  }

  /**
   * Updates a MetaModel for the active user.
   *
   * <p>If the caller is not allowed to update this MetaModel, an {@link AccessDeniedException} is
   * thrown.
   *
   * @param callerEmail authenticated user's email
   * @param id MetaModel identifier
   * @param metaModelPutRequest requested changes (validated)
   * @throws AccessDeniedException if the caller cannot update the specified MetaModel
   * @throws NotFoundException if the user or MetaModel cannot be found
   */
  @Transactional
  public void update(String callerEmail, Long id, @Valid MetaModelPutRequest metaModelPutRequest) {

    User user =
        userRepository
            .findByEmailIgnoreCaseAndRemovedAtIsNull(callerEmail)
            .orElseThrow(() -> new AccessDeniedException(USER_DOSE_NOT_HAVE_ACCESS));

    MetaModel metaModel =
        metaModelRepository
            .findById(id)
            .orElseThrow(() -> new NotFoundException(META_MODEL_ID_NOT_FOUND_ERROR));

    if (metaModel.getSource() == null) {
      if (!isOwnedBy(metaModel, user)) {
        throw new AccessDeniedException(USER_DOSE_NOT_HAVE_ACCESS);
      }

      FileStorage updatedEcore = resolveUpdatedEcore(callerEmail, metaModel, metaModelPutRequest);
      metaModelMapper.updateByMetaModelPutRequest(metaModelPutRequest, metaModel);
      if (updatedEcore != null) {
        metaModel.setEcoreFile(updatedEcore);
        regenerateGenModels(updatedEcore, metaModel);
      }
      rejectDuplicateLibraryVersion(
          metaModel.getUser(), metaModel.getName(), metaModel.getVersion(), metaModel.getId());
      metaModelRepository.save(metaModel);
      return;
    }

    MetaModel source = metaModel.getSource();

    if (isOwnedBy(source, user)) {
      FileStorage updatedEcore = resolveUpdatedEcore(callerEmail, metaModel, metaModelPutRequest);
      metaModelMapper.updateByMetaModelPutRequest(metaModelPutRequest, source);
      metaModelMapper.updateByMetaModelPutRequest(metaModelPutRequest, metaModel);
      if (updatedEcore != null) {
        source.setEcoreFile(updatedEcore);
        metaModel.setEcoreFile(updatedEcore);
        regenerateGenModels(updatedEcore, source, metaModel);
      }
      rejectDuplicateLibraryVersion(
          source.getUser(), source.getName(), source.getVersion(), source.getId());

      metaModelRepository.saveAll(List.of(source, metaModel));
      return;
    }

    final FileStorage updatedEcore =
        resolveUpdatedEcore(callerEmail, metaModel, metaModelPutRequest);
    MetaModel newSource = clone(source);
    newSource.setUser(user);
    newSource.setSource(null);

    metaModelMapper.updateByMetaModelPutRequest(metaModelPutRequest, newSource);
    metaModelMapper.updateByMetaModelPutRequest(metaModelPutRequest, metaModel);
    if (updatedEcore != null) {
      newSource.setEcoreFile(updatedEcore);
      metaModel.setEcoreFile(updatedEcore);
      regenerateGenModels(updatedEcore, newSource, metaModel);
    }
    rejectDuplicateLibraryVersion(
        user, newSource.getName(), newSource.getVersion(), newSource.getId());

    metaModelRepository.save(newSource);

    metaModel.setSource(newSource);
    metaModelRepository.save(metaModel);
  }

  /**
   * Replaces the Ecore content of a VSUM's own meta model copy and regenerates its GenModel, so
   * both files stay in sync.
   *
   * <p>The model-library entry the copy was cloned from is never modified. If the copy still shares
   * a file with the library entry, that file is cloned first.
   *
   * @param projectMetaModel the VSUM's meta model copy; must have a library source
   * @param ecore the complete new Ecore content
   * @throws tools.vitruv.methodologist.exception.SetupServiceException if the GenModel cannot be
   *     generated
   */
  @Transactional
  public void updateProjectEcore(MetaModel projectMetaModel, byte[] ecore) {
    if (Arrays.equals(projectMetaModel.getEcoreFile().getData(), ecore)) {
      return;
    }

    MetaModel libraryMetaModel = projectMetaModel.getSource();
    projectMetaModel.setEcoreFile(
        projectOwnedFile(projectMetaModel.getEcoreFile(), libraryMetaModel.getEcoreFile()));
    projectMetaModel.setGenModelFile(
        projectOwnedFile(projectMetaModel.getGenModelFile(), libraryMetaModel.getGenModelFile()));

    fileStorageService.overwriteStoredContent(projectMetaModel.getEcoreFile(), ecore);
    regenerateGenModels(projectMetaModel.getEcoreFile(), projectMetaModel);
    metaModelRepository.save(projectMetaModel);
  }

  /**
   * Returns a file the project copy may overwrite: the file itself, or a clone of it if it is
   * shared with the library entry.
   */
  private FileStorage projectOwnedFile(FileStorage projectFile, FileStorage libraryFile) {
    if (libraryFile == null || !Objects.equals(projectFile.getId(), libraryFile.getId())) {
      return projectFile;
    }
    return fileStorageService.clone(projectFile);
  }

  private FileStorage resolveUpdatedEcore(
      String callerEmail, MetaModel metaModel, MetaModelPutRequest request) {
    Long requestedId = request.getEcoreFileId();
    FileStorage currentFile = metaModel.getEcoreFile();
    if (requestedId == null
        || Objects.equals(requestedId, currentFile == null ? null : currentFile.getId())) {
      return null;
    }
    return fileStorageRepository
        .findByIdAndTypeAndUser_EmailAndUser_RemovedAtIsNull(
            requestedId, FileEnumType.ECORE, callerEmail)
        .orElseThrow(() -> new NotFoundException(ECORE_FILE_ID_NOT_FOUND_ERROR));
  }

  boolean isOwnedBy(MetaModel metaModel, User user) {
    if (metaModel == null || metaModel.getUser() == null || user == null) {
      return false;
    }
    if (metaModel.getUser().getId() != null && user.getId() != null) {
      return metaModel.getUser().getId().equals(user.getId());
    }
    return metaModel.getUser().equals(user);
  }

  /**
   * Finds all metamodels accessible by a given project.
   *
   * <p>This method retrieves:
   *
   * <ul>
   *   <li>All metamodels associated with the project via VSUM (if vsumId is provided)
   * </ul>
   *
   * <p>Duplicates are automatically removed by using a Set internally. This method is primarily
   * used by the Language Server to determine which metamodels should be loaded for a specific
   * editing session.
   *
   * @param vsumId the ID of the project/VSUM whose metamodels should be included (can be null)
   * @return a list of unique MetaModel instances accessible by the user and/or project
   */
  @Transactional(readOnly = true)
  public List<MetaModel> findAccessibleByProject(Long vsumId) {
    log.info("findAccessibleByProject called with vsumId: {}", vsumId);

    if (vsumId == null) {
      log.warn("vsumId is null, returning empty list");
      return List.of();
    }

    List<VsumMetaModel> vsumMetaModels = vsumMetaModelRepository.findByVsumId(vsumId);
    log.info("Found {} VsumMetaModel entries for vsumId {}", vsumMetaModels.size(), vsumId);

    Set<MetaModel> metamodels = new LinkedHashSet<>();

    for (VsumMetaModel vmm : vsumMetaModels) {
      MetaModel mm = vmm.getMetaModel();
      log.info(
          "Processing MetaModel id={}, name={}, hasSource={}",
          mm.getId(),
          mm.getName(),
          mm.getSource() != null);

      if (mm.getSource() != null) {
        metamodels.add(mm.getSource());
      } else {
        metamodels.add(mm);
      }
    }

    log.info("Returning {} metamodels", metamodels.size());
    return new ArrayList<>(metamodels);
  }

  /**
   * Writes all accessible metamodels to the specified directory for LSP workspace initialization.
   *
   * @param targetDir directory where .ecore files should be writtenyy
   * @param vsumId project/VSUM whose metamodels should be included
   * @throws IOException if file writing fails
   */
  @Transactional(readOnly = true, noRollbackFor = IOException.class)
  public void writeMetamodelsToDirectory(File targetDir, Long vsumId) throws IOException {
    List<MetaModel> metamodels = self.findAccessibleByProject(vsumId);

    for (MetaModel mm : metamodels) {
      String fileName = mm.getEcoreFile().getFilename();
      File ecoreFile = new File(targetDir, fileName);
      Files.write(ecoreFile.toPath(), mm.getEcoreFile().getData());
    }
  }

  private record CreateContext(User user, MetaModel metaModel, FileStorage ecoreFile) {}

  /**
   * Result of {@link #create(String, MetaModelPostRequest)}.
   *
   * @param metaModel the created metamodel
   * @param message the message to return to the caller
   */
  public record MetaModelCreationResult(MetaModel metaModel, String message) {}
}
