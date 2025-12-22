package tools.vitruv.methodologist.vsum.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tools.vitruv.methodologist.messages.Error.VSUM_ID_NOT_FOUND_ERROR;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import tools.vitruv.methodologist.exception.NotFoundException;
import tools.vitruv.methodologist.exception.UnauthorizedException;
import tools.vitruv.methodologist.general.model.FileStorage;
import tools.vitruv.methodologist.user.model.User;
import tools.vitruv.methodologist.user.model.repository.UserRepository;
import tools.vitruv.methodologist.vsum.VsumRole;
import tools.vitruv.methodologist.vsum.build.BuildCoordinator;
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

  @InjectMocks VsumService job;
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
  @Mock private MetaModelVitruvIntegrationService metaModelVitruvIntegrationService;
  @Mock private BuildCoordinator buildCoordinator;

  private VsumService service;

  private static Map<String, byte[]> unzip(byte[] zipBytes) throws IOException {
    Map<String, byte[]> entries = new LinkedHashMap<>();
    try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
      ZipEntry e;
      while ((e = zis.getNextEntry()) != null) {
        entries.put(e.getName(), zis.readAllBytes());
        zis.closeEntry();
      }
    }
    return entries;
  }

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

  private FileStorage fs(Long id, String filename, byte[] data) {
    FileStorage f = new FileStorage();
    f.setId(id);
    f.setFilename(filename);
    f.setData(data);
    return f;
  }

  private MetaModel mm(FileStorage ecore, FileStorage gen) {
    MetaModel m = new MetaModel();
    m.setEcoreFile(ecore);
    m.setGenModelFile(gen);
    return m;
  }

  private MetaModelRelation rel(MetaModel source, MetaModel target, FileStorage reaction) {
    MetaModelRelation r = new MetaModelRelation();
    r.setSource(source);
    r.setTarget(target);
    r.setReactionFileStorage(reaction);
    return r;
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
            vsumHistoryService,
            metaModelVitruvIntegrationService,
            buildCoordinator);
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
    verifyNoInteractions(metaModelMapper, metaModelRelationMapper);
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
    vsum.setVsumMetaModels(Set.of(vmm));

    MetaModelRelation rel =
        metaModelRelation(vsum, clonedMetaModel(11L, 11L), clonedMetaModel(22L, 22L));
    vsum.setMetaModelRelations(Set.of(rel));

    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull(email))
        .thenReturn(Optional.of(user));
    when(vsumRepository.findByIdAndRemovedAtIsNull(78L)).thenReturn(Optional.of(vsum));

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
    when(vsumUserRepository
            .findByVsum_IdAndUser_EmailAndUser_RemovedAtIsNullAndVsum_RemovedAtIsNull(1L, email))
        .thenReturn(Optional.of(vsumUser(vsum, owner)));

    MetaModel a = clonedMetaModel(10L, 100L);
    MetaModel b = clonedMetaModel(20L, 200L);
    MetaModel c = clonedMetaModel(30L, 300L);
    MetaModel d = clonedMetaModel(40L, 400L);

    MetaModelRelation relAB = metaModelRelation(vsum, a, b);
    MetaModelRelation relCD = metaModelRelation(vsum, c, d);
    when(metaModelRelationRepository.findAllByVsum(vsum)).thenReturn(List.of(relAB, relCD));
    when(vsumMetaModelRepository.findAllByVsum(vsum)).thenReturn(List.of());

    VsumSyncChangesPutRequest put = new VsumSyncChangesPutRequest();
    put.setMetaModelRelationRequests(List.of(new MetaModelRelationRequest(100L, 200L, 999L)));

    Vsum result = service.update(email, 1L, put);

    verify(metaModelRelationService).delete(List.of(relCD));
    verify(metaModelRelationService)
        .delete(
            argThat((List<MetaModelRelation> list) -> list.size() == 1 && list.contains(relCD)));
    assertThat(result.getMetaModelRelations()).isEmpty();
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
    when(vsumUserRepository
            .findByVsum_IdAndUser_EmailAndUser_RemovedAtIsNullAndVsum_RemovedAtIsNull(2L, email))
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
    when(vsumUserRepository
            .findByVsum_IdAndUser_EmailAndUser_RemovedAtIsNullAndVsum_RemovedAtIsNull(3L, email))
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
    when(vsumUserRepository
            .findByVsum_IdAndUser_EmailAndUser_RemovedAtIsNullAndVsum_RemovedAtIsNull(4L, email))
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
    when(vsumUserRepository
            .findByVsum_IdAndUser_EmailAndUser_RemovedAtIsNullAndVsum_RemovedAtIsNull(5L, email))
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
    when(vsumUserRepository
            .findByVsum_IdAndUser_EmailAndUser_RemovedAtIsNullAndVsum_RemovedAtIsNull(6L, email))
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
    when(vsumUserRepository
            .findByVsum_IdAndUser_EmailAndUser_RemovedAtIsNullAndVsum_RemovedAtIsNull(7L, email))
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

  @Test
  void delete_invokesSubservices_forEachOldVsum_andUses30DayCutoff() {
    Vsum a = new Vsum();
    a.setId(1L);
    Vsum b = new Vsum();
    b.setId(2L);

    when(vsumRepository.findAllByRemovedAtBefore(any(Instant.class))).thenReturn(List.of(a, b));

    job.delete();

    ArgumentCaptor<Instant> cutoffCap = ArgumentCaptor.forClass(Instant.class);
    verify(vsumRepository).findAllByRemovedAtBefore(cutoffCap.capture());

    Instant cutoff = cutoffCap.getValue();
    long days = ChronoUnit.DAYS.between(cutoff, Instant.now());
    assertThat(days).isBetween(29L, 31L);

    verify(vsumUserService, times(1))
        .delete(argThat(v -> v != null && Long.valueOf(1L).equals(v.getId())));
    verify(vsumUserService, times(1))
        .delete(argThat(v -> v != null && Long.valueOf(2L).equals(v.getId())));

    verify(vsumMetaModelService, times(1))
        .delete(argThat(v -> v != null && Long.valueOf(1L).equals(v.getId())));
    verify(vsumMetaModelService, times(1))
        .delete(argThat(v -> v != null && Long.valueOf(2L).equals(v.getId())));

    verify(metaModelRelationService, times(1))
        .deleteByVsum(argThat(v -> v != null && Long.valueOf(1L).equals(v.getId())));
    verify(metaModelRelationService, times(1))
        .deleteByVsum(argThat(v -> v != null && Long.valueOf(2L).equals(v.getId())));

    verify(vsumHistoryService, times(1))
        .delete(argThat(v -> v != null && Long.valueOf(1L).equals(v.getId())));
    verify(vsumHistoryService, times(1))
        .delete(argThat(v -> v != null && Long.valueOf(2L).equals(v.getId())));
    verifyNoMoreInteractions(
        vsumUserService, vsumMetaModelService, metaModelRelationService, vsumHistoryService);
  }

  @Test
  void delete_whenNoOldVsums_doesNothing() {
    when(vsumRepository.findAllByRemovedAtBefore(any(Instant.class))).thenReturn(List.of());

    job.delete();

    verify(vsumRepository).findAllByRemovedAtBefore(any(Instant.class));
    verifyNoInteractions(vsumUserService, vsumMetaModelService, metaModelRelationService);
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
  void getJarfat_shouldThrowAccessDenied_whenUserNotMember() {
    when(vsumUserRepository
            .findByVsum_IdAndUser_EmailAndUser_RemovedAtIsNullAndVsum_RemovedAtIsNull(
                anyLong(), anyString()))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getJarfat("x@y.com", 1L))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);

    verify(metaModelVitruvIntegrationService, never())
        .runVitruvAndGetFatJarBytes(anyList(), anyList(), anyList());
  }

  @Test
  void getJarfat_shouldReturnZipContainingJarAndDockerfile_whenAuthorized() throws Exception {
    String email = "x@y.com";
    Long id = 1L;

    Vsum vsum = new Vsum();
    VsumUser vu = new VsumUser();
    vu.setVsum(vsum);

    byte[] jarBytes = "FAKEJAR".getBytes(StandardCharsets.UTF_8);
    when(vsumUserRepository
            .findByVsum_IdAndUser_EmailAndUser_RemovedAtIsNullAndVsum_RemovedAtIsNull(id, email))
        .thenReturn(Optional.of(vu));

    FileStorage e1 = fs(1L, "a.ecore", new byte[] {1});
    FileStorage g1 = fs(2L, "a.genmodel", new byte[] {2});
    FileStorage r1 = fs(3L, "x.reactions", new byte[] {3});

    vsum.setMetaModelRelations(Set.of(rel(mm(e1, g1), null, r1)));

    when(metaModelVitruvIntegrationService.runVitruvAndGetFatJarBytes(
            anyList(), anyList(), anyList()))
        .thenReturn(jarBytes);

    byte[] zip = service.getJarfat(email, id);

    Map<String, byte[]> entries = unzip(zip);
    assertThat(entries)
        .containsKeys(
            "methodologisttemplate.vsum-0.1.0-SNAPSHOT-jar-with-dependencies.jar", "Dockerfile");

    assertThat(entries.get("methodologisttemplate.vsum-0.1.0-SNAPSHOT-jar-with-dependencies.jar"))
        .isEqualTo(jarBytes);

    String dockerfile = new String(entries.get("Dockerfile"), StandardCharsets.UTF_8);
    assertThat(dockerfile).contains("FROM eclipse-temurin:17-jre-alpine");
    assertThat(dockerfile).contains("ENTRYPOINT");
  }

  @Test
  void buildOrThrow_shouldThrowNotFound_whenMetaModelRelationsNull() {
    Vsum vsum = new Vsum();
    vsum.setMetaModelRelations(null);

    assertThatThrownBy(() -> service.buildOrThrow(vsum)).isInstanceOf(NotFoundException.class);

    verify(metaModelVitruvIntegrationService, never())
        .runVitruvAndGetFatJarBytes(anyList(), anyList(), anyList());
  }

  @Test
  void buildOrThrow_shouldThrowNotFound_whenMetaModelRelationsEmpty() {
    Vsum vsum = new Vsum();
    vsum.setMetaModelRelations(Set.of());

    assertThatThrownBy(() -> service.buildOrThrow(vsum)).isInstanceOf(NotFoundException.class);

    verify(metaModelVitruvIntegrationService, never())
        .runVitruvAndGetFatJarBytes(anyList(), anyList(), anyList());
  }

  @Test
  void buildOrThrow_shouldThrowNotFound_whenRelationIsNull() {
    Vsum vsum = new Vsum();
    vsum.setMetaModelRelations(Collections.singleton(null));

    assertThatThrownBy(() -> service.buildOrThrow(vsum)).isInstanceOf(NotFoundException.class);

    verify(metaModelVitruvIntegrationService, never())
        .runVitruvAndGetFatJarBytes(anyList(), anyList(), anyList());
  }

  @Test
  void buildOrThrow_shouldThrowNotFound_whenNoMetamodelPairsCollected() {
    Vsum vsum = new Vsum();

    FileStorage e = fs(1L, "a.ecore", new byte[] {1});
    MetaModel source = mm(e, null);

    FileStorage g = fs(2L, "a.genmodel", new byte[] {2});
    MetaModel target = mm(null, g);

    vsum.setMetaModelRelations(Set.of(rel(source, target, fs(3L, "r.reactions", new byte[] {3}))));

    assertThatThrownBy(() -> service.buildOrThrow(vsum)).isInstanceOf(NotFoundException.class);

    verify(metaModelVitruvIntegrationService, never())
        .runVitruvAndGetFatJarBytes(anyList(), anyList(), anyList());
  }

  @Test
  void buildOrThrow_shouldThrowNotFound_whenNoReactions() {
    Vsum vsum = new Vsum();

    FileStorage e = fs(1L, "a.ecore", new byte[] {1});
    FileStorage g = fs(2L, "a.genmodel", new byte[] {2});
    vsum.setMetaModelRelations(Set.of(rel(mm(e, g), null, null)));

    assertThatThrownBy(() -> service.buildOrThrow(vsum)).isInstanceOf(NotFoundException.class);

    verify(metaModelVitruvIntegrationService, never())
        .runVitruvAndGetFatJarBytes(anyList(), anyList(), anyList());
  }

  @Test
  void buildOrThrow_shouldDeduplicateById_whenSameFilesAppearMultipleTimes() {
    Vsum vsum = new Vsum();

    FileStorage e1 = fs(10L, "dup.ecore", new byte[] {1});
    FileStorage g1 = fs(11L, "dup.genmodel", new byte[] {2});
    FileStorage r1 = fs(12L, "a.reactions", new byte[] {3});

    MetaModel m1 = mm(e1, g1);
    MetaModel m2 = mm(e1, g1);

    vsum.setMetaModelRelations(
        Set.of(rel(m1, m2, r1), rel(m1, null, fs(13L, "b.reactions", new byte[] {4}))));

    byte[] jar = "JAR".getBytes(StandardCharsets.UTF_8);
    when(metaModelVitruvIntegrationService.runVitruvAndGetFatJarBytes(
            anyList(), anyList(), anyList()))
        .thenReturn(jar);

    byte[] out = service.buildOrThrow(vsum);
    assertThat(out).isEqualTo(jar);

    ArgumentCaptor<List<FileStorage>> ecoresCap = ArgumentCaptor.forClass(List.class);
    ArgumentCaptor<List<FileStorage>> gensCap = ArgumentCaptor.forClass(List.class);
    ArgumentCaptor<List<FileStorage>> reactionsCap = ArgumentCaptor.forClass(List.class);

    verify(metaModelVitruvIntegrationService)
        .runVitruvAndGetFatJarBytes(ecoresCap.capture(), gensCap.capture(), reactionsCap.capture());

    assertThat(ecoresCap.getValue()).hasSize(1);
    assertThat(gensCap.getValue()).hasSize(1);

    assertThat(reactionsCap.getValue()).hasSize(2);
  }

  @Test
  void buildOrThrow_shouldDeduplicateByFilename_whenIdIsNull() {
    Vsum vsum = new Vsum();

    FileStorage e1 = fs(null, "same.ecore", new byte[] {1});
    FileStorage g1 = fs(null, "same.genmodel", new byte[] {2});
    FileStorage r1 = fs(null, "a.reactions", new byte[] {3});

    FileStorage e2 = fs(null, "same.ecore", new byte[] {9});
    FileStorage g2 = fs(null, "same.genmodel", new byte[] {9});

    vsum.setMetaModelRelations(
        Set.of(
            rel(mm(e1, g1), null, r1),
            rel(mm(e2, g2), null, fs(null, "b.reactions", new byte[] {4}))));

    when(metaModelVitruvIntegrationService.runVitruvAndGetFatJarBytes(
            anyList(), anyList(), anyList()))
        .thenReturn(new byte[] {7});

    service.buildOrThrow(vsum);

    ArgumentCaptor<List<FileStorage>> ecoresCap = ArgumentCaptor.forClass(List.class);
    ArgumentCaptor<List<FileStorage>> gensCap = ArgumentCaptor.forClass(List.class);

    verify(metaModelVitruvIntegrationService)
        .runVitruvAndGetFatJarBytes(ecoresCap.capture(), gensCap.capture(), anyList());

    assertThat(ecoresCap.getValue()).hasSize(1);
    assertThat(gensCap.getValue()).hasSize(1);
  }

  @Test
  void buildOrThrow_shouldPropagateVsumBuildingException_fromIntegration() {
    Vsum vsum = new Vsum();

    FileStorage e1 = fs(1L, "a.ecore", new byte[] {1});
    FileStorage g1 = fs(2L, "a.genmodel", new byte[] {2});
    FileStorage r1 = fs(3L, "a.reactions", new byte[] {3});
    vsum.setMetaModelRelations(Set.of(rel(mm(e1, g1), null, r1)));

    when(metaModelVitruvIntegrationService.runVitruvAndGetFatJarBytes(
            anyList(), anyList(), anyList()))
        .thenThrow(new tools.vitruv.methodologist.exception.VsumBuildingException("boom"));

    assertThatThrownBy(() -> service.buildOrThrow(vsum))
        .isInstanceOf(tools.vitruv.methodologist.exception.VsumBuildingException.class)
        .hasMessageContaining("boom");
  }

  @Test
  void dockerfileBytes_shouldBeDeterministicAndContainExpectedInstructions() {
    String dockerfile = new String(service.dockerfileBytes(), StandardCharsets.UTF_8);
    assertThat(dockerfile).contains("FROM eclipse-temurin:17-jre-alpine");
    assertThat(dockerfile).contains("WORKDIR /app");
    assertThat(dockerfile).contains("COPY app.jar /app/app.jar");
    assertThat(dockerfile).contains("EXPOSE 8080");
    assertThat(dockerfile).contains("ENTRYPOINT");
  }
}
