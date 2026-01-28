package tools.vitruv.methodologist.vsum.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tools.vitruv.methodologist.messages.Error.ECORE_FILE_ID_NOT_FOUND_ERROR;
import static tools.vitruv.methodologist.messages.Error.GEN_MODEL_FILE_ID_NOT_FOUND_ERROR;
import static tools.vitruv.methodologist.messages.Error.META_MODEL_ID_NOT_FOUND_ERROR;
import static tools.vitruv.methodologist.messages.Error.USER_DOSE_NOT_HAVE_ACCESS;
import static tools.vitruv.methodologist.messages.Error.USER_EMAIL_NOT_FOUND_ERROR;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import tools.vitruv.methodologist.exception.CreateMwe2FileException;
import tools.vitruv.methodologist.exception.MetaModelUsedInVsumException;
import tools.vitruv.methodologist.exception.NotFoundException;
import tools.vitruv.methodologist.general.FileEnumType;
import tools.vitruv.methodologist.general.model.FileStorage;
import tools.vitruv.methodologist.general.model.repository.FileStorageRepository;
import tools.vitruv.methodologist.general.service.FileStorageService;
import tools.vitruv.methodologist.user.model.User;
import tools.vitruv.methodologist.user.model.repository.UserRepository;
import tools.vitruv.methodologist.vsum.controller.dto.request.MetaModelFilterRequest;
import tools.vitruv.methodologist.vsum.controller.dto.request.MetaModelPostRequest;
import tools.vitruv.methodologist.vsum.controller.dto.request.MetaModelPutRequest;
import tools.vitruv.methodologist.vsum.controller.dto.response.MetaModelResponse;
import tools.vitruv.methodologist.vsum.mapper.MetaModelMapper;
import tools.vitruv.methodologist.vsum.model.MetaModel;
import tools.vitruv.methodologist.vsum.model.Vsum;
import tools.vitruv.methodologist.vsum.model.VsumMetaModel;
import tools.vitruv.methodologist.vsum.model.repository.MetaModelRepository;
import tools.vitruv.methodologist.vsum.model.repository.VsumMetaModelRepository;

class MetaModelServiceTest {

  @TempDir Path tempDir;
  private MetaModelMapper metaModelMapper;
  private MetaModelRepository metaModelRepository;
  private FileStorageRepository fileStorageRepository;
  private UserRepository userRepository;
  private MetamodelBuildService metamodelBuildService;
  private FileStorageService fileStorageService;
  private VsumMetaModelRepository vsumMetaModelRepository;

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
    fileStorageService = mock(FileStorageService.class);
    metaModelMapper = mock(MetaModelMapper.class);
    metaModelRepository = mock(MetaModelRepository.class);
    fileStorageRepository = mock(FileStorageRepository.class);
    userRepository = mock(UserRepository.class);
    metamodelBuildService = mock(MetamodelBuildService.class);
    vsumMetaModelRepository = mock(VsumMetaModelRepository.class);

    metaModelService =
        new MetaModelService(
            metaModelMapper,
            metaModelRepository,
            fileStorageRepository,
            userRepository,
            metamodelBuildService,
            fileStorageService,
            vsumMetaModelRepository);
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
  void clone_copiesFiles_setsSource_andSaves() {
    FileStorage sourceEcore = new FileStorage();
    sourceEcore.setId(10L);
    sourceEcore.setData("x".getBytes());

    FileStorage sourceGen = new FileStorage();
    sourceGen.setId(11L);
    sourceGen.setData("y".getBytes());

    MetaModel source = new MetaModel();
    source.setId(1L);
    source.setEcoreFile(sourceEcore);
    source.setGenModelFile(sourceGen);

    MetaModel mappedClone = new MetaModel();
    when(metaModelMapper.clone(source)).thenReturn(mappedClone);

    FileStorage clonedEcore = new FileStorage();
    clonedEcore.setId(20L);
    FileStorage clonedGen = new FileStorage();
    clonedGen.setId(21L);

    when(fileStorageService.clone(sourceEcore)).thenReturn(clonedEcore);
    when(fileStorageService.clone(sourceGen)).thenReturn(clonedGen);
    when(metaModelRepository.save(mappedClone)).thenReturn(mappedClone);

    MetaModel result = metaModelService.clone(source);

    verify(metaModelMapper).clone(source);
    verify(fileStorageService).clone(sourceEcore);
    verify(fileStorageService).clone(sourceGen);
    verify(metaModelRepository).save(mappedClone);

    assertThat(result).isSameAs(mappedClone);
    assertThat(result.getSource()).isSameAs(source);
    assertThat(result.getEcoreFile()).isSameAs(clonedEcore);
    assertThat(result.getGenModelFile()).isSameAs(clonedGen);
  }

