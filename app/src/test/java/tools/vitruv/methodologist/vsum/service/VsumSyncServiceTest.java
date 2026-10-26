package tools.vitruv.methodologist.vsum.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.vitruv.methodologist.general.model.FileStorage;
import tools.vitruv.methodologist.user.model.User;
import tools.vitruv.methodologist.vsum.controller.dto.request.FineGranularMetaModelRelationRequest;
import tools.vitruv.methodologist.vsum.controller.dto.request.MetaModelRelationRequest;
import tools.vitruv.methodologist.vsum.controller.dto.request.ViewRequest;
import tools.vitruv.methodologist.vsum.controller.dto.request.VsumSyncChangesPutRequest;
import tools.vitruv.methodologist.vsum.mapper.LowCodeReactionRequestMapper;
import tools.vitruv.methodologist.vsum.model.MetaModel;
import tools.vitruv.methodologist.vsum.model.MetaModelRelation;
import tools.vitruv.methodologist.vsum.model.Vsum;
import tools.vitruv.methodologist.vsum.model.VsumMetaModel;
import tools.vitruv.methodologist.vsum.model.VsumView;
import tools.vitruv.methodologist.vsum.model.VsumViewMetaModel;
import tools.vitruv.methodologist.vsum.model.repository.MetaModelRelationRepository;
import tools.vitruv.methodologist.vsum.model.repository.VsumMetaModelRepository;
import tools.vitruv.methodologist.vsum.model.repository.VsumRepository;
import tools.vitruv.methodologist.vsum.model.repository.VsumViewMetaModelRepository;
import tools.vitruv.methodologist.vsum.model.repository.VsumViewRepository;

@ExtendWith(MockitoExtension.class)
class VsumSyncServiceTest {

  @Mock private VsumRepository vsumRepository;
  @Mock private VsumMetaModelService vsumMetaModelService;
  @Mock private VsumMetaModelRepository vsumMetaModelRepository;
  @Mock private MetaModelRelationService metaModelRelationService;
  @Mock private MetaModelRelationRepository metaModelRelationRepository;
  @Mock private VsumHistoryService vsumHistoryService;
  @Mock private VsumViewMetaModelService vsumViewMetaModelService;
  @Mock private VsumViewService vsumViewService;
  @Mock private VsumViewRepository vsumViewRepository;
  @Mock private VsumViewMetaModelRepository vsumViewMetaModelRepository;
  @Mock private FineGranularMetaModelRelationService fineGranularMetaModelRelationService;
  @Mock private LowCodeReactionRequestMapper lowCodeReactionRequestMapper;

  private VsumSyncService service;

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

  @BeforeEach
  void setUp() {
    service =
        new VsumSyncService(
            vsumRepository,
            vsumMetaModelService,
            vsumMetaModelRepository,
            metaModelRelationService,
            metaModelRelationRepository,
            vsumHistoryService,
            vsumViewMetaModelService,
            vsumViewService,
            vsumViewRepository,
            vsumViewMetaModelRepository,
            fineGranularMetaModelRelationService,
            lowCodeReactionRequestMapper);

    lenient().when(metaModelRelationService.create(any(), any())).thenReturn(Map.of());
    lenient().when(metaModelRelationService.update(any(), any())).thenReturn(Map.of());
    lenient().when(vsumViewRepository.findAllByVsum(any(Vsum.class))).thenReturn(List.of());
    lenient()
        .when(vsumViewMetaModelRepository.findAllByVsumViewIn(anyList()))
        .thenReturn(List.of());
  }

