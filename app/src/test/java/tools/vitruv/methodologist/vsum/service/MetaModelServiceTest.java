package tools.vitruv.methodologist.vsum.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static tools.vitruv.methodologist.messages.Error.ECORE_FILE_ID_NOT_FOUND_ERROR;
import static tools.vitruv.methodologist.messages.Error.GEN_MODEL_FILE_ID_NOT_FOUND_ERROR;
import static tools.vitruv.methodologist.messages.Error.USER_EMAIL_NOT_FOUND_ERROR;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.vitruv.methodologist.exception.CreateMwe2FileException;
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

class MetaModelServiceTest {

  private MetaModelMapper metaModelMapper;
  private MetaModelRepository metaModelRepository;
  private FileStorageRepository fileStorageRepository;
  private UserRepository userRepository;
  private MetamodelBuildService metamodelBuildService;

  private MetaModelService metaModelService;

  private static MetaModelPostRequest req(long ecoreId, long genId) {
    MetaModelPostRequest metaModelPostRequest = new MetaModelPostRequest();
    metaModelPostRequest.setName("mm1");
    metaModelPostRequest.setEcoreFileId(ecoreId);
    metaModelPostRequest.setGenModelFileId(genId);
    return metaModelPostRequest;
  }

  private static FileStorage fs(long id, FileEnumType type, byte[] data) {
    FileStorage fileStorage = new FileStorage();
    fileStorage.setId(id);
    fileStorage.setType(type);
    fileStorage.setData(data);
    return fileStorage;
  }

  private static MetaModel metaModel(Long id, FileStorage ecoreFile, FileStorage genModelFile) {
    MetaModel metaModel = new MetaModel();
    metaModel.setId(id);
    metaModel.setName("mm1");
    metaModel.setGenModelFile(genModelFile);
    metaModel.setEcoreFile(ecoreFile);
    return metaModel;
  }

  @BeforeEach
  void setup() {
    metaModelMapper = mock(MetaModelMapper.class);
    metaModelRepository = mock(MetaModelRepository.class);
    fileStorageRepository = mock(FileStorageRepository.class);
    userRepository = mock(UserRepository.class);
    metamodelBuildService = mock(MetamodelBuildService.class);

    metaModelService =
        new MetaModelService(
            metaModelMapper,
            metaModelRepository,
            fileStorageRepository,
            userRepository,
            metamodelBuildService);
  }

  @Test
  void create_success_whenBuildOk() {
    String email = "u@ex.com";
    var request = req(10L, 20L);
    var user = new User();
    user.setEmail(email);

    var ecore = fs(10L, FileEnumType.ECORE, "E".getBytes());
    var gen = fs(20L, FileEnumType.GEN_MODEL, "G".getBytes());

    var mapped = metaModel(null, ecore, gen);
    var saved = metaModel(100L, ecore, gen);

    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull(email))
        .thenReturn(Optional.of(user));
    when(fileStorageRepository.findByIdAndType(10L, FileEnumType.ECORE))
        .thenReturn(Optional.of(ecore));
    when(fileStorageRepository.findByIdAndType(20L, FileEnumType.GEN_MODEL))
        .thenReturn(Optional.of(gen));
    when(metaModelMapper.toMetaModel(request)).thenReturn(mapped);
    when(metaModelRepository.save(any(MetaModel.class))).thenReturn(saved);

    when(metamodelBuildService.buildAndValidate(any()))
        .thenReturn(
            MetamodelBuildService.BuildResult.builder()
                .success(true)
                .errors(0)
                .warnings(0)
                .report("OK")
                .discoveredNsUris("http://x")
                .build());

    MetaModel result = metaModelService.create(email, request);