  @Test
  void clone_propagates_whenFileCloneFails() {
    FileStorage sourceEcore = new FileStorage();
    sourceEcore.setId(10L);
    FileStorage sourceGen = new FileStorage();
    sourceGen.setId(11L);

    MetaModel source = new MetaModel();
    source.setId(1L);
    source.setEcoreFile(sourceEcore);
    source.setGenModelFile(sourceGen);

    MetaModel mappedClone = new MetaModel();
    when(metaModelMapper.clone(source)).thenReturn(mappedClone);

    when(fileStorageService.clone(sourceEcore)).thenThrow(new RuntimeException("disk full"));

    assertThatThrownBy(() -> metaModelService.clone(source))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("disk full");

    verify(metaModelRepository, never()).save(any(MetaModel.class));
  }

  @Test
  void deleteCloned_deletesModels_thenDeletesBothFileTypes() {
    FileStorage ecoreA = new FileStorage();
    ecoreA.setId(100L);
    FileStorage genA = new FileStorage();
    genA.setId(101L);
    MetaModel cloneA = new MetaModel();
    cloneA.setId(1000L);
    cloneA.setEcoreFile(ecoreA);
    cloneA.setGenModelFile(genA);

    FileStorage ecoreB = new FileStorage();
    ecoreB.setId(200L);
    FileStorage genB = new FileStorage();
    genB.setId(201L);
    MetaModel cloneB = new MetaModel();
    cloneB.setId(2000L);
    cloneB.setEcoreFile(ecoreB);
    cloneB.setGenModelFile(genB);

    List<MetaModel> clones = List.of(cloneA, cloneB);

    ArgumentCaptor<List<FileStorage>> filesCaptor = ArgumentCaptor.forClass(List.class);

    metaModelService.deleteCloned(clones);

    verify(metaModelRepository).deleteAll(clones);
    verify(fileStorageService).deleteFiles(filesCaptor.capture());

    List<FileStorage> sentFiles = filesCaptor.getValue();
    assertThat(sentFiles)
        .extracting(FileStorage::getId)
        .containsExactlyInAnyOrder(
            ecoreA.getId(), genA.getId(),
            ecoreB.getId(), genB.getId());
  }

  @Test
  void deleteCloned_handlesEmptyList() {
    List<MetaModel> empty = new ArrayList<>();

    metaModelService.deleteCloned(empty);

    verify(metaModelRepository).deleteAll(empty);
    verify(fileStorageService).deleteFiles(List.of());
  }

  @Test
  void delete_success_whenNotUsedInVsum() {
    String email = "user@ex.com";
    Long id = 1L;
    MetaModel metaModel = new MetaModel();
    FileStorage ecore = new FileStorage();
    FileStorage gen = new FileStorage();
    metaModel.setEcoreFile(ecore);
    metaModel.setGenModelFile(gen);

    when(metaModelRepository.findByIdAndUser_Email(id, email)).thenReturn(Optional.of(metaModel));
    when(vsumMetaModelRepository.findAllByMetaModel_Source(metaModel)).thenReturn(List.of());

    metaModelService.delete(email, id);

    verify(fileStorageService, times(1)).deleteFiles(List.of(ecore, gen));
    verify(metaModelRepository).delete(metaModel);
  }

  @Test
  void delete_throwsNotFoundException_whenMetaModelMissing() {
    String email = "user@ex.com";
    Long id = 1L;
    when(metaModelRepository.findByIdAndUser_Email(id, email)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> metaModelService.delete(email, id))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining(META_MODEL_ID_NOT_FOUND_ERROR);
  }

  @Test
  void delete_throwsMetaModelUsingInVsumException_whenUsedInVsum() {
    String email = "user@ex.com";
    Long id = 1L;
    MetaModel metaModel = new MetaModel();
    Vsum vsum = new Vsum();
    vsum.setName("VSUM1");
    VsumMetaModel vsumMetaModel = new VsumMetaModel();
    vsumMetaModel.setVsum(vsum);

    when(metaModelRepository.findByIdAndUser_Email(id, email)).thenReturn(Optional.of(metaModel));
    when(vsumMetaModelRepository.findAllByMetaModel_Source(metaModel))
        .thenReturn(List.of(vsumMetaModel));

    assertThatThrownBy(() -> metaModelService.delete(email, id))
        .isInstanceOf(MetaModelUsedInVsumException.class)
        .hasMessageContaining("VSUM1");
  }

