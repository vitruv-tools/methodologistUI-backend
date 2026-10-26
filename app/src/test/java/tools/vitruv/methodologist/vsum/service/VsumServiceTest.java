package tools.vitruv.methodologist.vsum.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tools.vitruv.methodologist.messages.Error.VSUM_ID_NOT_FOUND_ERROR;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import tools.vitruv.methodologist.exception.NotFoundException;
import tools.vitruv.methodologist.exception.UnauthorizedException;
import tools.vitruv.methodologist.general.model.FileStorage;
import tools.vitruv.methodologist.user.model.User;
import tools.vitruv.methodologist.user.model.repository.UserRepository;
import tools.vitruv.methodologist.vsum.VsumRole;
import tools.vitruv.methodologist.vsum.controller.dto.request.VsumPostRequest;
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
import tools.vitruv.methodologist.vsum.model.VsumView;
import tools.vitruv.methodologist.vsum.model.repository.VsumMetaModelRepository;
import tools.vitruv.methodologist.vsum.model.repository.VsumRepository;
import tools.vitruv.methodologist.vsum.model.repository.VsumUserRepository;
import tools.vitruv.methodologist.vsum.model.repository.VsumViewRepository;

@ExtendWith(MockitoExtension.class)
class VsumServiceTest {

  private static final String BUILD_EMAIL = "x@y.com";
  private static final Long BUILD_ID = 1L;

  @Mock private VsumMapper vsumMapper;
  @Mock private VsumRepository vsumRepository;
  @Mock private MetaModelMapper metaModelMapper;
  @Mock private UserRepository userRepository;
  @Mock private VsumUserRepository vsumUserRepository;
  @Mock private VsumUserService vsumUserService;
  @Mock private MetaModelRelationMapper metaModelRelationMapper;
  @Mock private VsumMetaModelRepository vsumMetaModelRepository;
  @Mock private VsumHistoryService vsumHistoryService;
  @Mock private VsumViewRepository vsumViewRepository;
  @Mock private VsumViewMapper vsumViewMapper;
  @Mock private LowCodeReactionRequestMapper lowCodeReactionRequestMapper;
  @Mock private VsumBuildService vsumBuildService;
  @Mock private MetaModelService metaModelService;
  @Mock private VsumSyncService vsumSyncService;
  @Mock private VsumCleanupService vsumCleanupService;

  private VsumService service;

  private MetaModel original(long id) {
    MetaModel m = new MetaModel();
    m.setId(id);
    return m;
  }

  private MetaModel clonedMetaModel(long id, long originalId) {
    MetaModel metaModel = new MetaModel();
    metaModel.setId(id);
    metaModel.setSource(original(originalId));
    return metaModel;
  }

  private MetaModelRelation metaModelRelation(Vsum vsum, MetaModel src, MetaModel tgt) {
    return metaModelRelation(vsum, src, tgt, null);
  }

  private MetaModelRelation metaModelRelation(
      Vsum vsum, MetaModel src, MetaModel tgt, Long reactionFileId) {
    MetaModelRelation metaModelRelation = new MetaModelRelation();
    metaModelRelation.setVsum(vsum);
    metaModelRelation.setSource(src);
    metaModelRelation.setTarget(tgt);
    if (reactionFileId != null) {
      FileStorage reaction = new FileStorage();
      reaction.setId(reactionFileId);
      metaModelRelation.setReactionFileStorage(reaction);
    }
    return metaModelRelation;
  }

  private VsumMetaModel vsumMetaModel(Vsum vsum, MetaModel mm) {
    VsumMetaModel vsumMetaModel = new VsumMetaModel();
    vsumMetaModel.setVsum(vsum);
    vsumMetaModel.setMetaModel(mm);
    return vsumMetaModel;
  }

  private VsumUser vsumUser(Vsum vsum, User u) {
    VsumUser vsumUser = new VsumUser();
    vsumUser.setVsum(vsum);
    vsumUser.setUser(u);
    return vsumUser;
  }

