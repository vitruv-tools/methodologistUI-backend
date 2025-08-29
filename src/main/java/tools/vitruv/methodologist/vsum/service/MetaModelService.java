package tools.vitruv.methodologist.vsum.service;

import static tools.vitruv.methodologist.messages.Error.ECORE_FILE_ID_NOT_FOUND_ERROR;
import static tools.vitruv.methodologist.messages.Error.GEN_MODEL_FILE_ID_NOT_FOUND_ERROR;
import static tools.vitruv.methodologist.messages.Error.USER_EMAIL_NOT_FOUND_ERROR;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.vitruv.methodologist.exception.NotFoundException;
import tools.vitruv.methodologist.general.FileEnumType;
import tools.vitruv.methodologist.general.model.FileStorage;
import tools.vitruv.methodologist.general.model.repository.FileStorageRepository;
import tools.vitruv.methodologist.user.model.User;
import tools.vitruv.methodologist.user.model.repository.UserRepository;
import tools.vitruv.methodologist.vsum.controller.dto.request.MetaModelPostRequest;
import tools.vitruv.methodologist.vsum.controller.dto.response.MetaModelResponse;
import tools.vitruv.methodologist.vsum.mapper.MetaModelMapper;
import tools.vitruv.methodologist.vsum.model.MetaModel;
import tools.vitruv.methodologist.vsum.model.repository.MetaModelRepository;

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
      MetamodelBuildService metamodelBuildService // ✅ inject it
      ) {
    this.metaModelMapper = metaModelMapper;
    this.metaModelRepository = metaModelRepository;
    this.fileStorageRepository = fileStorageRepository;
    this.userRepository = userRepository;
    this.metamodelBuildService = metamodelBuildService;
  }

  @Transactional
  protected PairAndModel savePendingAndLoad(String callerEmail, MetaModelPostRequest req) {
    User user =
        userRepository
            .findByEmailIgnoreCaseAndRemovedAtIsNull(callerEmail)
            .orElseThrow(() -> new NotFoundException(USER_EMAIL_NOT_FOUND_ERROR));

    MetaModel mm = metaModelMapper.toMetaModel(req);

    var ecoreFile =
        fileStorageRepository
            .findByIdAndType(req.getEcoreFileId(), FileEnumType.ECORE)
            .orElseThrow(() -> new NotFoundException(ECORE_FILE_ID_NOT_FOUND_ERROR));

    var genModelFile =
        fileStorageRepository
            .findByIdAndType(req.getGenModelFileId(), FileEnumType.GEN_MODEL)
            .orElseThrow(() -> new NotFoundException(GEN_MODEL_FILE_ID_NOT_FOUND_ERROR));

    mm.setUser(user);
    mm.setEcoreFile(ecoreFile);
    mm.setGenModelFile(genModelFile);

    mm = metaModelRepository.save(mm);

    // MATERIALIZE bytes while the session is open
    FilePair files = new FilePair(ecoreFile.getData(), genModelFile.getData());

    return new PairAndModel(mm, files);
  }

  /** Creates a metamodel, runs headless build, and accepts/rejects it. */
  public MetaModel create(String callerEmail, MetaModelPostRequest req) {
    // Tx: save + load bytes
    PairAndModel p = savePendingAndLoad(callerEmail, req);
    MetaModel mm = p.mm;
    FilePair files = p.files;

    // Run isolated build (no TX)
    var result =
        metamodelBuildService.buildAndValidate(
            MetamodelBuildService.MetamodelBuildInput.builder()
                .metaModelId(mm.getId())
                .ecoreBytes(files.ecore())
                .genModelBytes(files.gen())
                .runMwe2(true)
                .build());

    // Decide accept/reject (keep it simple)
    if (!result.isSuccess()) {
      // optionally store result.getReport() somewhere
      throw new RuntimeException("Metamodel rejected: " + result.getReport());
    }

    // success → just return (or mark accepted if you have a status field)
    return mm;
  }

  @Transactional(readOnly = true)
  protected MetaModel savePending(String callerEmail, MetaModelPostRequest req) {
    User user =
        userRepository
            .findByEmailIgnoreCaseAndRemovedAtIsNull(callerEmail)
            .orElseThrow(() -> new NotFoundException(USER_EMAIL_NOT_FOUND_ERROR));

    MetaModel metaModel = metaModelMapper.toMetaModel(req);

    fileStorageRepository.findAll();
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
    //    metaModel.setStatus(MetaModelStatus.PENDING);          // ✅ track status
    //    metaModel.setErrorsCount(0);
    //    metaModel.setWarningsCount(0);
    //    metaModel.setValidationReport(null);
    //    metaModel.setNsUris(null);

    return metaModelRepository.save(metaModel);
  }

  @Transactional
  protected void markAccepted(Long id, String report, int warnings, String nsUris) {
    MetaModel mm = metaModelRepository.findById(id).orElseThrow();
    //    mm.setStatus(MetaModelStatus.ACCEPTED);
    //    mm.setWarningsCount(warnings);
    //    mm.setErrorsCount(0);
    //    mm.setValidationReport(safe(report));
    //    mm.setNsUris(nsUris);
    metaModelRepository.save(mm);
  }

  @Transactional
  protected void markFailed(Long id, String report) {
    MetaModel mm = metaModelRepository.findById(id).orElseThrow();
    //    mm.setStatus(MetaModelStatus.FAILED);
    //    mm.setErrorsCount((mm.getErrorsCount() == null ? 0 : mm.getErrorsCount()) + 1);
    //    mm.setValidationReport(safe(report));
    metaModelRepository.save(mm);
  }

  private String safe(String s) {
    if (s == null) return null;
    return s.length() <= 8000 ? s : s.substring(0, 8000);
  }

  /**
   * Retrieves all metamodels associated with a specific user.
   *
   * @param callerEmail email of the user whose metamodels to retrieve
   * @return list of MetaModelResponse DTOs containing the metamodel details
   */
  @Transactional
  public List<MetaModelResponse> findAllByUser(String callerEmail) {
    var metaModels = metaModelRepository.findAllByUser_email(callerEmail);
    return metaModels.stream().map(metaModelMapper::toMetaModelResponse).toList();
  }

  public enum MetaModelStatus {
    PENDING,
    ACCEPTED,
    FAILED
  }

  // small holder
  private static final class PairAndModel {
    final MetaModel mm;
    final FilePair files;

    PairAndModel(MetaModel mm, FilePair files) {
      this.mm = mm;
      this.files = files;
    }
  }

  private record FilePair(byte[] ecore, byte[] gen) {}
}
