package tools.vitruv.methodologist.vsum.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tools.vitruv.methodologist.messages.Error.ECORE_FILE_ID_NOT_FOUND_ERROR;
import static tools.vitruv.methodologist.messages.Error.GEN_MODEL_FILE_ID_NOT_FOUND_ERROR;
import static tools.vitruv.methodologist.messages.Error.USER_EMAIL_NOT_FOUND_ERROR;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
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
    final MetaModelPostRequest metaModelPostRequest = new MetaModelPostRequest();
    metaModelPostRequest.setName("mm1");
    metaModelPostRequest.setEcoreFileId(ecoreId);
    metaModelPostRequest.setGenModelFileId(genId);
    return metaModelPostRequest;
  }

  private static FileStorage fs(long id, FileEnumType type, byte[] data) {
    final FileStorage fileStorage = new FileStorage();
    fileStorage.setId(id);
    fileStorage.setType(type);
    fileStorage.setData(data);
    return fileStorage;
  }

  private static MetaModel metaModel(Long id, FileStorage ecoreFile, FileStorage genModelFile) {
    final MetaModel metaModel = new MetaModel();
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
    final String email = "u@ex.com";

    final MetaModelPostRequest request = req(10L, 20L);
    final User user = new User();
    user.setEmail(email);
    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull(email))
        .thenReturn(Optional.of(user));

    final FileStorage ecore = fs(10L, FileEnumType.ECORE, "E".getBytes());
    when(fileStorageRepository.findByIdAndType(10L, FileEnumType.ECORE))
        .thenReturn(Optional.of(ecore));

    final FileStorage gen = fs(20L, FileEnumType.GEN_MODEL, "G".getBytes());
    when(fileStorageRepository.findByIdAndType(20L, FileEnumType.GEN_MODEL))
        .thenReturn(Optional.of(gen));

    final MetaModel mapped = metaModel(null, ecore, gen);
    when(metaModelMapper.toMetaModel(request)).thenReturn(mapped);

    final MetaModel saved = metaModel(100L, ecore, gen);
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

    final MetaModel result = metaModelService.create(email, request);

    assertThat(result.getId()).isEqualTo(100L);

    final ArgumentCaptor<MetamodelBuildService.MetamodelBuildInput> captor =
        ArgumentCaptor.forClass(MetamodelBuildService.MetamodelBuildInput.class);
    verify(metamodelBuildService).buildAndValidate(captor.capture());
    assertThat(captor.getValue().getEcoreBytes()).containsExactly(ecore.getData());
    assertThat(captor.getValue().getGenModelBytes()).containsExactly(gen.getData());
    assertThat(captor.getValue().isRunMwe2()).isTrue();
  }

  @Test
  void create_throwsCreateMwe2FileException_whenBuildFails() {
    final String email = "u@ex.com";

    final MetaModelPostRequest request = req(10L, 20L);
    final User user = new User();
    user.setEmail(email);
    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull(email))
        .thenReturn(Optional.of(user));

    final FileStorage ecore = fs(10L, FileEnumType.ECORE, "E".getBytes());
    when(fileStorageRepository.findByIdAndType(10L, FileEnumType.ECORE))
        .thenReturn(Optional.of(ecore));

    final FileStorage gen = fs(20L, FileEnumType.GEN_MODEL, "G".getBytes());
    when(fileStorageRepository.findByIdAndType(20L, FileEnumType.GEN_MODEL))
        .thenReturn(Optional.of(gen));

    final MetaModel mapped = metaModel(null, ecore, gen);
    when(metaModelMapper.toMetaModel(request)).thenReturn(mapped);

    final MetaModel saved = metaModel(100L, ecore, gen);
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
    final String email = "missing@ex.com";
    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull(email))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> metaModelService.create(email, req(10L, 20L)))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining(USER_EMAIL_NOT_FOUND_ERROR);
  }

  @Test
  void create_throwsNotFound_whenEcoreMissing() {
    final String email = "u@ex.com";
    final User user = new User();
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
    final String email = "u@ex.com";
    final User user = new User();
    user.setEmail(email);
    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull(email))
        .thenReturn(Optional.of(user));

    final FileStorage ecore = fs(10L, FileEnumType.ECORE, "E".getBytes());
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
    final String email = "u@ex.com";

    final MetaModel m1 = metaModel(1L, null, null);
    final MetaModel m2 = metaModel(2L, null, null);
    when(metaModelRepository.findAll(any(), any())).thenReturn(List.of(m1, m2));

    final MetaModelResponse r1 = new MetaModelResponse();
    r1.setId(1L);
    r1.setName("mm1");

    final MetaModelResponse r2 = new MetaModelResponse();
    r2.setId(2L);
    r2.setName("mm2");

    when(metaModelMapper.toMetaModelResponse(m1)).thenReturn(r1);
    when(metaModelMapper.toMetaModelResponse(m2)).thenReturn(r2);

    final List<MetaModelResponse> list =
        metaModelService.findAllByUser(email, null, Pageable.ofSize(1));

    assertThat(list).extracting(MetaModelResponse::getId).containsExactly(1L, 2L);
    assertThat(list).extracting(MetaModelResponse::getName).containsExactly("mm1", "mm2");
  }
}