  @Test
  void findAll_returnsMappedResponses_whenRepositoryReturnsResults() {

    MetaModel mm1 = new MetaModel();
    mm1.setId(1L);
    MetaModel mm2 = new MetaModel();
    mm2.setId(2L);

    MetaModelResponse resp1 = new MetaModelResponse();
    MetaModelResponse resp2 = new MetaModelResponse();

    when(metaModelRepository.findAll(any(), any(Pageable.class))).thenReturn(List.of(mm1, mm2));
    when(metaModelMapper.toMetaModelResponse(mm1)).thenReturn(resp1);
    when(metaModelMapper.toMetaModelResponse(mm2)).thenReturn(resp2);

    String email = "u@ex.com";
    Pageable pageable = PageRequest.of(0, 10);
    MetaModelFilterRequest filter = new MetaModelFilterRequest();
    List<MetaModelResponse> result = metaModelService.findAll(email, filter, pageable);

    assertThat(result).containsExactly(resp1, resp2);

    verify(metaModelRepository).findAll(any(), any(Pageable.class));
    verify(metaModelMapper, times(1)).toMetaModelResponse(mm1);
    verify(metaModelMapper, times(1)).toMetaModelResponse(mm2);
  }

  @Test
  void findAll_returnsEmptyList_whenRepositoryReturnsNoResults() {
    when(metaModelRepository.findAll(any(), any(Pageable.class))).thenReturn(List.of());

    String email = "u@ex.com";
    MetaModelFilterRequest filter = new MetaModelFilterRequest();
    Pageable pageable = PageRequest.of(0, 10);
    List<MetaModelResponse> result = metaModelService.findAll(email, filter, pageable);

    assertThat(result).isNotNull().isEmpty();
    verify(metaModelRepository).findAll(any(), any(Pageable.class));
    verify(metaModelMapper, never()).toMetaModelResponse(any());
  }

