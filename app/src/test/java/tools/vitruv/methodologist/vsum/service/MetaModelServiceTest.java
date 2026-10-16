package tools.vitruv.methodologist.vsum.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tools.vitruv.methodologist.messages.Error.ECORE_FILE_ID_NOT_FOUND_ERROR;
import static tools.vitruv.methodologist.messages.Error.META_MODEL_ID_NOT_FOUND_ERROR;
import static tools.vitruv.methodologist.messages.Error.USER_DOSE_NOT_HAVE_ACCESS;
import static tools.vitruv.methodologist.messages.Error.USER_EMAIL_NOT_FOUND_ERROR;
import static tools.vitruv.methodologist.messages.Message.META_MODEL_CREATED_SUCCESSFULLY;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import tools.vitruv.methodologist.apihandler.SetupServiceApiHandler;
import tools.vitruv.methodologist.apihandler.dto.response.GenModelInspectionResponse;
import tools.vitruv.methodologist.exception.CreateMwe2FileException;
import tools.vitruv.methodologist.exception.MetaModelUsedInVsumException;
import tools.vitruv.methodologist.exception.MetaModelVersionAlreadyExistsException;
import tools.vitruv.methodologist.exception.NotFoundException;
import tools.vitruv.methodologist.exception.SetupServiceException;
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
import tools.vitruv.methodologist.vsum.mapper.MetaModelMapper;
import tools.vitruv.methodologist.vsum.model.MetaModel;
import tools.vitruv.methodologist.vsum.model.Vsum;
import tools.vitruv.methodologist.vsum.model.VsumMetaModel;
import tools.vitruv.methodologist.vsum.model.VsumUser;
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
  private SetupServiceApiHandler setupServiceApiHandler;
  private VsumMetaModelRepository vsumMetaModelRepository;

  private MetaModelService metaModelService;

  private static MetaModelPostRequest req(long ecoreId) {
    final MetaModelPostRequest metaModelPostRequest = new MetaModelPostRequest();
    metaModelPostRequest.setName("mm1");
    metaModelPostRequest.setEcoreFileId(ecoreId);
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
    setupServiceApiHandler = mock(SetupServiceApiHandler.class);
    vsumMetaModelRepository = mock(VsumMetaModelRepository.class);

    metaModelService =
        new MetaModelService(
            metaModelService,
            metaModelMapper,
            metaModelRepository,
            fileStorageRepository,
            userRepository,
            metamodelBuildService,
            fileStorageService,
            setupServiceApiHandler,
            vsumMetaModelRepository);
  }

  private User stubUser(String email) {
    final User user = new User();
    user.setEmail(email);
    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull(email))
        .thenReturn(Optional.of(user));
    return user;
  }

  private FileStorage stubEcore(String email) {
    final FileStorage ecore = fs(10L, FileEnumType.ECORE, "E".getBytes());
    ecore.setFilename("family.ecore");
    when(fileStorageRepository.findByIdAndTypeAndUser_EmailAndUser_RemovedAtIsNull(
            10L, FileEnumType.ECORE, email))
        .thenReturn(Optional.of(ecore));
    return ecore;
  }

  private FileStorage stubGeneratedGenModel(String email, FileStorage ecore, byte[] genModelBytes) {
    final FileStorage gen = fs(30L, FileEnumType.GEN_MODEL, genModelBytes);
    when(setupServiceApiHandler.generateGenModelOrThrow(ecore)).thenReturn(genModelBytes);
    when(fileStorageService.storeFile(
            email, genModelBytes, "family.genmodel", null, FileEnumType.GEN_MODEL))
        .thenReturn(FileStorageResponse.builder().id(gen.getId()).build());
    when(fileStorageService.getFile(gen.getId())).thenReturn(gen);
    return gen;
  }

  private void stubSuccessfulBuild() {
    when(metamodelBuildService.buildAndValidate(any()))
        .thenReturn(
            MetamodelBuildService.BuildResult.builder()
                .success(true)
                .errors(0)
                .warnings(0)
                .report("OK")
                .discoveredNsUris("http://x")
                .build());
  }

  @Test
  void create_generatesGenModelFromEcore_storesIt_andBuilds() {
    final String email = "u@ex.com";
    final MetaModelPostRequest request = req(10L);
    stubUser(email);
    final FileStorage ecore = stubEcore(email);
    final byte[] generated = "generated-gen".getBytes();
    final FileStorage gen = stubGeneratedGenModel(email, ecore, generated);

    final MetaModel mapped = metaModel(null, null, null);
    when(metaModelMapper.toMetaModel(request)).thenReturn(mapped);
    when(metaModelRepository.save(any(MetaModel.class))).thenReturn(metaModel(100L, ecore, gen));
    stubSuccessfulBuild();

    final MetaModelService.MetaModelCreationResult result = metaModelService.create(email, request);

    assertThat(result.metaModel().getId()).isEqualTo(100L);
    assertThat(result.message()).isEqualTo(META_MODEL_CREATED_SUCCESSFULLY);
    assertThat(mapped.getEcoreFile()).isSameAs(ecore);
    assertThat(mapped.getGenModelFile()).isSameAs(gen);
    verify(setupServiceApiHandler).generateGenModelOrThrow(ecore);
    verify(fileStorageService)
        .storeFile(email, generated, "family.genmodel", null, FileEnumType.GEN_MODEL);
    verify(setupServiceApiHandler, never()).processGenModelOrThrow(any());
    verify(setupServiceApiHandler, never()).inspectGenModelOrThrow(any());
  }

  @Test
  void create_buildsWithGeneratedGenModel() {
    final String email = "u@ex.com";
    final MetaModelPostRequest request = req(10L);
    stubUser(email);
    final FileStorage ecore = stubEcore(email);
    final byte[] generated = "generated-gen".getBytes();
    stubGeneratedGenModel(email, ecore, generated);

    when(metaModelMapper.toMetaModel(request)).thenReturn(metaModel(null, null, null));
    when(metaModelRepository.save(any(MetaModel.class))).thenAnswer(inv -> inv.getArgument(0));
    stubSuccessfulBuild();

    metaModelService.create(email, request);

    ArgumentCaptor<MetamodelBuildService.MetamodelBuildInput> captor =
        ArgumentCaptor.forClass(MetamodelBuildService.MetamodelBuildInput.class);
    verify(metamodelBuildService).buildAndValidate(captor.capture());
    assertThat(captor.getValue().getEcoreBytes()).containsExactly("E".getBytes());
    assertThat(captor.getValue().getGenModelBytes()).containsExactly(generated);
  }

  @Test
  void create_usesDefaultGenModelName_whenEcoreFilenameMissing() {
    final String email = "u@ex.com";
    final MetaModelPostRequest request = req(10L);
    stubUser(email);
    final FileStorage ecore = stubEcore(email);
    ecore.setFilename(null);
    final byte[] generated = "generated-gen".getBytes();
    when(setupServiceApiHandler.generateGenModelOrThrow(ecore)).thenReturn(generated);
    when(fileStorageService.storeFile(
            email, generated, "model.genmodel", null, FileEnumType.GEN_MODEL))
        .thenReturn(FileStorageResponse.builder().id(30L).build());
    final FileStorage gen = fs(30L, FileEnumType.GEN_MODEL, generated);
    when(fileStorageService.getFile(30L)).thenReturn(gen);

    when(metaModelMapper.toMetaModel(request)).thenReturn(metaModel(null, null, null));
    when(metaModelRepository.save(any(MetaModel.class))).thenReturn(metaModel(100L, ecore, gen));
    stubSuccessfulBuild();

    metaModelService.create(email, request);

    verify(fileStorageService)
        .storeFile(email, generated, "model.genmodel", null, FileEnumType.GEN_MODEL);
  }

  @Test
  void create_doesNotSaveMetaModel_whenGenModelGenerationFails() {
    final String email = "u@ex.com";
    final MetaModelPostRequest request = req(10L);
    stubUser(email);
    final FileStorage ecore = stubEcore(email);
    when(metaModelMapper.toMetaModel(request)).thenReturn(metaModel(null, null, null));
    when(setupServiceApiHandler.generateGenModelOrThrow(ecore))
        .thenThrow(new SetupServiceException("invalid ecore"));

    assertThatThrownBy(() -> metaModelService.create(email, request))
        .isInstanceOf(SetupServiceException.class)
        .hasMessageContaining("invalid ecore");

    verify(fileStorageService, never()).storeFile(any(), any(byte[].class), any(), any(), any());
    verify(metaModelRepository, never()).save(any(MetaModel.class));
    verify(metamodelBuildService, never()).buildAndValidate(any());
  }

  @Test
  void create_rejectsSameNameAndVersion() {
    final String email = "u@ex.com";
    final MetaModelPostRequest request = req(10L);
    request.setVersion("1.0");
    final User user = stubUser(email);
    final FileStorage ecore = stubEcore(email);

    final MetaModel mapped = metaModel(null, ecore, null);
    mapped.setVersion("1.0");
    when(metaModelMapper.toMetaModel(request)).thenReturn(mapped);
    when(metaModelRepository.existsLibraryMetamodel(user, "mm1", "1.0")).thenReturn(true);

    assertThatThrownBy(() -> metaModelService.create(email, request))
        .isInstanceOf(MetaModelVersionAlreadyExistsException.class)
        .hasMessage("A metamodel with this name and version already exists.");

    verify(setupServiceApiHandler, never()).generateGenModelOrThrow(any());
    verify(metaModelRepository, never()).save(any());
  }

  @Test
  void create_allowsSameNameWhenVersionDiffers() {
    final String email = "u@ex.com";
    final MetaModelPostRequest request = req(10L);
    request.setVersion("2.0");
    final User user = stubUser(email);
    final FileStorage ecore = stubEcore(email);
    final FileStorage gen = stubGeneratedGenModel(email, ecore, "generated".getBytes());

    final MetaModel mapped = metaModel(null, ecore, null);
    mapped.setVersion("2.0");
    when(metaModelMapper.toMetaModel(request)).thenReturn(mapped);
    when(metaModelRepository.existsLibraryMetamodel(user, "mm1", "2.0")).thenReturn(false);
    when(metaModelRepository.save(any(MetaModel.class))).thenReturn(metaModel(100L, ecore, gen));
    stubSuccessfulBuild();

    MetaModelService.MetaModelCreationResult result = metaModelService.create(email, request);

    assertThat(result.metaModel().getId()).isEqualTo(100L);
    verify(metaModelRepository).existsLibraryMetamodel(user, "mm1", "2.0");
    verify(metaModelRepository).save(any(MetaModel.class));
  }

  @Test
  void processGenModel_delegatesToSetupServiceApiHandler() {
    final FileStorage gen = fs(20L, FileEnumType.GEN_MODEL, "G".getBytes());
    byte[] processed = "fixed".getBytes();
    when(setupServiceApiHandler.processGenModelOrThrow(gen)).thenReturn(processed);

    assertThat(metaModelService.processGenModel(gen)).isEqualTo(processed);
    verify(setupServiceApiHandler).processGenModelOrThrow(gen);
  }

  @Test
  void inspectGenModel_delegatesToSetupServiceApiHandler() {
    final FileStorage gen = fs(20L, FileEnumType.GEN_MODEL, "G".getBytes());
    GenModelInspectionResponse response =
        GenModelInspectionResponse.builder().message("ok").build();
    when(setupServiceApiHandler.inspectGenModelOrThrow(gen)).thenReturn(response);

    assertThat(metaModelService.inspectGenModel(gen)).isSameAs(response);
    verify(setupServiceApiHandler).inspectGenModelOrThrow(gen);
  }

  @Test
  void create_throwsCreateMwe2FileException_whenBuildFails() {
    final String email = "u@ex.com";
    final MetaModelPostRequest request = req(10L);
    stubUser(email);
    final FileStorage ecore = stubEcore(email);
    final FileStorage gen = stubGeneratedGenModel(email, ecore, "generated".getBytes());

    when(metaModelMapper.toMetaModel(request)).thenReturn(metaModel(null, ecore, null));
    when(metaModelRepository.save(any(MetaModel.class))).thenReturn(metaModel(100L, ecore, gen));

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
    final MetaModelPostRequest request = req(10L);
    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull(email))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> metaModelService.create(email, request))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining(USER_EMAIL_NOT_FOUND_ERROR);
  }

  @Test
  void create_throwsNotFound_whenEcoreMissing() {
    final String email = "u@ex.com";
    final MetaModelPostRequest request = req(10L);
    stubUser(email);

    when(fileStorageRepository.findByIdAndTypeAndUser_EmailAndUser_RemovedAtIsNull(
            10L, FileEnumType.ECORE, email))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> metaModelService.create(email, request))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining(ECORE_FILE_ID_NOT_FOUND_ERROR);
    verify(setupServiceApiHandler, never()).generateGenModelOrThrow(any());
  }

  @Test
  void create_throwsNotFound_whenEcoreBelongsToAnotherUser() {
    final String email = "u@ex.com";
    final MetaModelPostRequest request = req(10L);
    stubUser(email);

    when(fileStorageRepository.findByIdAndTypeAndUser_EmailAndUser_RemovedAtIsNull(
            10L, FileEnumType.ECORE, email))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> metaModelService.create(email, request))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining(ECORE_FILE_ID_NOT_FOUND_ERROR);

    verify(fileStorageRepository, never()).findByIdAndType(10L, FileEnumType.ECORE);
    verify(setupServiceApiHandler, never()).generateGenModelOrThrow(any());
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
        .hasMessage("Meta model is used in: VSUM1");
  }

  @Test
  void delete_marksBlockingVsumsInTrash_inTheErrorMessage() {
    String email = "user@ex.com";
    Long id = 1L;
    MetaModel metaModel = new MetaModel();
    Vsum active = new Vsum();
    active.setName("A");
    Vsum trashed = new Vsum();
    trashed.setName("B");
    trashed.setRemovedAt(Instant.parse("2026-01-01T00:00:00Z"));
    VsumMetaModel activeLink = new VsumMetaModel();
    activeLink.setVsum(active);
    VsumMetaModel trashedLink = new VsumMetaModel();
    trashedLink.setVsum(trashed);

    when(metaModelRepository.findByIdAndUser_Email(id, email)).thenReturn(Optional.of(metaModel));
    when(vsumMetaModelRepository.findAllByMetaModel_Source(metaModel))
        .thenReturn(List.of(activeLink, trashedLink));

    assertThatThrownBy(() -> metaModelService.delete(email, id))
        .isInstanceOf(MetaModelUsedInVsumException.class)
        .hasMessage("Meta model is used in: A, B (in trash)");

    verify(metaModelRepository, never()).delete(any(MetaModel.class));
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
    verify(vsumMetaModelRepository, never()).findAllByMetaModel_Source_IdIn(any());
  }

  @Test
  void findAll_includesVsumsUsingEachLibraryMetaModel() {
    MetaModel used = MetaModel.builder().id(1L).build();
    MetaModel unused = MetaModel.builder().id(2L).build();
    Vsum beta = vsumWithMember(10L, "Beta", "U@EX.com");
    Vsum alpha = vsumWithMember(11L, "Alpha", "u@ex.com");
    alpha.setRemovedAt(Instant.EPOCH);
    when(metaModelRepository.findAll(any(), any(Pageable.class))).thenReturn(List.of(used, unused));
    when(metaModelMapper.toMetaModelResponse(used)).thenReturn(new MetaModelResponse());
    when(metaModelMapper.toMetaModelResponse(unused)).thenReturn(new MetaModelResponse());
    when(vsumMetaModelRepository.findAllByMetaModel_Source_IdIn(List.of(1L, 2L)))
        .thenReturn(
            List.of(cloneUsage(beta, used), cloneUsage(alpha, used), cloneUsage(beta, used)));

    List<MetaModelResponse> result =
        metaModelService.findAll("u@ex.com", new MetaModelFilterRequest(), PageRequest.of(0, 10));

    assertThat(result.get(0).getVsums())
        .extracting("id", "name", "removedAt")
        .containsExactly(tuple(11L, "Alpha", Instant.EPOCH), tuple(10L, "Beta", null));
    assertThat(result.get(1).getVsums()).isEmpty();
  }

  @Test
  void findAll_hidesDetailsOfVsumsTheCallerIsNotMemberOf() {
    MetaModel used = MetaModel.builder().id(1L).build();
    Vsum own = vsumWithMember(10L, "Own", "u@ex.com");
    Vsum foreign = vsumWithMember(11L, "Foreign", "other@ex.com");
    when(metaModelRepository.findAll(any(), any(Pageable.class))).thenReturn(List.of(used));
    when(metaModelMapper.toMetaModelResponse(used)).thenReturn(new MetaModelResponse());
    when(vsumMetaModelRepository.findAllByMetaModel_Source_IdIn(List.of(1L)))
        .thenReturn(List.of(cloneUsage(foreign, used), cloneUsage(own, used)));

    List<MetaModelResponse> result =
        metaModelService.findAll("u@ex.com", new MetaModelFilterRequest(), PageRequest.of(0, 10));

    assertThat(result.get(0).getVsums())
        .extracting("id", "name", "removedAt")
        .containsExactly(tuple(10L, "Own", null), tuple(null, null, null));
  }

  private static Vsum vsumWithMember(Long id, String name, String memberEmail) {
    User member = new User();
    member.setEmail(memberEmail);
    Vsum vsum = Vsum.builder().id(id).name(name).build();
    vsum.setVsumUsers(Set.of(VsumUser.builder().user(member).build()));
    return vsum;
  }

  private static VsumMetaModel cloneUsage(Vsum vsum, MetaModel source) {
    MetaModel clone = MetaModel.builder().id(100L + vsum.getId()).source(source).build();
    return VsumMetaModel.builder().vsum(vsum).metaModel(clone).build();
  }

  @Test
  void update_throwsAccessDenied_whenUserMissing() {
    String email = "missing@ex.com";
    MetaModelPutRequest request = new MetaModelPutRequest();
    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull(email))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> metaModelService.update(email, 1L, request))
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

    MetaModelPutRequest request = new MetaModelPutRequest();
    ThrowingCallable callable = () -> metaModelService.update(email, 99L, request);

    assertThatThrownBy(callable)
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining(META_MODEL_ID_NOT_FOUND_ERROR);

    verify(metaModelRepository, never()).save(any());
    verify(metaModelRepository, never()).saveAll(any());
    verify(metaModelMapper, never()).updateByMetaModelPutRequest(any(), any());
  }

  @Test
  void update_updatesAndSaves_whenOriginalMetaModelNotOwned() {
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
    verify(setupServiceApiHandler, never()).generateGenModelOrThrow(any());
  }

  @Test
  void update_relinksOwnedEcoreRevision_whenOriginalMetaModelOwned() {
    String email = "u@ex.com";
    User caller = new User();
    caller.setId(1L);
    caller.setEmail(email);

    FileStorage originalEcore = fs(10L, FileEnumType.ECORE, "old".getBytes());
    FileStorage newEcore = fs(11L, FileEnumType.ECORE, "new".getBytes());
    FileStorage genModel = fs(20L, FileEnumType.GEN_MODEL, "gen".getBytes());
    MetaModel metaModel = metaModel(1L, originalEcore, genModel);
    metaModel.setUser(caller);

    MetaModelPutRequest request = new MetaModelPutRequest();
    request.setEcoreFileId(newEcore.getId());

    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull(email))
        .thenReturn(Optional.of(caller));
    when(metaModelRepository.findById(metaModel.getId())).thenReturn(Optional.of(metaModel));
    when(fileStorageRepository.findByIdAndTypeAndUser_EmailAndUser_RemovedAtIsNull(
            newEcore.getId(), FileEnumType.ECORE, email))
        .thenReturn(Optional.of(newEcore));

    metaModelService.update(email, metaModel.getId(), request);

    assertThat(metaModel.getEcoreFile()).isSameAs(newEcore);
    assertThat(metaModel.getGenModelFile()).isSameAs(genModel);
    verify(metaModelRepository).save(metaModel);
  }

  @Test
  void update_regeneratesGenModel_whenEcoreChanges() {
    String email = "u@ex.com";
    User caller = new User();
    caller.setId(1L);
    caller.setEmail(email);

    FileStorage originalEcore = fs(10L, FileEnumType.ECORE, "old".getBytes());
    FileStorage newEcore = fs(11L, FileEnumType.ECORE, "new".getBytes());
    newEcore.setFilename("renamed.ecore");
    FileStorage genModel = fs(20L, FileEnumType.GEN_MODEL, "old-gen".getBytes());
    genModel.setFilename("family.genmodel");
    MetaModel metaModel = metaModel(1L, originalEcore, genModel);
    metaModel.setUser(caller);

    MetaModelPutRequest request = new MetaModelPutRequest();
    request.setEcoreFileId(newEcore.getId());

    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull(email))
        .thenReturn(Optional.of(caller));
    when(metaModelRepository.findById(metaModel.getId())).thenReturn(Optional.of(metaModel));
    when(fileStorageRepository.findByIdAndTypeAndUser_EmailAndUser_RemovedAtIsNull(
            newEcore.getId(), FileEnumType.ECORE, email))
        .thenReturn(Optional.of(newEcore));
    byte[] regenerated = "new-gen".getBytes();
    when(setupServiceApiHandler.generateGenModelOrThrow(newEcore)).thenReturn(regenerated);

    metaModelService.update(email, metaModel.getId(), request);

    verify(setupServiceApiHandler).generateGenModelOrThrow(newEcore);
    verify(fileStorageService).overwriteStoredContent(genModel, regenerated);
    assertThat(genModel.getFilename()).isEqualTo("renamed.genmodel");
    assertThat(metaModel.getGenModelFile()).isSameAs(genModel);
  }

  private static MetaModel projectCopy(FileStorage ecoreFile, FileStorage genModelFile) {
    final MetaModel library =
        metaModel(
            1L,
            fs(1L, FileEnumType.ECORE, "old".getBytes()),
            fs(2L, FileEnumType.GEN_MODEL, "old-gen".getBytes()));
    final MetaModel copy = metaModel(101L, ecoreFile, genModelFile);
    copy.setSource(library);
    return copy;
  }

  @Test
  void updateProjectEcore_overwritesCopyAndRegeneratesGenModel() {
    FileStorage ecore = fs(10L, FileEnumType.ECORE, "old".getBytes());
    ecore.setFilename("family.ecore");
    FileStorage genModel = fs(20L, FileEnumType.GEN_MODEL, "old-gen".getBytes());
    MetaModel projectMetaModel = projectCopy(ecore, genModel);
    byte[] regenerated = "new-gen".getBytes();
    when(setupServiceApiHandler.generateGenModelOrThrow(ecore)).thenReturn(regenerated);

    metaModelService.updateProjectEcore(projectMetaModel, "new".getBytes());

    verify(fileStorageService).overwriteStoredContent(ecore, "new".getBytes());
    verify(fileStorageService).overwriteStoredContent(genModel, regenerated);
    verify(fileStorageService, times(2)).overwriteStoredContent(any(), any());
    verify(fileStorageService, never()).clone(any());
    assertThat(genModel.getFilename()).isEqualTo("family.genmodel");
    verify(metaModelRepository).save(projectMetaModel);
  }

  @Test
  void updateProjectEcore_clonesFilesFirst_whenSharedWithLibrary() {
    MetaModel library = projectCopy(null, null).getSource();
    MetaModel projectMetaModel = metaModel(101L, library.getEcoreFile(), library.getGenModelFile());
    projectMetaModel.setSource(library);
    FileStorage ecoreCopy = fs(10L, FileEnumType.ECORE, "old".getBytes());
    when(fileStorageService.clone(library.getEcoreFile())).thenReturn(ecoreCopy);
    FileStorage genModelCopy = fs(20L, FileEnumType.GEN_MODEL, "old-gen".getBytes());
    when(fileStorageService.clone(library.getGenModelFile())).thenReturn(genModelCopy);
    when(setupServiceApiHandler.generateGenModelOrThrow(ecoreCopy))
        .thenReturn("new-gen".getBytes());

    metaModelService.updateProjectEcore(projectMetaModel, "new".getBytes());

    verify(fileStorageService).overwriteStoredContent(ecoreCopy, "new".getBytes());
    verify(fileStorageService).overwriteStoredContent(genModelCopy, "new-gen".getBytes());
    verify(fileStorageService, times(2)).overwriteStoredContent(any(), any());
    assertThat(projectMetaModel.getEcoreFile()).isSameAs(ecoreCopy);
    assertThat(projectMetaModel.getGenModelFile()).isSameAs(genModelCopy);
  }

  @Test
  void updateProjectEcore_doesNothing_whenContentUnchanged() {
    MetaModel projectMetaModel =
        projectCopy(
            fs(10L, FileEnumType.ECORE, "same".getBytes()),
            fs(20L, FileEnumType.GEN_MODEL, "gen".getBytes()));

    metaModelService.updateProjectEcore(projectMetaModel, "same".getBytes());

    verify(fileStorageService, never()).overwriteStoredContent(any(), any());
    verify(setupServiceApiHandler, never()).generateGenModelOrThrow(any());
    verify(metaModelRepository, never()).save(any());
  }

  @Test
  void updateProjectEcore_propagatesSetupServiceFailure() {
    FileStorage ecore = fs(10L, FileEnumType.ECORE, "old".getBytes());
    MetaModel projectMetaModel =
        projectCopy(ecore, fs(20L, FileEnumType.GEN_MODEL, "old-gen".getBytes()));
    when(setupServiceApiHandler.generateGenModelOrThrow(ecore))
        .thenThrow(new SetupServiceException("invalid ecore"));
    byte[] newEcore = "broken".getBytes();

    assertThatThrownBy(() -> metaModelService.updateProjectEcore(projectMetaModel, newEcore))
        .isInstanceOf(SetupServiceException.class);
    verify(metaModelRepository, never()).save(any());
  }

  @Test
  void update_doesNotRegenerateGenModel_whenEcoreUnchanged() {
    String email = "u@ex.com";
    User caller = new User();
    caller.setId(1L);
    caller.setEmail(email);

    FileStorage ecore = fs(10L, FileEnumType.ECORE, "ecore".getBytes());
    FileStorage genModel = fs(20L, FileEnumType.GEN_MODEL, "gen".getBytes());
    MetaModel metaModel = metaModel(1L, ecore, genModel);
    metaModel.setUser(caller);

    MetaModelPutRequest request = new MetaModelPutRequest();
    request.setEcoreFileId(ecore.getId());

    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull(email))
        .thenReturn(Optional.of(caller));
    when(metaModelRepository.findById(metaModel.getId())).thenReturn(Optional.of(metaModel));

    metaModelService.update(email, metaModel.getId(), request);

    verify(setupServiceApiHandler, never()).generateGenModelOrThrow(any());
    verify(fileStorageService, never()).overwriteStoredContent(any(), any());
    verify(metaModelRepository).save(metaModel);
  }

  @Test
  void update_doesNotSave_whenGenModelRegenerationFails() {
    String email = "u@ex.com";
    User caller = new User();
    caller.setId(1L);
    caller.setEmail(email);

    FileStorage originalEcore = fs(10L, FileEnumType.ECORE, "old".getBytes());
    FileStorage newEcore = fs(11L, FileEnumType.ECORE, "new".getBytes());
    FileStorage genModel = fs(20L, FileEnumType.GEN_MODEL, "gen".getBytes());
    MetaModel metaModel = metaModel(1L, originalEcore, genModel);
    metaModel.setUser(caller);

    MetaModelPutRequest request = new MetaModelPutRequest();
    request.setEcoreFileId(newEcore.getId());

    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull(email))
        .thenReturn(Optional.of(caller));
    when(metaModelRepository.findById(metaModel.getId())).thenReturn(Optional.of(metaModel));
    when(fileStorageRepository.findByIdAndTypeAndUser_EmailAndUser_RemovedAtIsNull(
            newEcore.getId(), FileEnumType.ECORE, email))
        .thenReturn(Optional.of(newEcore));
    when(setupServiceApiHandler.generateGenModelOrThrow(newEcore))
        .thenThrow(new SetupServiceException("invalid ecore"));

    assertThatThrownBy(() -> metaModelService.update(email, metaModel.getId(), request))
        .isInstanceOf(SetupServiceException.class)
        .hasMessageContaining("invalid ecore");

    verify(fileStorageService, never()).overwriteStoredContent(any(), any());
    verify(metaModelRepository, never()).save(any());
  }

  @Test
  void update_rejectsEcoreRevisionNotOwnedByCaller() {
    String email = "u@ex.com";
    User caller = new User();
    caller.setId(1L);
    caller.setEmail(email);

    FileStorage originalEcore = fs(10L, FileEnumType.ECORE, "old".getBytes());
    FileStorage genModel = fs(20L, FileEnumType.GEN_MODEL, "gen".getBytes());
    MetaModel metaModel = metaModel(1L, originalEcore, genModel);
    metaModel.setUser(caller);

    MetaModelPutRequest request = new MetaModelPutRequest();
    request.setEcoreFileId(11L);

    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull(email))
        .thenReturn(Optional.of(caller));
    when(metaModelRepository.findById(metaModel.getId())).thenReturn(Optional.of(metaModel));

    assertThatThrownBy(() -> metaModelService.update(email, metaModel.getId(), request))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining(ECORE_FILE_ID_NOT_FOUND_ERROR);

    assertThat(metaModel.getEcoreFile()).isSameAs(originalEcore);
    verify(metaModelRepository, never()).save(any());
    verify(setupServiceApiHandler, never()).generateGenModelOrThrow(any());
  }

  @Test
  void update_rejectsNameThatCollidesWithSameVersion() {
    String email = "u@ex.com";
    User caller = new User();
    caller.setId(1L);
    caller.setEmail(email);

    MetaModel metaModel = new MetaModel();
    metaModel.setId(10L);
    metaModel.setName("Model 3");
    metaModel.setVersion("1.0");
    metaModel.setSource(null);
    metaModel.setUser(caller);

    MetaModelPutRequest req = new MetaModelPutRequest();
    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull(email))
        .thenReturn(Optional.of(caller));
    when(metaModelRepository.findById(10L)).thenReturn(Optional.of(metaModel));
    when(metaModelRepository.existsOtherLibraryMetamodel(caller, "Model 3", "1.0", 10L))
        .thenReturn(true);

    assertThatThrownBy(() -> metaModelService.update(email, 10L, req))
        .isInstanceOf(MetaModelVersionAlreadyExistsException.class);

    verify(metaModelRepository, never()).save(any());
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
    source.setEcoreFile(fs(10L, FileEnumType.ECORE, "old".getBytes()));
    FileStorage sourceGenModel = fs(20L, FileEnumType.GEN_MODEL, "old-gen".getBytes());
    source.setGenModelFile(sourceGenModel);

    MetaModel derived = new MetaModel();
    derived.setId(2L);
    derived.setUser(new User());
    derived.setSource(source);
    derived.setEcoreFile(source.getEcoreFile());
    FileStorage derivedGenModel = fs(21L, FileEnumType.GEN_MODEL, "old-gen".getBytes());
    derived.setGenModelFile(derivedGenModel);

    MetaModelPutRequest req = new MetaModelPutRequest();
    FileStorage newEcore = fs(11L, FileEnumType.ECORE, "new".getBytes());
    req.setEcoreFileId(newEcore.getId());

    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull(email))
        .thenReturn(Optional.of(caller));
    when(metaModelRepository.findById(2L)).thenReturn(Optional.of(derived));
    when(fileStorageRepository.findByIdAndTypeAndUser_EmailAndUser_RemovedAtIsNull(
            newEcore.getId(), FileEnumType.ECORE, email))
        .thenReturn(Optional.of(newEcore));
    byte[] regenerated = "new-gen".getBytes();
    when(setupServiceApiHandler.generateGenModelOrThrow(newEcore)).thenReturn(regenerated);

    metaModelService.update(email, 2L, req);

    verify(metaModelMapper).updateByMetaModelPutRequest(req, source);
    verify(metaModelMapper).updateByMetaModelPutRequest(req, derived);
    assertThat(source.getEcoreFile()).isSameAs(newEcore);
    assertThat(derived.getEcoreFile()).isSameAs(newEcore);
    verify(setupServiceApiHandler, times(1)).generateGenModelOrThrow(newEcore);
    verify(fileStorageService).overwriteStoredContent(sourceGenModel, regenerated);
    verify(fileStorageService).overwriteStoredContent(derivedGenModel, regenerated);

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
  void update_regeneratesGenModelsOfClonedSourceAndDerived_whenEcoreChangesOnNotOwnedSource() {
    String email = "u@ex.com";
    User caller = new User();
    caller.setId(1L);
    caller.setEmail(email);

    User other = new User();
    other.setId(2L);

    MetaModel source = new MetaModel();
    source.setId(100L);
    source.setUser(other);

    FileStorage derivedGenModel = fs(21L, FileEnumType.GEN_MODEL, "old-gen".getBytes());
    MetaModel derived = metaModel(200L, fs(10L, FileEnumType.ECORE, "old".getBytes()), null);
    derived.setGenModelFile(derivedGenModel);
    derived.setSource(source);

    FileStorage newEcore = fs(11L, FileEnumType.ECORE, "new".getBytes());
    MetaModelPutRequest req = new MetaModelPutRequest();
    req.setEcoreFileId(newEcore.getId());

    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull(email))
        .thenReturn(Optional.of(caller));
    when(metaModelRepository.findById(200L)).thenReturn(Optional.of(derived));
    when(fileStorageRepository.findByIdAndTypeAndUser_EmailAndUser_RemovedAtIsNull(
            newEcore.getId(), FileEnumType.ECORE, email))
        .thenReturn(Optional.of(newEcore));
    byte[] regenerated = "new-gen".getBytes();
    when(setupServiceApiHandler.generateGenModelOrThrow(newEcore)).thenReturn(regenerated);

    MetaModelService spyService = org.mockito.Mockito.spy(metaModelService);
    FileStorage clonedGenModel = fs(22L, FileEnumType.GEN_MODEL, "old-gen".getBytes());
    MetaModel clonedSource = metaModel(999L, null, clonedGenModel);
    doReturn(clonedSource).when(spyService).clone(source);

    spyService.update(email, 200L, req);

    assertThat(clonedSource.getEcoreFile()).isSameAs(newEcore);
    assertThat(derived.getEcoreFile()).isSameAs(newEcore);
    verify(setupServiceApiHandler, times(1)).generateGenModelOrThrow(newEcore);
    verify(fileStorageService).overwriteStoredContent(clonedGenModel, regenerated);
    verify(fileStorageService).overwriteStoredContent(derivedGenModel, regenerated);
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

    MetaModelPutRequest request = new MetaModelPutRequest();
    ThrowingCallable callable = () -> spyService.update(email, 200L, request);

    assertThatThrownBy(callable)
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
    // Setup
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

    // Inject self reference manually
    ReflectionTestUtils.setField(metaModelService, "self", metaModelService);

    File targetDir = tempDir.toFile();
    metaModelService.writeMetamodelsToDirectory(targetDir, vsumId);

    // Verify
    File file1 = new File(targetDir, "model1.ecore");
    File file2 = new File(targetDir, "model2.ecore");

    assertThat(file1).exists();
    assertThat(file2).exists();
    assertThat(Files.readAllBytes(file1.toPath())).isEqualTo("ecore content 1".getBytes());
    assertThat(Files.readAllBytes(file2.toPath())).isEqualTo("ecore content 2".getBytes());
  }
}