  @Test
  void applySyncChanges_removesRelations_whenPairsMissingInDesired_andWritesHistory() {
    Vsum vsum = new Vsum();
    vsum.setId(1L);
    vsum.setMetaModelRelations(new java.util.HashSet<>());
    vsum.setVsumMetaModels(new java.util.HashSet<>());
    User owner = new User();
    String email = "u@ex.com";
    owner.setEmail(email);

    MetaModel a = clonedMetaModel(10L, 100L);
    MetaModel b = clonedMetaModel(20L, 200L);
    MetaModel c = clonedMetaModel(30L, 300L);
    MetaModel d = clonedMetaModel(40L, 400L);

    MetaModelRelation relAB = metaModelRelation(vsum, a, b, 999L);
    MetaModelRelation relCD = metaModelRelation(vsum, c, d);
    when(metaModelRelationRepository.findAllByVsum(vsum)).thenReturn(List.of(relAB, relCD));
    when(vsumMetaModelRepository.findAllByVsum(vsum)).thenReturn(List.of());

    VsumSyncChangesPutRequest put = new VsumSyncChangesPutRequest();
    put.setMetaModelRelationRequests(List.of(new MetaModelRelationRequest(100L, 200L, 999L)));

    Vsum result = service.applySyncChanges(vsum, owner, put, true);

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
  void applySyncChanges_createsRelations_whenNewPairsAppear_andWritesHistory() {
    Vsum vsum = new Vsum();
    vsum.setId(2L);
    vsum.setMetaModelRelations(new java.util.HashSet<>());
    vsum.setVsumMetaModels(new java.util.HashSet<>());
    User owner = new User();
    String email = "u@ex.com";
    owner.setEmail(email);

    when(metaModelRelationRepository.findAllByVsum(vsum)).thenReturn(List.of());
    when(vsumMetaModelRepository.findAllByVsum(vsum)).thenReturn(List.of());

    MetaModelRelationRequest req = new MetaModelRelationRequest(100L, 200L, 777L);
    VsumSyncChangesPutRequest put = new VsumSyncChangesPutRequest();
    put.setMetaModelRelationRequests(List.of(req));

    service.applySyncChanges(vsum, owner, put, true);

    verify(metaModelRelationService).create(vsum, List.of(req));
    verify(vsumHistoryService).create(vsum, owner);
    verify(vsumRepository).save(vsum);
  }

  @Test
  void applySyncChanges_removesVsumMetaModels_notInDesiredList_andWritesHistory() {
    Vsum vsum = new Vsum();
    vsum.setId(3L);
    vsum.setMetaModelRelations(new java.util.HashSet<>());
    vsum.setVsumMetaModels(new java.util.HashSet<>());
    User owner = new User();
    String email = "u@ex.com";
    owner.setEmail(email);

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

    Vsum result = service.applySyncChanges(vsum, owner, put, true);

    verify(vsumMetaModelService).delete(vsum, List.of(v20));
    verify(vsumHistoryService).create(vsum, owner);
    verify(vsumRepository).save(vsum);
    assertThat(result.getVsumMetaModels()).doesNotContain(v20);
  }

  @Test
  void applySyncChanges_addsVsumMetaModels_whenNewIdsAppear_andWritesHistory() {
    Vsum vsum = new Vsum();
    vsum.setId(4L);
    vsum.setMetaModelRelations(new java.util.HashSet<>());
    vsum.setVsumMetaModels(new java.util.HashSet<>());
    User owner = new User();
    String email = "u@ex.com";
    owner.setEmail(email);

    MetaModel mm11 = clonedMetaModel(1011L, 11L);
    MetaModel mm12 = clonedMetaModel(1012L, 12L);
    VsumMetaModel v11 = vsumMetaModel(vsum, mm11);
    VsumMetaModel v12 = vsumMetaModel(vsum, mm12);
    when(vsumMetaModelRepository.findAllByVsum(vsum)).thenReturn(List.of(v11, v12));
    when(metaModelRelationRepository.findAllByVsum(vsum)).thenReturn(List.of());

    VsumSyncChangesPutRequest put = new VsumSyncChangesPutRequest();
    put.setMetaModelIds(List.of(11L, 12L, 13L));

    service.applySyncChanges(vsum, owner, put, true);

    verify(vsumMetaModelService).create(vsum, Set.of(13L));
    verify(vsumHistoryService).create(vsum, owner);
    verify(vsumRepository).save(vsum);
  }

  @Test
  void applySyncChanges_mixedChanges_relationsAndMetaModels_andWritesHistoryOnce() {
    Vsum vsum = new Vsum();
    vsum.setId(5L);
    vsum.setMetaModelRelations(new java.util.HashSet<>());
    vsum.setVsumMetaModels(new java.util.HashSet<>());
    String email = "u@ex.com";
    User owner = new User();
    owner.setEmail(email);

    MetaModel m41 = clonedMetaModel(1041L, 41L);
    MetaModel m42 = clonedMetaModel(1042L, 42L);
    VsumMetaModel v41 = vsumMetaModel(vsum, m41);
    VsumMetaModel v42 = vsumMetaModel(vsum, m42);
    when(vsumMetaModelRepository.findAllByVsum(vsum)).thenReturn(List.of(v41, v42));

    MetaModel s10 = clonedMetaModel(10L, 10L);
    MetaModel t20 = clonedMetaModel(20L, 20L);
    MetaModel s30 = clonedMetaModel(30L, 30L);
    MetaModel t40 = clonedMetaModel(40L, 40L);
    MetaModelRelation r10And20 = metaModelRelation(vsum, s10, t20, 111L);
    MetaModelRelation r30And40 = metaModelRelation(vsum, s30, t40);
    when(metaModelRelationRepository.findAllByVsum(vsum)).thenReturn(List.of(r10And20, r30And40));

    MetaModelRelationRequest addRelationReq = new MetaModelRelationRequest(50L, 60L, 909L);

    VsumSyncChangesPutRequest put = new VsumSyncChangesPutRequest();
    put.setMetaModelRelationRequests(
        List.of(new MetaModelRelationRequest(10L, 20L, 111L), addRelationReq));
    put.setMetaModelIds(List.of(41L, 43L));

    service.applySyncChanges(vsum, owner, put, true);

    verify(vsumMetaModelService).delete(vsum, List.of(v42));
    verify(vsumMetaModelService).create(vsum, Set.of(43L));
    verify(metaModelRelationService).delete(List.of(r30And40));
    verify(metaModelRelationService).create(vsum, List.of(addRelationReq));
    verify(vsumHistoryService).create(vsum, owner);
    verify(vsumRepository).save(vsum);
  }

  @Test
  void applySyncChanges_noChanges_noSideEffects_exceptSave_andNoHistory() {
    Vsum vsum = new Vsum();
    vsum.setId(6L);
    vsum.setMetaModelRelations(new java.util.HashSet<>());
    vsum.setVsumMetaModels(new java.util.HashSet<>());
    User owner = new User();
    String email = "u@ex.com";
    owner.setEmail(email);

    MetaModel m1 = clonedMetaModel(101L, 1L);
    MetaModel m2 = clonedMetaModel(102L, 2L);
    VsumMetaModel v1 = vsumMetaModel(vsum, m1);
    VsumMetaModel v2 = vsumMetaModel(vsum, m2);
    when(vsumMetaModelRepository.findAllByVsum(vsum)).thenReturn(List.of(v1, v2));

    MetaModel s1 = clonedMetaModel(1L, 1L);
    MetaModel t2 = clonedMetaModel(2L, 2L);
    MetaModelRelation r12 = metaModelRelation(vsum, s1, t2, 555L);
    when(metaModelRelationRepository.findAllByVsum(vsum)).thenReturn(List.of(r12));

    VsumSyncChangesPutRequest put = new VsumSyncChangesPutRequest();
    put.setMetaModelIds(List.of(1L, 2L));
    put.setMetaModelRelationRequests(List.of(new MetaModelRelationRequest(1L, 2L, 555L)));

    service.applySyncChanges(vsum, owner, put, true);

    verify(vsumMetaModelService, never()).delete(any(), any());
    verify(vsumMetaModelService, never()).create(any(), any());
    verify(metaModelRelationService, never()).delete(any());
    verify(metaModelRelationService, never()).create(any(), any());
    verify(metaModelRelationService, never()).update(any(), any());
    verify(vsumHistoryService, never()).create(any(), any());
    verify(vsumRepository).save(vsum);
  }

  @Test
  void applySyncChanges_updatesRelation_whenSamePairHasDifferentReactionFile_andWritesHistory() {
    Vsum vsum = new Vsum();
    vsum.setId(61L);
    vsum.setMetaModelRelations(new java.util.HashSet<>());
    vsum.setVsumMetaModels(new java.util.HashSet<>());
    User owner = new User();
    String email = "u@ex.com";
    owner.setEmail(email);

    MetaModel s1 = clonedMetaModel(1L, 1L);
    MetaModel t2 = clonedMetaModel(2L, 2L);
    MetaModelRelation r12 = metaModelRelation(vsum, s1, t2, 555L);
    when(metaModelRelationRepository.findAllByVsum(vsum)).thenReturn(List.of(r12));
    when(vsumMetaModelRepository.findAllByVsum(vsum)).thenReturn(List.of());

    MetaModelRelationRequest updated = new MetaModelRelationRequest(1L, 2L, 777L);
    VsumSyncChangesPutRequest put = new VsumSyncChangesPutRequest();
    put.setMetaModelRelationRequests(List.of(updated));

    when(metaModelRelationService.update(eq(vsum), any())).thenReturn(Map.of(updated, r12));

    service.applySyncChanges(vsum, owner, put, true);

    verify(metaModelRelationService, never()).delete(any());
    verify(metaModelRelationService, never()).create(any(), any());
    verify(metaModelRelationService).update(eq(vsum), eq(Map.of(updated, r12)));
    verify(vsumHistoryService).create(vsum, owner);
    verify(fineGranularMetaModelRelationService).update(eq(email), eq(Map.of(updated, r12)), any());
  }

  @Test
  void applySyncChanges_syncsFineGranularAndViewsTogether_andWritesHistoryOnce() {
    Vsum vsum = new Vsum();
    vsum.setId(80L);
    vsum.setMetaModelRelations(new java.util.HashSet<>());
    vsum.setVsumMetaModels(new java.util.HashSet<>());
    User owner = new User();
    String email = "u@ex.com";
    owner.setEmail(email);

    MetaModel s1 = clonedMetaModel(1L, 1L);
    MetaModel t2 = clonedMetaModel(2L, 2L);
    MetaModelRelation r12 = metaModelRelation(vsum, s1, t2, 555L);
    when(metaModelRelationRepository.findAllByVsum(vsum)).thenReturn(List.of(r12));
    when(vsumMetaModelRepository.findAllByVsum(vsum)).thenReturn(List.of());
    when(vsumViewRepository.findAllByVsum(vsum)).thenReturn(List.of());

    FineGranularMetaModelRelationRequest fgReq =
        new FineGranularMetaModelRelationRequest(null, "Component", "Class", 14L, null);
    MetaModelRelationRequest updated = new MetaModelRelationRequest(1L, 2L, 555L);
    updated.getFineGranularMetaModelRelationSet().add(fgReq);

    VsumView createdView = new VsumView();
    createdView.setId(991L);
    when(vsumViewService.create(vsum, 90L)).thenReturn(createdView);
    when(metaModelRelationService.update(eq(vsum), any())).thenReturn(Map.of(updated, r12));

    VsumSyncChangesPutRequest put = new VsumSyncChangesPutRequest();
    put.setMetaModelRelationRequests(List.of(updated));
    put.setViewRequests(
        List.of(ViewRequest.builder().fileStorageId(90L).metaModelIds(List.of(1L, 2L)).build()));

    service.applySyncChanges(vsum, owner, put, true);

    verify(metaModelRelationService).update(eq(vsum), eq(Map.of(updated, r12)));
    verify(fineGranularMetaModelRelationService).update(eq(email), eq(Map.of(updated, r12)), any());
    verify(vsumViewService).create(vsum, 90L);
    verify(vsumHistoryService, times(1)).create(vsum, owner);
    verify(vsumRepository).save(vsum);
  }

  @Test
  void applySyncChanges_clearsAll_whenRequestListsAreNull_orEmpty_andWritesHistory() {
    Vsum vsum = new Vsum();
    vsum.setId(7L);
    vsum.setMetaModelRelations(new java.util.HashSet<>());
    vsum.setVsumMetaModels(new java.util.HashSet<>());
    User owner = new User();
    String email = "u@ex.com";
    owner.setEmail(email);

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

    service.applySyncChanges(vsum, owner, put, true);

    verify(vsumMetaModelService)
        .delete(eq(vsum), argThat(list -> list.size() == 2 && list.containsAll(List.of(v10, v20))));
    verify(metaModelRelationService).delete(List.of(r));
    verify(vsumHistoryService).create(vsum, owner);
    verify(vsumRepository).save(vsum);
  }

  @Test
  void applySyncChanges_createsViews_whenNewViewAppears_andWritesHistory() {
    Vsum vsum = new Vsum();
    vsum.setId(70L);
    vsum.setMetaModelRelations(new java.util.HashSet<>());
    vsum.setVsumMetaModels(new java.util.HashSet<>());

    User owner = new User();
    String email = "u@ex.com";
    owner.setEmail(email);

    when(metaModelRelationRepository.findAllByVsum(vsum)).thenReturn(List.of());
    when(vsumMetaModelRepository.findAllByVsum(vsum)).thenReturn(List.of());
    when(vsumViewRepository.findAllByVsum(vsum)).thenReturn(List.of());

    VsumView createdView = new VsumView();
    createdView.setId(991L);
    when(vsumViewService.create(vsum, 90L)).thenReturn(createdView);

    VsumSyncChangesPutRequest put = new VsumSyncChangesPutRequest();
    put.setViewRequests(
        List.of(
            ViewRequest.builder()
                .fileStorageId(90L)
                .metaModelIds(List.of(3L, 1L, 3L, 2L))
                .build()));

    service.applySyncChanges(vsum, owner, put, true);

    verify(vsumViewService).create(vsum, 90L);
    verify(vsumViewMetaModelService)
        .create(
            argThat(view -> view != null && Long.valueOf(991L).equals(view.getId())),
            argThat(ids -> ids.equals(Set.of(1L, 2L, 3L))));
    verify(vsumHistoryService).create(vsum, owner);
    verify(vsumRepository).save(vsum);
  }

  @Test
  void applySyncChanges_deletesViews_whenExistingViewIsRemoved_andWritesHistory() {
    Vsum vsum = new Vsum();
    vsum.setId(71L);
    vsum.setMetaModelRelations(new java.util.HashSet<>());
    vsum.setVsumMetaModels(new java.util.HashSet<>());

    User owner = new User();
    String email = "u@ex.com";
    owner.setEmail(email);

    when(metaModelRelationRepository.findAllByVsum(vsum)).thenReturn(List.of());
    when(vsumMetaModelRepository.findAllByVsum(vsum)).thenReturn(List.of());

    VsumView existingView = new VsumView();
    existingView.setId(10L);
    FileStorage viewFile = new FileStorage();
    viewFile.setId(50L);
    existingView.setFileStorage(viewFile);

    when(vsumViewRepository.findAllByVsum(vsum)).thenReturn(List.of(existingView));

    VsumViewMetaModel vm1 = new VsumViewMetaModel();
    vm1.setMetaModel(clonedMetaModel(1001L, 1L));
    vm1.setVsumView(existingView);
    VsumViewMetaModel vm2 = new VsumViewMetaModel();
    vm2.setMetaModel(clonedMetaModel(1002L, 2L));
    vm2.setVsumView(existingView);
    when(vsumViewMetaModelRepository.findAllByVsumViewIn(List.of(existingView)))
        .thenReturn(List.of(vm1, vm2));

    VsumSyncChangesPutRequest put = new VsumSyncChangesPutRequest();
    put.setViewRequests(List.of());

    service.applySyncChanges(vsum, owner, put, true);

    verify(vsumViewMetaModelService).deleteByVsumView(existingView);
    verify(vsumViewService).delete(vsum, existingView);
    verify(vsumHistoryService).create(vsum, owner);
    verify(vsumRepository).save(vsum);
  }

  @Test
  void applySyncChanges_doesNotTouchViews_whenDesiredMatchesExistingAfterNormalization() {
    Vsum vsum = new Vsum();
    vsum.setId(72L);
    vsum.setMetaModelRelations(new java.util.HashSet<>());
    vsum.setVsumMetaModels(new java.util.HashSet<>());

    User owner = new User();
    String email = "u@ex.com";
    owner.setEmail(email);

    when(metaModelRelationRepository.findAllByVsum(vsum)).thenReturn(List.of());
    when(vsumMetaModelRepository.findAllByVsum(vsum)).thenReturn(List.of());

    VsumView existingView = new VsumView();
    existingView.setId(11L);
    FileStorage viewFile = new FileStorage();
    viewFile.setId(60L);
    existingView.setFileStorage(viewFile);
    when(vsumViewRepository.findAllByVsum(vsum)).thenReturn(List.of(existingView));

    VsumViewMetaModel vm1 = new VsumViewMetaModel();
    vm1.setMetaModel(clonedMetaModel(2001L, 1L));
    vm1.setVsumView(existingView);
    VsumViewMetaModel vm2 = new VsumViewMetaModel();
    vm2.setMetaModel(clonedMetaModel(2002L, 2L));
    vm2.setVsumView(existingView);
    when(vsumViewMetaModelRepository.findAllByVsumViewIn(List.of(existingView)))
        .thenReturn(List.of(vm1, vm2));

    VsumSyncChangesPutRequest put = new VsumSyncChangesPutRequest();
    put.setViewRequests(
        List.of(
            ViewRequest.builder()
                .fileStorageId(60L)
                .metaModelIds(Arrays.asList(2L, 1L, 2L, null))
                .build()));

    service.applySyncChanges(vsum, owner, put, true);

    verify(vsumViewService, never()).create(any(), anyLong());
    verify(vsumViewService, never()).delete(any(), any());
    verify(vsumViewMetaModelService, never()).create(any(), any());
    verify(vsumViewMetaModelService, never()).deleteByVsumView(any());
    verify(vsumHistoryService, never()).create(any(), any());
    verify(vsumRepository).save(vsum);
  }

  @Test
  void applySyncChanges_ignoresInvalidViewRequests_andCreatesOnlyValidOnes() {
    Vsum vsum = new Vsum();
    vsum.setId(73L);
    vsum.setMetaModelRelations(new java.util.HashSet<>());
    vsum.setVsumMetaModels(new java.util.HashSet<>());

    User owner = new User();
    String email = "u@ex.com";
    owner.setEmail(email);

    when(metaModelRelationRepository.findAllByVsum(vsum)).thenReturn(List.of());
    when(vsumMetaModelRepository.findAllByVsum(vsum)).thenReturn(List.of());
    when(vsumViewRepository.findAllByVsum(vsum)).thenReturn(List.of());

    VsumView createdView = new VsumView();
    createdView.setId(992L);
    when(vsumViewService.create(vsum, 72L)).thenReturn(createdView);

    VsumSyncChangesPutRequest put = new VsumSyncChangesPutRequest();
    put.setViewRequests(
        Arrays.asList(
            null,
            ViewRequest.builder().fileStorageId(null).metaModelIds(List.of(1L, 2L)).build(),
            ViewRequest.builder().fileStorageId(71L).metaModelIds(null).build(),
            ViewRequest.builder()
                .fileStorageId(71L)
                .metaModelIds(Collections.singletonList(null))
                .build(),
            ViewRequest.builder()
                .fileStorageId(72L)
                .metaModelIds(Arrays.asList(5L, null, 4L, 5L))
                .build()));

    service.applySyncChanges(vsum, owner, put, true);

    verify(vsumViewService).create(vsum, 72L);
    verify(vsumViewMetaModelService)
        .create(
            argThat(view -> view != null && Long.valueOf(992L).equals(view.getId())),
            argThat(ids -> ids.equals(Set.of(4L, 5L))));
    verify(vsumHistoryService).create(vsum, owner);
    verify(vsumRepository).save(vsum);
  }

  @Test
  void applySyncChanges_doesNotWriteHistory_whenHistoryIsDisabled() {
    Vsum vsum = new Vsum();
    vsum.setId(74L);
    vsum.setMetaModelRelations(new java.util.HashSet<>());
    vsum.setVsumMetaModels(new java.util.HashSet<>());
    User owner = new User();
    owner.setEmail("u@ex.com");

    when(metaModelRelationRepository.findAllByVsum(vsum)).thenReturn(List.of());
    when(vsumMetaModelRepository.findAllByVsum(vsum)).thenReturn(List.of());

    VsumSyncChangesPutRequest put = new VsumSyncChangesPutRequest();
    put.setMetaModelIds(List.of(13L));

    service.applySyncChanges(vsum, owner, put, false);

    verify(vsumMetaModelService).create(vsum, Set.of(13L));
    verifyNoInteractions(vsumHistoryService);
    verify(vsumRepository).save(vsum);
  }
}