  @BeforeEach
  void setUp() {
    service =
        new VsumService(
            vsumMapper,
            vsumRepository,
            metaModelMapper,
            userRepository,
            vsumUserRepository,
            vsumUserService,
            metaModelRelationMapper,
            vsumMetaModelRepository,
            vsumHistoryService,
            vsumViewRepository,
            vsumViewMapper,
            lowCodeReactionRequestMapper,
            vsumBuildService,
            metaModelService,
            vsumSyncService,
            vsumCleanupService);

    lenient().when(vsumViewRepository.findAllByVsum(any(Vsum.class))).thenReturn(List.of());
    lenient()
        .when(vsumViewMapper.toViewsResponse(any(VsumView.class)))
        .thenAnswer(
            inv -> {
              VsumView view = inv.getArgument(0);
              ViewsResponse response = new ViewsResponse();
              response.setId(view.getId());
              response.setAssignedModels(List.of());
              return response;
            });
  }

  @Test
  void create_savesWithOwner_whenUserExists() {
    String email = "u@ex.com";
    User user = new User();
    user.setEmail(email);
    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull(email))
        .thenReturn(Optional.of(user));

    VsumPostRequest req = new VsumPostRequest();
    Vsum mapped = new Vsum();
    when(vsumMapper.toVsum(req)).thenReturn(mapped);

    Vsum saved = new Vsum();
    saved.setId(10L);
    when(vsumRepository.save(mapped)).thenReturn(saved);

    Vsum result = service.create(email, req);