    assertThat(result.getId()).isEqualTo(100L);
    ArgumentCaptor<MetamodelBuildService.MetamodelBuildInput> cap =
        ArgumentCaptor.forClass(MetamodelBuildService.MetamodelBuildInput.class);
    verify(metamodelBuildService).buildAndValidate(cap.capture());
    assertThat(cap.getValue().getEcoreBytes()).containsExactly(ecore.getData());
    assertThat(cap.getValue().getGenModelBytes()).containsExactly(gen.getData());
    assertThat(cap.getValue().isRunMwe2()).isTrue();
  }

  @Test
  void create_throwsCreateMwe2FileException_whenBuildFails() {
    String email = "u@ex.com";
    var request = req(10L, 20L);
    var user = new User();
    user.setEmail(email);
    var ecore = fs(10L, FileEnumType.ECORE, "E".getBytes());
    var gen = fs(20L, FileEnumType.GEN_MODEL, "G".getBytes());
    var mapped = metaModel(null, ecore, gen);
    var saved = metaModel(100L, ecore, gen);

    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull(email))
        .thenReturn(Optional.of(user));
    when(fileStorageRepository.findByIdAndType(10L, FileEnumType.ECORE))
        .thenReturn(Optional.of(ecore));
    when(fileStorageRepository.findByIdAndType(20L, FileEnumType.GEN_MODEL))
        .thenReturn(Optional.of(gen));
    when(metaModelMapper.toMetaModel(request)).thenReturn(mapped);
    when(metaModelRepository.save(any(MetaModel.class))).thenReturn(saved);

    when(metamodelBuildService.buildAndValidate(any()))
        .thenReturn(
            MetamodelBuildService.BuildResult.builder()
                .success(false)
                .errors(1)
                .warnings(0)
                .report("bad model")
                .build());

    assertThatThrownBy(() -> metaModelService.create(email, request))
        .isInstanceOf(CreateMwe2FileException.class)
        .hasMessageContaining("bad model");
  }

  @Test
  void create_throwsNotFound_whenUserMissing() {
    String email = "missing@ex.com";
    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull(email))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> metaModelService.create(email, req(10L, 20L)))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining(USER_EMAIL_NOT_FOUND_ERROR);
  }

  @Test
  void create_throwsNotFound_whenEcoreMissing() {
    String email = "u@ex.com";
    var user = new User();
    user.setEmail(email);

    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull(email))
        .thenReturn(Optional.of(user));
    when(fileStorageRepository.findByIdAndType(10L, FileEnumType.ECORE))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> metaModelService.create(email, req(10L, 20L)))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining(ECORE_FILE_ID_NOT_FOUND_ERROR);
  }

  @Test
  void create_throwsNotFound_whenGenModelMissing() {
    String email = "u@ex.com";
    var user = new User();
    user.setEmail(email);
    var ecore = fs(10L, FileEnumType.ECORE, "E".getBytes());

    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull(email))
        .thenReturn(Optional.of(user));
    when(fileStorageRepository.findByIdAndType(10L, FileEnumType.ECORE))
        .thenReturn(Optional.of(ecore));
    when(fileStorageRepository.findByIdAndType(20L, FileEnumType.GEN_MODEL))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> metaModelService.create(email, req(10L, 20L)))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining(GEN_MODEL_FILE_ID_NOT_FOUND_ERROR);
  }

  @Test
  void findAllByUser_mapsToResponses() {
    String email = "u@ex.com";
    var m1 = metaModel(1L, null, null);
    var m2 = metaModel(2L, null, null);
    when(metaModelRepository.findAllByUser_email(email)).thenReturn(List.of(m1, m2));

    var r1 = new MetaModelResponse();
    r1.setId(1L);
    r1.setName("mm1");
    var r2 = new MetaModelResponse();
    r2.setId(2L);
    r2.setName("mm2");
    when(metaModelMapper.toMetaModelResponse(m1)).thenReturn(r1);
    when(metaModelMapper.toMetaModelResponse(m2)).thenReturn(r2);

    var list = metaModelService.findAllByUser(email);

    assertThat(list).extracting(MetaModelResponse::getId).containsExactly(1L, 2L);
    assertThat(list).extracting(MetaModelResponse::getName).containsExactly("mm1", "mm2");
  }
}
