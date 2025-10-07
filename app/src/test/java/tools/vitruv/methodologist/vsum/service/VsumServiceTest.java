package tools.vitruv.methodologist.vsum.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tools.vitruv.methodologist.messages.Error.VSUM_ID_NOT_FOUND_ERROR;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import tools.vitruv.methodologist.exception.NotFoundException;
import tools.vitruv.methodologist.exception.UnauthorizedException;
import tools.vitruv.methodologist.user.model.User;
import tools.vitruv.methodologist.user.model.repository.UserRepository;
import tools.vitruv.methodologist.vsum.VsumRole;
import tools.vitruv.methodologist.vsum.controller.dto.request.MetaModelRelationRequest;
import tools.vitruv.methodologist.vsum.controller.dto.request.VsumPostRequest;
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

@ExtendWith(MockitoExtension.class)
class VsumServiceTest {

  @Mock private VsumMapper vsumMapper;
  @Mock private VsumRepository vsumRepository;
  @Mock private MetaModelMapper metaModelMapper;
  @Mock private VsumMetaModelService vsumMetaModelService;
  @Mock private UserRepository userRepository;
  @Mock private VsumUserRepository vsumUserRepository;
  @Mock private VsumUserService vsumUserService;
  @Mock private MetaModelRelationService metaModelRelationService;
  @Mock private MetaModelRelationMapper metaModelRelationMapper;
  @Mock private VsumMetaModelRepository vsumMetaModelRepository;
  @Mock private MetaModelRelationRepository metaModelRelationRepository;
  @Mock private VsumHistoryService vsumHistoryService;

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
    MetaModelRelation metaModelRelation = new MetaModelRelation();
    metaModelRelation.setVsum(vsum);
    metaModelRelation.setSource(src);
    metaModelRelation.setTarget(tgt);
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
            vsumMetaModelService,
            userRepository,
            vsumUserRepository,
            vsumUserService,
            metaModelRelationService,
            metaModelRelationMapper,
            vsumMetaModelRepository,
            metaModelRelationRepository,
            vsumHistoryService);
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
    Vsum vsum = new Vsum();
    vsum.setId(77L);
    vsum.setVsumMetaModels(null);
    vsum.setMetaModelRelations(null);
    String email = "u@ex.com";
    when(vsumRepository.findByIdAndUser_emailAndRemovedAtIsNull(77L, email))
        .thenReturn(Optional.of(vsum));

    VsumMetaModelResponse base = new VsumMetaModelResponse();
    when(vsumMapper.toVsumMetaModelResponse(vsum)).thenReturn(base);

    VsumMetaModelResponse result = service.findVsumWithDetails(email, 77L);

    assertThat(result.getMetaModels()).isNotNull().isEmpty();
    assertThat(result.getMetaModelsRelation()).isNotNull().isEmpty();
    verifyNoInteractions(metaModelMapper, metaModelRelationMapper);
  }

  @Test
  void findVsumWithDetails_mapsLists_whenPresent() {
    Vsum vsum = new Vsum();
    vsum.setId(78L);

    MetaModel mm = clonedMetaModel(101L, 1L);
    VsumMetaModel vmm = vsumMetaModel(vsum, mm);
    vsum.setVsumMetaModels(Set.of(vmm));

    MetaModelRelation rel =
        metaModelRelation(vsum, clonedMetaModel(11L, 11L), clonedMetaModel(22L, 22L));
    vsum.setMetaModelRelations(Set.of(rel));

    String email = "u@ex.com";
    when(vsumRepository.findByIdAndUser_emailAndRemovedAtIsNull(78L, email))
        .thenReturn(Optional.of(vsum));

    VsumMetaModelResponse base = new VsumMetaModelResponse();
    when(vsumMapper.toVsumMetaModelResponse(vsum)).thenReturn(base);

    MetaModelResponse mmResp = new MetaModelResponse();
    when(metaModelMapper.toMetaModelResponse(mm)).thenReturn(mmResp);

    MetaModelRelationResponse relResp = new MetaModelRelationResponse();
    when(metaModelRelationMapper.toMetaModelRelationResponse(rel)).thenReturn(relResp);

    VsumMetaModelResponse result = service.findVsumWithDetails(email, 78L);

    assertThat(result.getMetaModels()).containsExactly(mmResp);
    assertThat(result.getMetaModelsRelation()).containsExactly(relResp);
  }

  @Test
  void findVsumWithDetails_throwsNotFound_whenMissing() {
    String email = "u@ex.com";
    when(vsumRepository.findByIdAndUser_emailAndRemovedAtIsNull(1L, email))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.findVsumWithDetails(email, 1L))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining(VSUM_ID_NOT_FOUND_ERROR);
  }

  @Test
  void findAllByUser_mapsList() {
    Vsum a = new Vsum();
    a.setId(1L);

    VsumUser x = new VsumUser();
    x.setId(1L);
    x.setVsum(a);

    Vsum b = new Vsum();
    b.setId(2L);

    VsumUser y = new VsumUser();
    y.setId(1L);
    y.setVsum(b);
    String email = "u@ex.com";
    when(vsumUserRepository.findAllByUser_EmailAndVsum_removedAtIsNull(email, Pageable.ofSize(1)))
        .thenReturn(List.of(x, y));

    VsumResponse ra = new VsumResponse();
    ra.setId(1L);
    VsumResponse rb = new VsumResponse();
    rb.setId(2L);
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
  }

  @Test
  void update_throwsNotFound_whenNotOwnedOrMissing() {
    String email = "u@ex.com";
    when(vsumUserRepository.findByVsum_idAndUser_emailAndVsum_RemovedAtIsNull(7L, email))
        .thenReturn(Optional.empty());

    VsumSyncChangesPutRequest put = new VsumSyncChangesPutRequest();

    assertThatThrownBy(() -> service.update(email, 7L, put))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining(VSUM_ID_NOT_FOUND_ERROR);

    verifyNoInteractions(
        metaModelRelationRepository,
        metaModelRelationService,
        vsumMetaModelRepository,
        vsumMetaModelService,
        vsumHistoryService);
  }

  @Test
  void update_removesRelations_whenPairsMissingInDesired_andWritesHistory() {
    Vsum vsum = new Vsum();
    vsum.setId(1L);
    vsum.setMetaModelRelations(new java.util.HashSet<>());
    vsum.setVsumMetaModels(new java.util.HashSet<>());
    User owner = new User();
    String email = "u@ex.com";
    owner.setEmail(email);
    when(vsumUserRepository.findByVsum_idAndUser_emailAndVsum_RemovedAtIsNull(1L, email))
        .thenReturn(Optional.of(vsumUser(vsum, owner)));

    MetaModel a = clonedMetaModel(10L, 100L);
    MetaModel b = clonedMetaModel(20L, 200L);
    MetaModel c = clonedMetaModel(30L, 300L);
    MetaModel d = clonedMetaModel(40L, 400L);

    MetaModelRelation relAB = metaModelRelation(vsum, a, b);
    MetaModelRelation relCD = metaModelRelation(vsum, c, d);
    when(metaModelRelationRepository.findAllByVsum(vsum)).thenReturn(List.of(relAB, relCD));
    when(vsumMetaModelRepository.findAllByVsum(vsum)).thenReturn(List.of()); // none

    VsumSyncChangesPutRequest put = new VsumSyncChangesPutRequest();
    put.setMetaModelRelationRequests(List.of(new MetaModelRelationRequest(100L, 200L, 999L)));

    Vsum result = service.update(email, 1L, put);

    verify(metaModelRelationService).delete(List.of(relCD));
    verify(vsumHistoryService).create(vsum, owner);
    verify(vsumRepository).save(vsum);
    assertThat(result.getMetaModelRelations()).isEmpty();
  }

  @Test
  void update_createsRelations_whenNewPairsAppear_andWritesHistory() {
    Vsum vsum = new Vsum();
    vsum.setId(2L);
    vsum.setMetaModelRelations(new java.util.HashSet<>());
    vsum.setVsumMetaModels(new java.util.HashSet<>());
    User owner = new User();
    String email = "u@ex.com";
    owner.setEmail(email);
    when(vsumUserRepository.findByVsum_idAndUser_emailAndVsum_RemovedAtIsNull(2L, email))
        .thenReturn(Optional.of(vsumUser(vsum, owner)));

    when(metaModelRelationRepository.findAllByVsum(vsum)).thenReturn(List.of());
    when(vsumMetaModelRepository.findAllByVsum(vsum)).thenReturn(List.of());

    MetaModelRelationRequest req = new MetaModelRelationRequest(100L, 200L, 777L);
    VsumSyncChangesPutRequest put = new VsumSyncChangesPutRequest();
    put.setMetaModelRelationRequests(List.of(req));

    service.update(email, 2L, put);

    verify(metaModelRelationService).create(vsum, List.of(req));
    verify(vsumHistoryService).create(vsum, owner);
    verify(vsumRepository).save(vsum);
  }

  @Test
  void update_removesVsumMetaModels_notInDesiredList_andWritesHistory() {
    Vsum vsum = new Vsum();
    vsum.setId(3L);
    vsum.setMetaModelRelations(new java.util.HashSet<>());
    vsum.setVsumMetaModels(new java.util.HashSet<>());
    User owner = new User();
    String email = "u@ex.com";
    owner.setEmail(email);
    when(vsumUserRepository.findByVsum_idAndUser_emailAndVsum_RemovedAtIsNull(3L, email))
        .thenReturn(Optional.of(vsumUser(vsum, owner)));

    MetaModel mm10 = clonedMetaModel(1000L, 10L);
    MetaModel mm20 = clonedMetaModel(2000L, 20L);
    MetaModel mm30 = clonedMetaModel(3000L, 30L);
    VsumMetaModel v10 = vsumMetaModel(vsum, mm10);
    VsumMetaModel v20 = vsumMetaModel(vsum, mm20);
    VsumMetaModel v30 = vsumMetaModel(vsum, mm30);
    when(vsumMetaModelRepository.findAllByVsum(vsum)).thenReturn(List.of(v10, v20, v30));
    when(metaModelRelationRepository.findAllByVsum(vsum)).thenReturn(List.of());

    VsumSyncChangesPutRequest put = new VsumSyncChangesPutRequest();
    put.setMetaModelIds(List.of(10L, 30L));

    Vsum result = service.update(email, 3L, put);

    verify(vsumMetaModelService).delete(vsum, List.of(v20));
    verify(vsumHistoryService).create(vsum, owner);
    verify(vsumRepository).save(vsum);
    assertThat(result.getVsumMetaModels()).doesNotContain(v20);
  }

  @Test
  void update_addsVsumMetaModels_whenNewIdsAppear_andWritesHistory() {
    Vsum vsum = new Vsum();
    vsum.setId(4L);
    vsum.setMetaModelRelations(new java.util.HashSet<>());
    vsum.setVsumMetaModels(new java.util.HashSet<>());
    User owner = new User();
    String email = "u@ex.com";
    owner.setEmail(email);
    when(vsumUserRepository.findByVsum_idAndUser_emailAndVsum_RemovedAtIsNull(4L, email))
        .thenReturn(Optional.of(vsumUser(vsum, owner)));

    MetaModel mm11 = clonedMetaModel(1011L, 11L);
    MetaModel mm12 = clonedMetaModel(1012L, 12L);
    VsumMetaModel v11 = vsumMetaModel(vsum, mm11);
    VsumMetaModel v12 = vsumMetaModel(vsum, mm12);
    when(vsumMetaModelRepository.findAllByVsum(vsum)).thenReturn(List.of(v11, v12));
    when(metaModelRelationRepository.findAllByVsum(vsum)).thenReturn(List.of());

    VsumSyncChangesPutRequest put = new VsumSyncChangesPutRequest();
    put.setMetaModelIds(List.of(11L, 12L, 13L));

    service.update(email, 4L, put);

    verify(vsumMetaModelService).create(vsum, Set.of(13L));
    verify(vsumHistoryService).create(vsum, owner);
    verify(vsumRepository).save(vsum);
  }

  @Test
  void update_mixedChanges_relationsAndMetaModels_andWritesHistoryOnce() {
    Vsum vsum = new Vsum();
    vsum.setId(5L);
    vsum.setMetaModelRelations(new java.util.HashSet<>());
    vsum.setVsumMetaModels(new java.util.HashSet<>());
    String email = "u@ex.com";
    User owner = new User();
    owner.setEmail(email);
    when(vsumUserRepository.findByVsum_idAndUser_emailAndVsum_RemovedAtIsNull(5L, email))
        .thenReturn(Optional.of(vsumUser(vsum, owner)));

    MetaModel m41 = clonedMetaModel(1041L, 41L);
    MetaModel m42 = clonedMetaModel(1042L, 42L);
    VsumMetaModel v41 = vsumMetaModel(vsum, m41);
    VsumMetaModel v42 = vsumMetaModel(vsum, m42);
    when(vsumMetaModelRepository.findAllByVsum(vsum)).thenReturn(List.of(v41, v42));

    MetaModel s10 = clonedMetaModel(10L, 10L);
    MetaModel t20 = clonedMetaModel(20L, 20L);
    MetaModel s30 = clonedMetaModel(30L, 30L);
    MetaModel t40 = clonedMetaModel(40L, 40L);
    MetaModelRelation r10And20 = metaModelRelation(vsum, s10, t20);
    MetaModelRelation r30And40 = metaModelRelation(vsum, s30, t40);
    when(metaModelRelationRepository.findAllByVsum(vsum)).thenReturn(List.of(r10And20, r30And40));

    MetaModelRelationRequest addRelationReq = new MetaModelRelationRequest(50L, 60L, 909L);

    VsumSyncChangesPutRequest put = new VsumSyncChangesPutRequest();
    put.setMetaModelRelationRequests(
        List.of(new MetaModelRelationRequest(10L, 20L, 111L), addRelationReq));
    put.setMetaModelIds(List.of(41L, 43L));

    service.update(email, 5L, put);

    verify(vsumMetaModelService).delete(vsum, List.of(v42));
    verify(vsumMetaModelService).create(vsum, Set.of(43L));
    verify(metaModelRelationService).delete(List.of(r30And40));
    verify(metaModelRelationService).create(vsum, List.of(addRelationReq));
    verify(vsumHistoryService).create(vsum, owner);
    verify(vsumRepository).save(vsum);
  }

  @Test
  void update_noChanges_noSideEffects_exceptSave_andNoHistory() {
    Vsum vsum = new Vsum();
    vsum.setId(6L);
    vsum.setMetaModelRelations(new java.util.HashSet<>());
    vsum.setVsumMetaModels(new java.util.HashSet<>());
    User owner = new User();
    String email = "u@ex.com";
    owner.setEmail(email);
    when(vsumUserRepository.findByVsum_idAndUser_emailAndVsum_RemovedAtIsNull(6L, email))
        .thenReturn(Optional.of(vsumUser(vsum, owner)));

    MetaModel m1 = clonedMetaModel(101L, 1L);
    MetaModel m2 = clonedMetaModel(102L, 2L);
    VsumMetaModel v1 = vsumMetaModel(vsum, m1);
    VsumMetaModel v2 = vsumMetaModel(vsum, m2);
    when(vsumMetaModelRepository.findAllByVsum(vsum)).thenReturn(List.of(v1, v2));

    MetaModel s1 = clonedMetaModel(1L, 1L);
    MetaModel t2 = clonedMetaModel(2L, 2L);
    MetaModelRelation r12 = metaModelRelation(vsum, s1, t2);
    when(metaModelRelationRepository.findAllByVsum(vsum)).thenReturn(List.of(r12));

    VsumSyncChangesPutRequest put = new VsumSyncChangesPutRequest();
    put.setMetaModelIds(List.of(1L, 2L));
    put.setMetaModelRelationRequests(List.of(new MetaModelRelationRequest(1L, 2L, 555L)));

    service.update(email, 6L, put);

    verify(vsumMetaModelService, never()).delete(any(), any());
    verify(vsumMetaModelService, never()).create(any(), any());
    verify(metaModelRelationService, never()).delete(any());
    verify(metaModelRelationService, never()).create(any(), any());
    verify(vsumHistoryService, never()).create(any(), any());
    verify(vsumRepository).save(vsum);
  }

  @Test
  void update_clearsAll_whenRequestListsAreNull_orEmpty_andWritesHistory() {
    Vsum vsum = new Vsum();
    vsum.setId(7L);
    vsum.setMetaModelRelations(new java.util.HashSet<>());
    vsum.setVsumMetaModels(new java.util.HashSet<>());
    User owner = new User();
    String email = "u@ex.com";
    owner.setEmail(email);
    when(vsumUserRepository.findByVsum_idAndUser_emailAndVsum_RemovedAtIsNull(7L, email))
        .thenReturn(Optional.of(vsumUser(vsum, owner)));

    MetaModel mm10 = clonedMetaModel(1000L, 10L);
    MetaModel mm20 = clonedMetaModel(2000L, 20L);
    VsumMetaModel v10 = vsumMetaModel(vsum, mm10);
    VsumMetaModel v20 = vsumMetaModel(vsum, mm20);
    when(vsumMetaModelRepository.findAllByVsum(vsum)).thenReturn(List.of(v10, v20));

    MetaModel s1 = clonedMetaModel(11L, 11L);
    MetaModel t2 = clonedMetaModel(22L, 22L);
    MetaModelRelation r = metaModelRelation(vsum, s1, t2);
    when(metaModelRelationRepository.findAllByVsum(vsum)).thenReturn(List.of(r));

    VsumSyncChangesPutRequest put = new VsumSyncChangesPutRequest();
    put.setMetaModelIds(null);
    put.setMetaModelRelationRequests(null);

    service.update(email, 7L, put);

    verify(vsumMetaModelService)
        .delete(eq(vsum), argThat(list -> list.size() == 2 && list.containsAll(List.of(v10, v20))));
    verify(metaModelRelationService).delete(List.of(r));
    verify(vsumHistoryService).create(vsum, owner);
    verify(vsumRepository).save(vsum);
  }
}