    assertThat(result.getId()).isEqualTo(10L);
    assertThat(mapped.getUser()).isSameAs(user);
    verify(vsumRepository).save(mapped);
    verify(vsumUserService).create(saved, user, VsumRole.OWNER);
  }

  @Test
  void create_throwsUnauthorized_whenUserMissing() {
    String email = "none@ex.com";
    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull(email))
        .thenReturn(Optional.empty());

    VsumPostRequest request = new VsumPostRequest();

    assertThatThrownBy(() -> service.create(email, request))
        .isInstanceOf(UnauthorizedException.class);

    verify(vsumRepository, never()).save(any(Vsum.class));
  }

  @Test
  void updateMetaModelName_updatesOnlyTheProjectSpecificName() {
    String email = "u@ex.com";
    User user = new User();
    user.setEmail(email);
    Vsum vsum = new Vsum();
    VsumUser vsumUser = vsumUser(vsum, user);
    vsumUser.setRole(VsumRole.MEMBER);
    MetaModel libraryMetaModel = original(10L);
    libraryMetaModel.setName("Library name");
    MetaModel projectMetaModel = new MetaModel();
    projectMetaModel.setId(101L);
    projectMetaModel.setSource(libraryMetaModel);
    VsumMetaModel vsumMetaModel = vsumMetaModel(vsum, projectMetaModel);
    vsumMetaModel.setName("Original project name");

    when(vsumUserRepository
            .findByVsum_IdAndUser_EmailAndUser_RemovedAtIsNullAndVsum_RemovedAtIsNull(1L, email))
        .thenReturn(Optional.of(vsumUser));
    when(vsumMetaModelRepository.findByVsumAndMetaModel_Source_Id(vsum, 10L))
        .thenReturn(Optional.of(vsumMetaModel));

    service.updateMetaModelName(email, 1L, 10L, "Renamed in project");

    assertThat(vsumMetaModel.getName()).isEqualTo("Renamed in project");
    assertThat(libraryMetaModel.getName()).isEqualTo("Library name");
    verify(vsumHistoryService).create(vsum, user);
    verify(vsumMetaModelRepository).save(vsumMetaModel);
  }

  private void givenVsumMember(String email, Vsum vsum, VsumRole role) {
    VsumUser vsumUser = vsumUser(vsum, new User());
    vsumUser.setRole(role);
    when(vsumUserRepository
            .findByVsum_IdAndUser_EmailAndUser_RemovedAtIsNullAndVsum_RemovedAtIsNull(1L, email))
        .thenReturn(Optional.of(vsumUser));
  }

  @Test
  void updateMetaModelEcore_delegatesProjectCopyToMetaModelService() {
    String email = "u@ex.com";
    Vsum vsum = new Vsum();
    givenVsumMember(email, vsum, VsumRole.MEMBER);
    MetaModel projectMetaModel = clonedMetaModel(101L, 10L);
    when(vsumMetaModelRepository.findByVsumAndMetaModel_Source_Id(vsum, 10L))
        .thenReturn(Optional.of(vsumMetaModel(vsum, projectMetaModel)));

    service.updateMetaModelEcore(email, 1L, 10L, "<edited/>");

    verify(metaModelService)
        .updateProjectEcore(projectMetaModel, "<edited/>".getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void updateMetaModelEcore_throwsAccessDenied_whenViewer() {
    String email = "u@ex.com";
    givenVsumMember(email, new Vsum(), VsumRole.VIEWER);

    assertThatThrownBy(() -> service.updateMetaModelEcore(email, 1L, 10L, "<edited/>"))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(metaModelService);
  }

  @Test
  void updateMetaModelEcore_throwsNotFound_whenMetaModelNotInVsum() {
    String email = "u@ex.com";
    Vsum vsum = new Vsum();
    givenVsumMember(email, vsum, VsumRole.MEMBER);
    when(vsumMetaModelRepository.findByVsumAndMetaModel_Source_Id(vsum, 10L))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.updateMetaModelEcore(email, 1L, 10L, "<edited/>"))
        .isInstanceOf(NotFoundException.class);
    verifyNoInteractions(metaModelService);
  }

  @Test
  void findById_returnsMapped_whenOwned() {
    String email = "u@ex.com";
    Vsum entity = new Vsum();
    entity.setId(9L);
    when(vsumRepository.findByIdAndUser_emailAndRemovedAtIsNull(9L, email))
        .thenReturn(Optional.of(entity));

    VsumResponse dto = new VsumResponse();
    dto.setId(9L);
    when(vsumMapper.toVsumResponse(entity)).thenReturn(dto);

    VsumResponse result = service.findById(email, 9L);

    assertThat(result.getId()).isEqualTo(9L);
  }

  @Test
  void findById_throwsNotFound_whenMissing() {
    String email = "u@ex.com";
    when(vsumRepository.findByIdAndUser_emailAndRemovedAtIsNull(9L, email))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.findById(email, 9L))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining(VSUM_ID_NOT_FOUND_ERROR);
  }

  @Test
  void remove_setsRemovedAtAndSaves_whenOwned() {
    String email = "u@ex.com";
    Vsum entity = new Vsum();
    entity.setId(3L);
    when(vsumRepository.findByIdAndUser_emailAndRemovedAtIsNull(3L, email))
        .thenReturn(Optional.of(entity));

    when(vsumRepository.save(any(Vsum.class))).thenAnswer(inv -> inv.getArgument(0));

    Vsum result = service.remove(email, 3L);

    assertThat(result.getRemovedAt()).isNotNull();
    assertThat(result.getRemovedAt()).isBeforeOrEqualTo(Instant.now());
    verify(vsumRepository).save(entity);
  }

  @Test
  void remove_throwsNotFound_whenMissing() {
    String email = "u@ex.com";
    when(vsumRepository.findByIdAndUser_emailAndRemovedAtIsNull(3L, email))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.remove(email, 3L))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining(VSUM_ID_NOT_FOUND_ERROR);
  }

  @Test
  void findVsumWithDetails_handlesNullChildLists_metaModelsAndRelations() {
    String email = "u@ex.com";

    User user = new User();
    user.setEmail(email);

    Vsum vsum = new Vsum();
    vsum.setId(77L);
    vsum.setVsumMetaModels(null);
    vsum.setMetaModelRelations(null);

    VsumUser vsumUser = new VsumUser();
    vsumUser.setUser(user);
    vsumUser.setVsum(vsum);
    vsum.setVsumUsers(Set.of(vsumUser));

    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull(email))
        .thenReturn(Optional.of(user));
    when(vsumRepository.findByIdAndRemovedAtIsNull(77L)).thenReturn(Optional.of(vsum));

    VsumMetaModelResponse base = new VsumMetaModelResponse();
    when(vsumMapper.toVsumMetaModelResponse(vsum)).thenReturn(base);

    VsumMetaModelResponse result = service.findVsumWithDetails(email, 77L);

    assertThat(result.getMetaModels()).isNotNull().isEmpty();
    assertThat(result.getMetaModelsRelation()).isNotNull().isEmpty();
    assertThat(result.getViews()).isNotNull().isEmpty();
    verifyNoInteractions(metaModelMapper, metaModelRelationMapper, vsumViewMapper);
  }

  @Test
  void findVsumWithDetails_mapsLists_whenPresent() {
    String email = "u@ex.com";

    User user = new User();
    user.setEmail(email);

    Vsum vsum = new Vsum();
    vsum.setId(78L);

    VsumUser vsumUser = new VsumUser();
    vsumUser.setUser(user);
    vsumUser.setVsum(vsum);
    vsum.setVsumUsers(Set.of(vsumUser));

    MetaModel mm = clonedMetaModel(101L, 1L);
    VsumMetaModel vmm = vsumMetaModel(vsum, mm);
    vmm.setName("Project model name");
    vsum.setVsumMetaModels(Set.of(vmm));

    MetaModelRelation rel =
        metaModelRelation(vsum, clonedMetaModel(11L, 11L), clonedMetaModel(22L, 22L));
    vsum.setMetaModelRelations(Set.of(rel));

    VsumView view = new VsumView();
    view.setId(901L);

    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull(email))
        .thenReturn(Optional.of(user));
    when(vsumRepository.findByIdAndRemovedAtIsNull(78L)).thenReturn(Optional.of(vsum));
    when(vsumViewRepository.findAllByVsum(vsum)).thenReturn(List.of(view));

    VsumMetaModelResponse base = new VsumMetaModelResponse();
    when(vsumMapper.toVsumMetaModelResponse(vsum)).thenReturn(base);

    MetaModelResponse mmResp = new MetaModelResponse();
    when(metaModelMapper.toMetaModelResponse(mm)).thenReturn(mmResp);

    MetaModelRelationResponse relResp = new MetaModelRelationResponse();
    when(metaModelRelationMapper.toMetaModelRelationResponse(eq(rel), any())).thenReturn(relResp);

    ViewsResponse viewResp = new ViewsResponse();
    viewResp.setId(901L);
    viewResp.setAssignedModels(List.of(mmResp));
    when(vsumViewMapper.toViewsResponse(view)).thenReturn(viewResp);

    VsumMetaModelResponse result = service.findVsumWithDetails(email, 78L);

    assertThat(result.getMetaModels()).containsExactly(mmResp);
    assertThat(result.getMetaModels())
        .extracting(MetaModelResponse::getName)
        .containsExactly("Project model name");
    assertThat(result.getMetaModelsRelation()).containsExactly(relResp);
    assertThat(result.getViews()).containsExactly(viewResp);
    assertThat(result.getViews().get(0).getAssignedModels()).containsExactly(mmResp);
  }

  @Test
  void findVsumWithDetails_throwsNotFound_whenMissing() {
    String email = "u@ex.com";

    User user = new User();
    user.setEmail(email);

    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull(email))
        .thenReturn(Optional.of(user));
    when(vsumRepository.findByIdAndRemovedAtIsNull(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.findVsumWithDetails(email, 1L))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining(VSUM_ID_NOT_FOUND_ERROR);

    verify(userRepository).findByEmailIgnoreCaseAndRemovedAtIsNull(email);
    verify(vsumRepository).findByIdAndRemovedAtIsNull(1L);
  }

  @Test
  void findAllByUser_mapsList() {
    Vsum a = new Vsum();
    a.setId(1L);

    VsumUser x = new VsumUser();
    x.setId(1L);
    x.setVsum(a);
    x.setRole(VsumRole.OWNER);

    Vsum b = new Vsum();
    b.setId(2L);

    VsumUser y = new VsumUser();
    y.setId(1L);
    y.setVsum(b);
    y.setRole(VsumRole.MEMBER);

    String email = "u@ex.com";
    when(vsumUserRepository.findAllByUser_EmailAndVsum_RemovedAtIsNull(email, Pageable.ofSize(1)))
        .thenReturn(List.of(x, y));

    VsumResponse ra = new VsumResponse();
    ra.setId(1L);
    ra.setRole(VsumRole.OWNER);
    VsumResponse rb = new VsumResponse();
    rb.setId(2L);
    rb.setRole(VsumRole.MEMBER);

    when(vsumMapper.toVsumResponse(any(Vsum.class)))
        .thenAnswer(
            inv -> {
              Vsum v = inv.getArgument(0);
              VsumResponse dto = new VsumResponse();
              dto.setId(v.getId());
              return dto;
            });

    List<VsumResponse> result = service.findAllByUser(email, null, Pageable.ofSize(1));

    assertThat(result).extracting(VsumResponse::getId).containsExactly(1L, 2L);
    assertThat(result)
        .extracting(VsumResponse::getRole)
        .containsExactly(VsumRole.OWNER, VsumRole.MEMBER);
  }

  @Test
  void update_throwsNotFound_whenNotOwnedOrMissing() {
    String email = "u@ex.com";
    when(vsumUserRepository
            .findByVsum_IdAndUser_EmailAndUser_RemovedAtIsNullAndVsum_RemovedAtIsNull(7L, email))
        .thenReturn(Optional.empty());

    VsumSyncChangesPutRequest put = new VsumSyncChangesPutRequest();

    assertThatThrownBy(() -> service.update(email, 7L, put))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining(VSUM_ID_NOT_FOUND_ERROR);

    verifyNoInteractions(vsumSyncService, vsumMetaModelRepository, vsumHistoryService);
  }

  @Test
  void update_delegatesTheSyncToVsumSyncService_withHistory() {
    String email = "u@ex.com";
    Vsum vsum = new Vsum();
    vsum.setId(1L);
    User member = new User();
    member.setEmail(email);
    VsumUser vsumUser = vsumUser(vsum, member);
    vsumUser.setRole(VsumRole.MEMBER);
    when(vsumUserRepository
            .findByVsum_IdAndUser_EmailAndUser_RemovedAtIsNullAndVsum_RemovedAtIsNull(1L, email))
        .thenReturn(Optional.of(vsumUser));
    VsumSyncChangesPutRequest put = new VsumSyncChangesPutRequest();
    when(vsumSyncService.applySyncChanges(vsum, member, put, true)).thenReturn(vsum);

    Vsum result = service.update(email, 1L, put);

    assertThat(result).isSameAs(vsum);
  }

  @Test
  void update_throwsAccessDenied_whenViewer() {
    String email = "u@ex.com";
    givenVsumMember(email, new Vsum(), VsumRole.VIEWER);
    VsumSyncChangesPutRequest put = new VsumSyncChangesPutRequest();

    assertThatThrownBy(() -> service.update(email, 1L, put))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(vsumSyncService);
  }

  @Test
  void findAllRemoved_returnsMappedResponses_forRemovedVsums() {
    Pageable pageable = PageRequest.of(0, 50, Sort.by("id").descending());

    Vsum vsum1 = Vsum.builder().id(1L).name("First").build();
    Vsum vsum2 = Vsum.builder().id(2L).name("Second").build();

    VsumUser u1 = VsumUser.builder().vsum(vsum1).build();
    VsumUser u2 = VsumUser.builder().vsum(vsum2).build();

    String callerEmail = "user@x.test";
    when(vsumUserRepository.findAllByUser_EmailAndVsum_RemovedAtIsNotNull(callerEmail, pageable))
        .thenReturn(List.of(u1, u2));

    when(vsumMapper.toVsumResponse(any(Vsum.class)))
        .thenAnswer(
            inv -> {
              Vsum v = inv.getArgument(0);
              VsumResponse resp = new VsumResponse();
              resp.setId(v.getId());
              return resp;
            });

    List<VsumResponse> result = service.findAllRemoved(callerEmail, pageable);

    assertThat(result).hasSize(2);
    verify(vsumUserRepository, times(1))
        .findAllByUser_EmailAndVsum_RemovedAtIsNotNull(callerEmail, pageable);
    verify(vsumMapper, times(2)).toVsumResponse(any(Vsum.class));
  }

  @Test
  void recovery_clearsRemovedAtAndSaves_whenOwned() {
    Vsum entity = new Vsum();
    entity.setId(42L);
    entity.setRemovedAt(Instant.now());
    String email = "u@ex.com";
    when(vsumRepository.findByIdAndUser_EmailAndUser_RemovedAtIsNullAndRemovedAtIsNotNull(
            42L, email))
        .thenReturn(Optional.of(entity));
    when(vsumRepository.save(any(Vsum.class))).thenAnswer(inv -> inv.getArgument(0));

    service.recovery(email, 42L);

    assertThat(entity.getRemovedAt()).isNull();
    verify(vsumRepository).save(entity);
  }

  @Test
  void recovery_throwsNotFound_whenMissing() {
    String email = "u@ex.com";
    when(vsumRepository.findByIdAndUser_EmailAndUser_RemovedAtIsNullAndRemovedAtIsNotNull(
            99L, email))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.recovery(email, 99L))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining(VSUM_ID_NOT_FOUND_ERROR);

    verify(vsumRepository, never()).save(any(Vsum.class));
  }

  @Test
  void deletePermanently_deletesTheProjectData_whenOwnedAndInTrash() {
    Vsum entity = new Vsum();
    entity.setId(42L);
    entity.setRemovedAt(Instant.now());
    String email = "u@ex.com";
    when(vsumRepository.findByIdAndUser_EmailAndUser_RemovedAtIsNullAndRemovedAtIsNotNull(
            42L, email))
        .thenReturn(Optional.of(entity));

    service.deletePermanently(email, 42L);

    verify(vsumCleanupService).deleteProjectData(entity);
  }

  @Test
  void deletePermanently_throwsNotFound_whenNotOwnedOrNotInTrash() {
    String email = "u@ex.com";
    when(vsumRepository.findByIdAndUser_EmailAndUser_RemovedAtIsNullAndRemovedAtIsNotNull(
            99L, email))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.deletePermanently(email, 99L))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining(VSUM_ID_NOT_FOUND_ERROR);

    verifyNoInteractions(vsumCleanupService);
  }

  @Test
  void getJarfat_shouldReturnTheJarOfTheBuildPipeline() {
    byte[] jarBytes = "FAKEJAR".getBytes(StandardCharsets.UTF_8);
    when(vsumBuildService.buildAndWait(BUILD_EMAIL, BUILD_ID)).thenReturn(jarBytes);

    assertThat(service.getJarfat(BUILD_EMAIL, BUILD_ID)).isEqualTo(jarBytes);
  }

  @Test
  void getJarfat_shouldPropagateBuildPipelineFailures() {
    when(vsumBuildService.buildAndWait(BUILD_EMAIL, BUILD_ID))
        .thenThrow(new org.springframework.security.access.AccessDeniedException("no"));

    assertThatThrownBy(() -> service.getJarfat(BUILD_EMAIL, BUILD_ID))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
  }
}
