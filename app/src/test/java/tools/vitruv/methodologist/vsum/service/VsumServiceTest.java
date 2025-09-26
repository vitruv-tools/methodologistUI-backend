package tools.vitruv.methodologist.vsum.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tools.vitruv.methodologist.messages.Error.VSUM_ID_NOT_FOUND_ERROR;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.vitruv.methodologist.exception.NotFoundException;
import tools.vitruv.methodologist.exception.UnauthorizedException;
import tools.vitruv.methodologist.user.model.User;
import tools.vitruv.methodologist.user.model.repository.UserRepository;
import tools.vitruv.methodologist.vsum.VsumRole;
import tools.vitruv.methodologist.vsum.controller.dto.request.VsumPostRequest;
import tools.vitruv.methodologist.vsum.controller.dto.request.VsumSyncChangesPutRequest;
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
            metaModelRelationRepository);
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

    VsumPostRequest req = new VsumPostRequest();

    assertThatThrownBy(() -> service.create(email, req)).isInstanceOf(UnauthorizedException.class);

    verify(vsumRepository, never()).save(any(Vsum.class));
  }

  @Test
  void update_throwsNotFound_whenNotOwnedOrMissing() {
    String email = "u@ex.com";
    when(vsumRepository.findByIdAndUser_emailAndRemovedAtIsNull(7L, email))
        .thenReturn(Optional.empty());

    VsumSyncChangesPutRequest put = new VsumSyncChangesPutRequest();

    assertThatThrownBy(() -> service.update(email, 7L, put))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining(VSUM_ID_NOT_FOUND_ERROR);
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

    ArgumentCaptor<Vsum> captor = ArgumentCaptor.forClass(Vsum.class);
    when(vsumRepository.save(any(Vsum.class))).thenAnswer(inv -> inv.getArgument(0));

    Vsum result = service.remove(email, 3L);

    assertThat(result.getRemovedAt()).isNotNull();
    assertThat(result.getRemovedAt()).isBeforeOrEqualTo(Instant.now());
    verify(vsumRepository).save(captor.capture());
    assertThat(captor.getValue().getRemovedAt()).isNotNull();
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
  void findVsumWithDetails_handlesNullChildList() {
    String email = "u@ex.com";
    Vsum vsum = new Vsum();
    vsum.setId(77L);
    vsum.setVsumMetaModels(null);
    when(vsumRepository.findByIdAndUser_emailAndRemovedAtIsNull(77L, email))
        .thenReturn(Optional.of(vsum));

    VsumMetaModelResponse base = new VsumMetaModelResponse();
    when(vsumMapper.toVsumMetaModelResponse(vsum)).thenReturn(base);

    VsumMetaModelResponse result = service.findVsumWithDetails(email, 77L);

    assertThat(result.getMetaModels()).isNotNull();
    assertThat(result.getMetaModels()).isEmpty();
    verifyNoInteractions(metaModelMapper);
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
    String email = "u@ex.com";
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
    when(vsumUserRepository.findAllByUser_Email(email)).thenReturn(List.of(x, y));

    VsumResponse ra = new VsumResponse();
    ra.setId(1L);
    VsumResponse rb = new VsumResponse();
    rb.setId(2L);
    when(vsumMapper.toVsumResponse(a)).thenReturn(ra);
    when(vsumMapper.toVsumResponse(b)).thenReturn(rb);

    List<VsumResponse> result = service.findAllByUser(email);

    assertThat(result).extracting(VsumResponse::getId).containsExactly(1L, 2L);
  }

  @Test
  void update_throwsNotFound_whenNotOwnedOrMissing_new() {
    String email = "u@ex.com";
    when(vsumRepository.findByIdAndUser_emailAndRemovedAtIsNull(7L, email))
        .thenReturn(Optional.empty());

    VsumSyncChangesPutRequest put = new VsumSyncChangesPutRequest();

    assertThatThrownBy(() -> service.update(email, 7L, put))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining(VSUM_ID_NOT_FOUND_ERROR);

    verifyNoInteractions(
        metaModelRelationRepository,
        metaModelRelationService,
        vsumMetaModelRepository,
        vsumMetaModelService);
  }

  @Test
  void update_removesRelations_whenPairsMissingInDesired() {
    String email = "u@ex.com";

    Vsum vsum = new Vsum();
    vsum.setId(1L);
    vsum.setMetaModelRelations(new java.util.ArrayList<>());
    vsum.setVsumMetaModels(new java.util.ArrayList<>());
    when(vsumRepository.findByIdAndUser_emailAndRemovedAtIsNull(1L, email))
        .thenReturn(Optional.of(vsum));

    MetaModel a = clonedMetaModel(10L, 100L);
    MetaModel b = clonedMetaModel(20L, 200L);
    MetaModel c = clonedMetaModel(30L, 300L);
    MetaModel d = clonedMetaModel(40L, 400L);

    MetaModelRelation relAB = metaModelRelation(vsum, a, b);
    MetaModelRelation relCD = metaModelRelation(vsum, c, d);

    when(metaModelRelationRepository.findAllByVsum(vsum)).thenReturn(List.of(relAB, relCD));

    VsumSyncChangesPutRequest put = new VsumSyncChangesPutRequest();
    put.setMetaModelRelationRequests(
        List.of(
            new tools.vitruv.methodologist.vsum.controller.dto.request.MetaModelRelationRequest(
                100L, 200L, 999L)));

    Vsum result = service.update(email, 1L, put);

    verify(metaModelRelationService).delete(List.of(relCD));
    verify(metaModelRelationService)
        .delete(
            org.mockito.ArgumentMatchers.argThat(list -> list.size() == 1 && list.contains(relCD)));

    assertThat(result.getMetaModelRelations()).doesNotContain(relCD);

    verify(vsumRepository).save(vsum);
  }

  @Test
  void update_createsRelations_whenNewPairsAppear() {
    String email = "u@ex.com";

    Vsum vsum = new Vsum();
    vsum.setId(2L);
    vsum.setMetaModelRelations(new java.util.ArrayList<>());
    vsum.setVsumMetaModels(new java.util.ArrayList<>());
    when(vsumRepository.findByIdAndUser_emailAndRemovedAtIsNull(2L, email))
        .thenReturn(Optional.of(vsum));

    when(metaModelRelationRepository.findAllByVsum(vsum)).thenReturn(List.of());

    var req =
        new tools.vitruv.methodologist.vsum.controller.dto.request.MetaModelRelationRequest(
            100L, 200L, 777L);

    VsumSyncChangesPutRequest put = new VsumSyncChangesPutRequest();
    put.setMetaModelRelationRequests(List.of(req));

    Vsum result = service.update(email, 2L, put);

    verify(metaModelRelationService).create(vsum, List.of(req));

    verify(vsumRepository).save(vsum);
  }

  @Test
  void update_removesVsumMetaModels_notInDesiredList() {
    String email = "u@ex.com";

    Vsum vsum = new Vsum();
    vsum.setId(3L);
    vsum.setMetaModelRelations(new java.util.ArrayList<>());
    vsum.setVsumMetaModels(new java.util.ArrayList<>());
    when(vsumRepository.findByIdAndUser_emailAndRemovedAtIsNull(3L, email))
        .thenReturn(Optional.of(vsum));

    MetaModel mm10 = clonedMetaModel(1000L, 10L);
    MetaModel mm20 = clonedMetaModel(2000L, 20L);
    MetaModel mm30 = clonedMetaModel(3000L, 30L);
    VsumMetaModel v10 = vsumMetaModel(vsum, mm10);
    VsumMetaModel v20 = vsumMetaModel(vsum, mm20);
    VsumMetaModel v30 = vsumMetaModel(vsum, mm30);

    when(vsumMetaModelRepository.findAllByVsum(vsum)).thenReturn(List.of(v10, v20, v30));

    VsumSyncChangesPutRequest put = new VsumSyncChangesPutRequest();
    put.setMetaModelIds(List.of(10L, 30L));

    when(metaModelRelationRepository.findAllByVsum(vsum)).thenReturn(List.of());

    Vsum result = service.update(email, 3L, put);

    verify(vsumMetaModelService).delete(vsum, List.of(v20));
    assertThat(result.getVsumMetaModels()).doesNotContain(v20);

    verify(vsumRepository).save(vsum);
  }

  @Test
  void update_addsVsumMetaModels_whenNewIdsAppear() {
    String email = "u@ex.com";

    Vsum vsum = new Vsum();
    vsum.setId(4L);
    vsum.setMetaModelRelations(new java.util.ArrayList<>());
    vsum.setVsumMetaModels(new java.util.ArrayList<>());
    when(vsumRepository.findByIdAndUser_emailAndRemovedAtIsNull(4L, email))
        .thenReturn(Optional.of(vsum));

    MetaModel mm11 = clonedMetaModel(1011L, 11L);
    MetaModel mm12 = clonedMetaModel(1012L, 12L);
    VsumMetaModel v11 = vsumMetaModel(vsum, mm11);
    VsumMetaModel v12 = vsumMetaModel(vsum, mm12);
    when(vsumMetaModelRepository.findAllByVsum(vsum)).thenReturn(List.of(v11, v12));

    VsumSyncChangesPutRequest put = new VsumSyncChangesPutRequest();
    put.setMetaModelIds(List.of(11L, 12L, 13L));

    when(metaModelRelationRepository.findAllByVsum(vsum)).thenReturn(List.of());

    service.update(email, 4L, put);

    verify(vsumMetaModelService).create(vsum, new java.util.HashSet<>(java.util.List.of(13L)));

    verify(vsumRepository).save(vsum);
  }

  @Test
  void update_mixedChanges_relationsAndMetaModels() {
    String email = "u@ex.com";

    Vsum vsum = new Vsum();
    vsum.setId(5L);
    vsum.setMetaModelRelations(new java.util.ArrayList<>());
    vsum.setVsumMetaModels(new java.util.ArrayList<>());
    when(vsumRepository.findByIdAndUser_emailAndRemovedAtIsNull(5L, email))
        .thenReturn(Optional.of(vsum));

    MetaModel m41 = clonedMetaModel(1041L, 41L);
    MetaModel m42 = clonedMetaModel(1042L, 42L);
    VsumMetaModel v41 = vsumMetaModel(vsum, m41);
    VsumMetaModel v42 = vsumMetaModel(vsum, m42);
    when(vsumMetaModelRepository.findAllByVsum(vsum)).thenReturn(List.of(v41, v42));

    MetaModel s10 = clonedMetaModel(10L, 10L);
    MetaModel t20 = clonedMetaModel(20L, 20L);
    MetaModel s30 = clonedMetaModel(30L, 30L);
    MetaModel t40 = clonedMetaModel(40L, 40L);
    MetaModelRelation r10_20 = metaModelRelation(vsum, s10, t20);
    MetaModelRelation r30_40 = metaModelRelation(vsum, s30, t40);
    when(metaModelRelationRepository.findAllByVsum(vsum)).thenReturn(List.of(r10_20, r30_40));

    var addRelationReq =
        new tools.vitruv.methodologist.vsum.controller.dto.request.MetaModelRelationRequest(
            50L, 60L, 909L);

    VsumSyncChangesPutRequest put = new VsumSyncChangesPutRequest();
    put.setMetaModelRelationRequests(
        List.of(
            new tools.vitruv.methodologist.vsum.controller.dto.request.MetaModelRelationRequest(
                10L, 20L, 111L),
            addRelationReq));
    put.setMetaModelIds(List.of(41L, 43L));

    service.update(email, 5L, put);

    verify(vsumMetaModelService).delete(vsum, List.of(v42));
    verify(vsumMetaModelService).create(vsum, new java.util.HashSet<>(java.util.List.of(43L)));

    verify(metaModelRelationService).delete(List.of(r30_40));
    verify(metaModelRelationService).create(vsum, List.of(addRelationReq));

    verify(vsumRepository).save(vsum);
  }

  @Test
  void update_noChanges_noServiceCallsExceptSave() {
    String email = "u@ex.com";
    Vsum vsum = new Vsum();
    vsum.setId(6L);
    vsum.setMetaModelRelations(new java.util.ArrayList<>());
    vsum.setVsumMetaModels(new java.util.ArrayList<>());
    when(vsumRepository.findByIdAndUser_emailAndRemovedAtIsNull(6L, email))
        .thenReturn(Optional.of(vsum));

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
    put.setMetaModelRelationRequests(
        List.of(
            new tools.vitruv.methodologist.vsum.controller.dto.request.MetaModelRelationRequest(
                1L, 2L, 555L)));

    service.update(email, 6L, put);

    verify(vsumMetaModelService, never()).delete(any(), any());
    verify(vsumMetaModelService, never()).create(any(), any());
    verify(metaModelRelationService, never()).delete(any());
    verify(metaModelRelationService, never()).create(any(), any());
    verify(vsumRepository).save(vsum);
  }
}
