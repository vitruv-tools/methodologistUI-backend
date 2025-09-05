package tools.vitruv.methodologist.vsum.service;

import static tools.vitruv.methodologist.messages.Error.ECORE_FILE_ID_NOT_FOUND_ERROR;
import static tools.vitruv.methodologist.messages.Error.GEN_MODEL_FILE_ID_NOT_FOUND_ERROR;
import static tools.vitruv.methodologist.messages.Error.USER_EMAIL_NOT_FOUND_ERROR;

import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.vitruv.methodologist.exception.CreateMwe2FileException;
import tools.vitruv.methodologist.exception.NotFoundException;
import tools.vitruv.methodologist.general.FileEnumType;
import tools.vitruv.methodologist.general.model.FileStorage;
import tools.vitruv.methodologist.general.model.repository.FileStorageRepository;
import tools.vitruv.methodologist.general.service.FileStorageService;
import tools.vitruv.methodologist.user.model.User;
import tools.vitruv.methodologist.user.model.repository.UserRepository;
import tools.vitruv.methodologist.vsum.controller.dto.request.MetaModelFilterRequest;
import tools.vitruv.methodologist.vsum.controller.dto.request.MetaModelPostRequest;
import tools.vitruv.methodologist.vsum.controller.dto.response.MetaModelResponse;
import tools.vitruv.methodologist.vsum.mapper.MetaModelMapper;
import tools.vitruv.methodologist.vsum.model.MetaModel;
import tools.vitruv.methodologist.vsum.model.repository.MetaModelRepository;
import tools.vitruv.methodologist.vsum.model.repository.MetaModelSpecifications;
import tools.vitruv.methodologist.vsum.service.MetamodelBuildService.BuildResult;

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
public class MetaModelService {
  private final MetaModelMapper metaModelMapper;
  private final MetaModelRepository metaModelRepository;
  private final FileStorageRepository fileStorageRepository;
  private final UserRepository userRepository;
  private final MetamodelBuildService metamodelBuildService;
  private final FileStorageService fileStorageService;

  /**
   * Constructs a new MetaModelService with the specified dependencies.
   *
   * @param metaModelMapper mapper for MetaModel conversions
   * @param metaModelRepository repository for metamodel operations
   * @param fileStorageRepository repository for file storage operations
   * @param userRepository repository for user operations
   */
  public MetaModelService(
      MetaModelMapper metaModelMapper,
      MetaModelRepository metaModelRepository,
      FileStorageRepository fileStorageRepository,
      UserRepository userRepository,
      MetamodelBuildService metamodelBuildService,
      FileStorageService fileStorageService) {
    this.metaModelMapper = metaModelMapper;
    this.metaModelRepository = metaModelRepository;
    this.fileStorageRepository = fileStorageRepository;
    this.userRepository = userRepository;
    this.metamodelBuildService = metamodelBuildService;
    this.fileStorageService = fileStorageService;
  }

  /**
   * Saves a new MetaModel linked to the given user and the uploaded Ecore/GenModel files. Persists
   * the MetaModel and returns it together with the raw file data. Throws NotFoundException if the
   * user or files cannot be found.
   */
  @Transactional
  protected PairAndModel savePendingAndLoad(String callerEmail, MetaModelPostRequest req) {
    User user =
        userRepository
            .findByEmailIgnoreCaseAndRemovedAtIsNull(callerEmail)
            .orElseThrow(() -> new NotFoundException(USER_EMAIL_NOT_FOUND_ERROR));

    MetaModel metaModel = metaModelMapper.toMetaModel(req);

    FileStorage ecoreFile =
        fileStorageRepository
            .findByIdAndType(req.getEcoreFileId(), FileEnumType.ECORE)
            .orElseThrow(() -> new NotFoundException(ECORE_FILE_ID_NOT_FOUND_ERROR));

    FileStorage genModelFile =
        fileStorageRepository
            .findByIdAndType(req.getGenModelFileId(), FileEnumType.GEN_MODEL)
            .orElseThrow(() -> new NotFoundException(GEN_MODEL_FILE_ID_NOT_FOUND_ERROR));

    metaModel.setUser(user);
    metaModel.setEcoreFile(ecoreFile);
    metaModel.setGenModelFile(genModelFile);

    metaModel = metaModelRepository.save(metaModel);

    FilePair files = new FilePair(ecoreFile.getData(), genModelFile.getData());
    return new PairAndModel(metaModel, files);
  }

  /** Creates a metamodel, runs headless build, and accepts/rejects it. */
  @Transactional
  public MetaModel create(String callerEmail, MetaModelPostRequest req) {
    PairAndModel pairAndModel = savePendingAndLoad(callerEmail, req);
    MetaModel metaModel = pairAndModel.metaModel;

    BuildResult result =
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
  public List<MetaModelResponse> findAllByUser(
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
  @Transactional
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
  public void deleteCloned(List<MetaModel> metaModels) {
    metaModelRepository.deleteAll(metaModels);
    List<FileStorage> fileStorages = new ArrayList<>();
    fileStorages.addAll(metaModels.stream().map(MetaModel::getEcoreFile).toList());
    fileStorages.addAll(metaModels.stream().map(MetaModel::getGenModelFile).toList());
    fileStorageService.deleteFiles(fileStorages);
  }

  /** Holds a MetaModel and its associated file pair. */
  private static final class PairAndModel {
    final MetaModel metaModel;
    final FilePair files;

    PairAndModel(MetaModel metaModel, FilePair files) {
      this.metaModel = metaModel;
      this.files = files;
    }
  }

  /** Pair of raw Ecore and GenModel file data. */
  private record FilePair(byte[] ecore, byte[] gen) {}
}