  @Test
  void update_throwsAccessDenied_whenUserMissing() {
    String email = "missing@ex.com";
    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull(email))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> metaModelService.update(email, 1L, new MetaModelPutRequest()))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
        .hasMessageContaining(USER_DOSE_NOT_HAVE_ACCESS);

    verify(metaModelRepository, never()).findById(any());
    verify(metaModelRepository, never()).save(any());
    verify(metaModelRepository, never()).saveAll(any());
    verify(metaModelMapper, never()).updateByMetaModelPutRequest(any(), any());
  }

  @Test
  void update_throwsNotFound_whenMetaModelMissing() {
    String email = "u@ex.com";
    User user = new User();
    user.setId(1L);
    user.setEmail(email);

    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull(email))
        .thenReturn(Optional.of(user));
    when(metaModelRepository.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> metaModelService.update(email, 99L, new MetaModelPutRequest()))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining(META_MODEL_ID_NOT_FOUND_ERROR);

    verify(metaModelRepository, never()).save(any());
    verify(metaModelRepository, never()).saveAll(any());
    verify(metaModelMapper, never()).updateByMetaModelPutRequest(any(), any());
  }

  @Test
  void update_throwsAccessDenied_whenOriginalMetaModelNotOwned() {
    String email = "u@ex.com";
    User caller = new User();
    caller.setId(1L);
    caller.setEmail(email);

    User other = new User();
    other.setId(2L);

    MetaModel metaModel = new MetaModel();
    metaModel.setId(10L);
    metaModel.setSource(null);
    metaModel.setUser(other);

    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull(email))
        .thenReturn(Optional.of(caller));
    when(metaModelRepository.findById(10L)).thenReturn(Optional.of(metaModel));

    assertThatThrownBy(() -> metaModelService.update(email, 10L, new MetaModelPutRequest()))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
        .hasMessageContaining(USER_DOSE_NOT_HAVE_ACCESS);

    verify(metaModelMapper, never()).updateByMetaModelPutRequest(any(), any());
    verify(metaModelRepository, never()).save(any());
    verify(metaModelRepository, never()).saveAll(any());
  }

  @Test
  void update_updatesAndSaves_whenOriginalMetaModelOwned() {
    String email = "u@ex.com";
    User caller = new User();
    caller.setId(1L);
    caller.setEmail(email);

    MetaModel metaModel = new MetaModel();
    metaModel.setId(10L);
    metaModel.setSource(null);
    metaModel.setUser(caller);

    MetaModelPutRequest req = new MetaModelPutRequest();

    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull(email))
        .thenReturn(Optional.of(caller));
    when(metaModelRepository.findById(10L)).thenReturn(Optional.of(metaModel));

    metaModelService.update(email, 10L, req);

    verify(metaModelMapper, times(1)).updateByMetaModelPutRequest(req, metaModel);
    verify(metaModelRepository, times(1)).save(metaModel);
    verify(metaModelRepository, never()).saveAll(any());
  }

  @Test
  void update_updatesBothAndSaveAll_whenDerivedAndSourceOwned() {
    String email = "u@ex.com";
    User caller = new User();
    caller.setId(1L);
    caller.setEmail(email);

    MetaModel source = new MetaModel();
    source.setId(1L);
    source.setUser(caller);

    MetaModel derived = new MetaModel();
    derived.setId(2L);
    derived.setUser(new User());
    derived.setSource(source);

    MetaModelPutRequest req = new MetaModelPutRequest();

    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull(email))
        .thenReturn(Optional.of(caller));
    when(metaModelRepository.findById(2L)).thenReturn(Optional.of(derived));

    metaModelService.update(email, 2L, req);

    verify(metaModelMapper).updateByMetaModelPutRequest(req, source);
    verify(metaModelMapper).updateByMetaModelPutRequest(req, derived);

    ArgumentCaptor<List<MetaModel>> captor = ArgumentCaptor.forClass(List.class);
    verify(metaModelRepository, times(1)).saveAll(captor.capture());
    assertThat(captor.getValue()).containsExactlyInAnyOrder(source, derived);

    verify(metaModelRepository, never()).save(source);
    verify(metaModelRepository, never()).save(derived);
  }

  @Test
  void update_clonesSourceAndRewires_whenDerivedAndSourceNotOwned() {
    String email = "u@ex.com";
    User caller = new User();
    caller.setId(1L);
    caller.setEmail(email);

    User other = new User();
    other.setId(2L);

    MetaModel source = new MetaModel();
    source.setId(100L);
    source.setUser(other);

    MetaModel derived = new MetaModel();
    derived.setId(200L);
    derived.setSource(source);

    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull(email))
        .thenReturn(Optional.of(caller));
    when(metaModelRepository.findById(200L)).thenReturn(Optional.of(derived));

    MetaModelService spyService = org.mockito.Mockito.spy(metaModelService);

    MetaModel clonedSource = new MetaModel();
    clonedSource.setId(999L);
    doReturn(clonedSource).when(spyService).clone(source);
    MetaModelPutRequest req = new MetaModelPutRequest();

    spyService.update(email, 200L, req);

    assertThat(clonedSource.getUser()).isSameAs(caller);
    assertThat(clonedSource.getSource()).isNull();

    verify(metaModelMapper).updateByMetaModelPutRequest(req, clonedSource);
    verify(metaModelMapper).updateByMetaModelPutRequest(req, derived);

    verify(metaModelRepository).save(clonedSource);
    verify(metaModelRepository).save(derived);
    verify(metaModelRepository, never()).saveAll(any());

    assertThat(derived.getSource()).isSameAs(clonedSource);
  }

  @Test
  void update_propagates_whenCloneFails_andDoesNotSaveAnything() {
    String email = "u@ex.com";
    User caller = new User();
    caller.setId(1L);
    caller.setEmail(email);

    User other = new User();
    other.setId(2L);

    MetaModel source = new MetaModel();
    source.setId(100L);
    source.setUser(other);

    MetaModel derived = new MetaModel();
    derived.setId(200L);
    derived.setSource(source);

    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull(email))
        .thenReturn(Optional.of(caller));
    when(metaModelRepository.findById(200L)).thenReturn(Optional.of(derived));

    MetaModelService spyService = org.mockito.Mockito.spy(metaModelService);
    doThrow(new RuntimeException("clone failed")).when(spyService).clone(source);

    assertThatThrownBy(() -> spyService.update(email, 200L, new MetaModelPutRequest()))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("clone failed");

    verify(metaModelRepository, never()).save(any());
    verify(metaModelRepository, never()).saveAll(any());
    verify(metaModelMapper, never()).updateByMetaModelPutRequest(any(), any());
  }

  @Test
  void isOwnedBy_returnsFalse_whenMetaModelOrUserNull() {
    assertThat(metaModelService.isOwnedBy(null, new User())).isFalse();
    assertThat(metaModelService.isOwnedBy(new MetaModel(), null)).isFalse();

    MetaModel m = new MetaModel();
    m.setUser(null);
    assertThat(metaModelService.isOwnedBy(m, new User())).isFalse();
  }

  @Test
  void isOwnedBy_comparesById_whenBothIdsPresent() {
    User a = new User();
    a.setId(1L);
    User bSameId = new User();
    bSameId.setId(1L);
    User cOtherId = new User();
    cOtherId.setId(2L);

    MetaModel m = new MetaModel();
    m.setUser(a);

    assertThat(metaModelService.isOwnedBy(m, bSameId)).isTrue();
    assertThat(metaModelService.isOwnedBy(m, cOtherId)).isFalse();
  }

  @Test
  void isOwnedBy_fallsBackToEquals_whenIdsMissing() {
    User owner = new User();
    owner.setEmail("x@x.com");

    MetaModel m = new MetaModel();
    m.setUser(owner);

    assertThat(metaModelService.isOwnedBy(m, owner)).isTrue();
  }

  /**
   * Verifies that finding accessible metamodels by project returns source metamodels.
   *
   * <p>Tests the LSP workspace preparation logic that retrieves all metamodels associated with a
   * given VSUM, preferring source metamodels over clones.
   */
  @Test
  void findAccessibleByProject_returnsSourceMetaModels() {

    MetaModel source1 = new MetaModel();
    source1.setId(1L);
    source1.setName("SourceMM1");

    MetaModel clone1 = new MetaModel();
    clone1.setId(2L);
    clone1.setSource(source1);

    MetaModel original2 = new MetaModel();
    original2.setId(3L);
    original2.setName("OriginalMM2");
    Long vsumId = 10L;
    Vsum vsum = new Vsum();
    vsum.setId(vsumId);

    VsumMetaModel vmm1 = VsumMetaModel.builder().vsum(vsum).metaModel(clone1).build();
    VsumMetaModel vmm2 = VsumMetaModel.builder().vsum(vsum).metaModel(original2).build();

    when(vsumMetaModelRepository.findByVsumId(vsumId)).thenReturn(List.of(vmm1, vmm2));

    List<MetaModel> result = metaModelService.findAccessibleByProject(vsumId);

    assertThat(result).hasSize(2).contains(source1, original2).doesNotContain(clone1);
  }

  /**
   * Verifies that finding accessible metamodels with null vsumId returns empty list.
   *
   * <p>Tests defensive handling when no project context is provided.
   */
  @Test
  void findAccessibleByProject_withNullVsumId_returnsEmptyList() {
    List<MetaModel> result = metaModelService.findAccessibleByProject(null);

    assertThat(result).isEmpty();
  }

  private FileStorage createFileStorage(Long id, String filename, byte[] data) {
    FileStorage file = mock(FileStorage.class);
    when(file.getId()).thenReturn(id);
    when(file.getFilename()).thenReturn(filename);
    when(file.getData()).thenReturn(data);
    return file;
  }

  /**
   * Verifies that writing metamodels to directory creates files correctly.
   *
   * <p>Tests the LSP workspace initialization that writes ecore files to the filesystem for the
   * language server to access.
   */
  @Test
  void writeMetamodelsToDirectory_createsEcoreFiles() throws IOException {

    MetaModel mm1 = new MetaModel();
    mm1.setId(1L);
    mm1.setEcoreFile(createFileStorage(10L, "model1.ecore", "ecore content 1".getBytes()));

    MetaModel mm2 = new MetaModel();
    mm2.setId(2L);
    mm2.setEcoreFile(createFileStorage(11L, "model2.ecore", "ecore content 2".getBytes()));

    Vsum vsum = new Vsum();
    VsumMetaModel vmm1 = VsumMetaModel.builder().vsum(vsum).metaModel(mm1).build();
    VsumMetaModel vmm2 = VsumMetaModel.builder().vsum(vsum).metaModel(mm2).build();
    Long vsumId = 10L;
    when(vsumMetaModelRepository.findByVsumId(vsumId)).thenReturn(List.of(vmm1, vmm2));
    File targetDir = tempDir.toFile();
    metaModelService.writeMetamodelsToDirectory(targetDir, vsumId);

    File file1 = new File(targetDir, "model1.ecore");
    File file2 = new File(targetDir, "model2.ecore");

    assertThat(file1).exists();
    assertThat(file2).exists();
    assertThat(Files.readAllBytes(file1.toPath())).isEqualTo("ecore content 1".getBytes());
    assertThat(Files.readAllBytes(file2.toPath())).isEqualTo("ecore content 2".getBytes());
  }
}
