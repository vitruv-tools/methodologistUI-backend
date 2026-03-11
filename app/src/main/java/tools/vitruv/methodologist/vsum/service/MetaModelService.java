package tools.vitruv.methodologist.vsum.service;

import static tools.vitruv.methodologist.messages.Error.ECORE_FILE_ID_NOT_FOUND_ERROR;
import static tools.vitruv.methodologist.messages.Error.GEN_MODEL_FILE_ID_NOT_FOUND_ERROR;
import static tools.vitruv.methodologist.messages.Error.META_MODEL_ID_NOT_FOUND_ERROR;
import static tools.vitruv.methodologist.messages.Error.USER_DOSE_NOT_HAVE_ACCESS;
import static tools.vitruv.methodologist.messages.Error.USER_EMAIL_NOT_FOUND_ERROR;
import static tools.vitruv.methodologist.messages.Message.FOUND_ISSUE_IN_GEN_MODEL;
import static tools.vitruv.methodologist.messages.Message.PRE_CHECK_GEN_MODEL_ABORTED;
import static tools.vitruv.methodologist.messages.Message.PRE_CHECK_GEN_MODEL_FAILED;
import static tools.vitruv.methodologist.messages.Message.PRE_CHECK_GEN_MODEL_UNKNOWN;

import jakarta.validation.Valid;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.vitruv.methodologist.exception.CreateMwe2FileException;
import tools.vitruv.methodologist.exception.MetaModelUsedInVsumException;
import tools.vitruv.methodologist.exception.NotFoundException;
import tools.vitruv.methodologist.general.FileEnumType;
import tools.vitruv.methodologist.general.model.FileStorage;
import tools.vitruv.methodologist.general.model.repository.FileStorageRepository;
import tools.vitruv.methodologist.general.service.FileStorageService;
import tools.vitruv.methodologist.user.model.User;
import tools.vitruv.methodologist.user.model.repository.UserRepository;
import tools.vitruv.methodologist.vitruvcli.GenModelPrecheckStatus;
import tools.vitruv.methodologist.vsum.controller.dto.request.MetaModelFilterRequest;
import tools.vitruv.methodologist.vsum.controller.dto.request.MetaModelPostRequest;
import tools.vitruv.methodologist.vsum.controller.dto.request.MetaModelPutRequest;
import tools.vitruv.methodologist.vsum.controller.dto.response.MetaModelResponse;
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

  private static final int PRECHECK_SUMMARY_MAX_LENGTH = 160;

  MetaModelService self;
  MetaModelMapper metaModelMapper;
  MetaModelRepository metaModelRepository;
  FileStorageRepository fileStorageRepository;
  UserRepository userRepository;
  MetamodelBuildService metamodelBuildService;
  FileStorageService fileStorageService;
  MetaModelVitruvIntegrationService metaModelVitruvIntegrationService;
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
   * @param metaModelVitruvIntegrationService service for executing Vitruv CLI workflows on
   *     metamodel files
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
      MetaModelVitruvIntegrationService metaModelVitruvIntegrationService,
      VsumMetaModelRepository vsumMetaModelRepository) {
    this.self = self;
    this.metaModelMapper = metaModelMapper;
    this.metaModelRepository = metaModelRepository;
    this.fileStorageRepository = fileStorageRepository;
    this.userRepository = userRepository;
    this.metamodelBuildService = metamodelBuildService;
    this.fileStorageService = fileStorageService;
    this.metaModelVitruvIntegrationService = metaModelVitruvIntegrationService;
    this.vsumMetaModelRepository = vsumMetaModelRepository;
  }

  /**
   * Loads the user, mapped metamodel, and referenced files required to create a metamodel.
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

    FileStorage genModelFile =
        fileStorageRepository
            .findByIdAndTypeAndUser_EmailAndUser_RemovedAtIsNull(
                req.getGenModelFileId(), FileEnumType.GEN_MODEL, callerEmail)
            .orElseThrow(() -> new NotFoundException(GEN_MODEL_FILE_ID_NOT_FOUND_ERROR));

    return new CreateContext(user, metaModel, ecoreFile, genModelFile);
  }

  private MetaModel savePendingMetaModel(CreateContext createContext) {
    MetaModel metaModel = createContext.metaModel();
    metaModel.setUser(createContext.user());
    metaModel.setEcoreFile(createContext.ecoreFile());
    metaModel.setGenModelFile(createContext.genModelFile());
    return metaModelRepository.save(metaModel);
  }

  private void precheckGenModelOrThrow(CreateContext createContext, boolean applyGenModelFixes) {
    MetaModelVitruvIntegrationService.GenModelPrecheckExecutionResult result =
        metaModelVitruvIntegrationService.precheckGenModels(
            List.of(createContext.ecoreFile()),
            List.of(createContext.genModelFile()),
            applyGenModelFixes);

    if (!result.isSuccess()) {
      logPrecheckFailure(result);
      throw new CreateMwe2FileException(precheckFailureMessage(result));
    }

    if (result.shouldOverwriteGenModels()) {
      fileStorageService.overwriteStoredContent(
          createContext.genModelFile(), result.getUpdatedGenModelBytes().get(0));
    }
  }

  private String precheckFailureMessage(
      MetaModelVitruvIntegrationService.GenModelPrecheckExecutionResult result) {
    if (result.getStatus() == GenModelPrecheckStatus.ISSUES_FOUND) {
      return FOUND_ISSUE_IN_GEN_MODEL;
    }

    if (result.getStatus() == GenModelPrecheckStatus.ABORTED) {
      return PRE_CHECK_GEN_MODEL_ABORTED;
    }

    String summary = extractClientSafePrecheckSummary(result);
    String details = PRE_CHECK_GEN_MODEL_FAILED + result.getStatus();
    if (summary != null) {
      details += ". Summary: " + summary;
    }

    if (result.getStatus() == GenModelPrecheckStatus.UNKNOWN) {
      return PRE_CHECK_GEN_MODEL_UNKNOWN + details;
    }
    return details;
  }

  private void logPrecheckFailure(
      MetaModelVitruvIntegrationService.GenModelPrecheckExecutionResult result) {
    log.warn(
        "GenModel precheck failed with status={}, exitCode={}, stdout={}, stderr={}",
        result.getStatus(),
        result.getExitCode(),
        result.getStdout(),
        result.getStderr());
  }

  private String extractClientSafePrecheckSummary(
      MetaModelVitruvIntegrationService.GenModelPrecheckExecutionResult result) {
    String stderrSummary = extractClientSafePrecheckSummary(result.getStderr());
    if (stderrSummary != null) {
      return stderrSummary;
    }
    return extractClientSafePrecheckSummary(result.getStdout());
  }

  private String extractClientSafePrecheckSummary(String output) {
    if (output == null || output.isBlank()) {
      return null;
    }

    return output
        .lines()
        .map(String::trim)
        .filter(this::isClientSafePrecheckSummaryLine)
        .map(this::truncatePrecheckSummary)
        .findFirst()
        .orElse(null);
  }

  private boolean isClientSafePrecheckSummaryLine(String line) {
    if (line == null || line.isBlank()) {
      return false;
    }

    return !line.startsWith("GENMODEL_PRECHECK_STATUS:")
        && !line.startsWith("Exception in thread")
        && !line.startsWith("Caused by:")
        && !line.startsWith("at ")
        && !line.startsWith("INFO:")
        && !line.contains(".java:")
        && !line.contains("/")
        && !line.contains("\\");
  }

  private String truncatePrecheckSummary(String line) {
    String normalized = line.replaceAll("\\s+", " ").trim();
    if (normalized.length() <= PRECHECK_SUMMARY_MAX_LENGTH) {
      return normalized;
    }
    return normalized.substring(0, PRECHECK_SUMMARY_MAX_LENGTH - 3) + "...";
  }

  /** Creates a metamodel, runs GenModel precheck, then headless build, and accepts/rejects it. */
  @Transactional
  public MetaModel create(String callerEmail, MetaModelPostRequest req) {
    CreateContext createContext = prepareCreateContext(callerEmail, req);
    precheckGenModelOrThrow(createContext, req.isApplyGenModelFixes());

    MetaModel metaModel = savePendingMetaModel(createContext);

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

    return metaModel;
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
    return metaModels.stream().map(metaModelMapper::toMetaModelResponse).toList();
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
   * referencing VSUM names.
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
              .map(Vsum::getName)
              .map(String::valueOf)
              .collect(Collectors.joining(",")));
    }

    metaModelRepository.delete(metaModel);
    fileStorageService.deleteFiles(List.of(metaModel.getEcoreFile(), metaModel.getGenModelFile()));
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

      metaModelMapper.updateByMetaModelPutRequest(metaModelPutRequest, metaModel);
      metaModelRepository.save(metaModel);
      return;
    }

    MetaModel source = metaModel.getSource();

    if (isOwnedBy(source, user)) {
      metaModelMapper.updateByMetaModelPutRequest(metaModelPutRequest, source);
      metaModelMapper.updateByMetaModelPutRequest(metaModelPutRequest, metaModel);

      metaModelRepository.saveAll(List.of(source, metaModel));
      return;
    }

    MetaModel newSource = clone(source);
    newSource.setUser(user);
    newSource.setSource(null);

    metaModelMapper.updateByMetaModelPutRequest(metaModelPutRequest, newSource);
    metaModelMapper.updateByMetaModelPutRequest(metaModelPutRequest, metaModel);

    metaModelRepository.save(newSource);

    metaModel.setSource(newSource);
    metaModelRepository.save(metaModel);
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
  @Transactional(readOnly = true)
  public void writeMetamodelsToDirectory(File targetDir, Long vsumId) throws IOException {
    List<MetaModel> metamodels = self.findAccessibleByProject(vsumId);

    for (MetaModel mm : metamodels) {
      String fileName = mm.getEcoreFile().getFilename();
      File ecoreFile = new File(targetDir, fileName);
      Files.write(ecoreFile.toPath(), mm.getEcoreFile().getData());
    }
  }

  private record CreateContext(
      User user, MetaModel metaModel, FileStorage ecoreFile, FileStorage genModelFile) {}
}
