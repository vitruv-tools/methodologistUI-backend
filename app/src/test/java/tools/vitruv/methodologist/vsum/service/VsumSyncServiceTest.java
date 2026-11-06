package tools.vitruv.methodologist.vsum.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.BadRequestException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
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

  private static FileStorage neoJoinFile(long id, String filename) {
    FileStorage file = new FileStorage();
    file.setId(id);
    file.setFilename(filename);
    return file;
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
    when(vsumMetaModelRepository.findAllByVsum(vsum))
        .thenReturn(List.of(vsumMetaModel(vsum, s1), vsumMetaModel(vsum, t2)));
    when(vsumViewRepository.findAllByVsum(vsum)).thenReturn(List.of());

    FineGranularMetaModelRelationRequest fgReq =
        new FineGranularMetaModelRelationRequest(null, "Component", "Class", 14L, null);
    MetaModelRelationRequest updated = new MetaModelRelationRequest(1L, 2L, 555L);
    updated.getFineGranularMetaModelRelationSet().add(fgReq);

    VsumView createdView = new VsumView();
    createdView.setId(991L);
    when(vsumViewService.findNeoJoinFile(90L)).thenReturn(neoJoinFile(90L, "Brake.nj"));
    when(vsumViewService.create(vsum, 90L, "Brake", null)).thenReturn(createdView);
    when(metaModelRelationService.update(eq(vsum), any())).thenReturn(Map.of(updated, r12));

    VsumSyncChangesPutRequest put = new VsumSyncChangesPutRequest();
    put.setMetaModelIds(List.of(1L, 2L));
    put.setMetaModelRelationRequests(List.of(updated));
    put.setViewRequests(
        List.of(ViewRequest.builder().fileStorageId(90L).metaModelIds(List.of(1L, 2L)).build()));

    service.applySyncChanges(vsum, owner, put, true);

    verify(metaModelRelationService).update(eq(vsum), eq(Map.of(updated, r12)));
    verify(fineGranularMetaModelRelationService).update(eq(email), eq(Map.of(updated, r12)), any());
    verify(vsumViewService).create(vsum, 90L, "Brake", null);
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
    when(vsumViewService.findNeoJoinFile(90L)).thenReturn(neoJoinFile(90L, "Brake.nj"));
    when(vsumViewService.create(vsum, 90L, "Brake", null)).thenReturn(createdView);

    VsumSyncChangesPutRequest put = new VsumSyncChangesPutRequest();
    put.setMetaModelIds(List.of(1L, 2L, 3L));
    put.setViewRequests(
        List.of(
            ViewRequest.builder()
                .fileStorageId(90L)
                .metaModelIds(List.of(3L, 1L, 3L, 2L))
                .build()));

    service.applySyncChanges(vsum, owner, put, true);

    verify(vsumViewService).create(vsum, 90L, "Brake", null);
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
    when(vsumMetaModelRepository.findAllByVsum(vsum))
        .thenReturn(
            List.of(
                vsumMetaModel(vsum, clonedMetaModel(2001L, 1L)),
                vsumMetaModel(vsum, clonedMetaModel(2002L, 2L))));

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
    put.setMetaModelIds(List.of(1L, 2L));
    put.setViewRequests(
        List.of(
            ViewRequest.builder()
                .fileStorageId(60L)
                .metaModelIds(Arrays.asList(2L, 1L, 2L, null))
                .build()));

    service.applySyncChanges(vsum, owner, put, true);

    verify(vsumViewService, never()).create(any(), any(), any(), any());
    verify(vsumViewService, never()).update(any(), any(), any(), any());
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
    when(vsumViewService.findNeoJoinFile(72L)).thenReturn(neoJoinFile(72L, "Brake.nj"));
    when(vsumViewService.create(vsum, 72L, "Brake", null)).thenReturn(createdView);

    VsumSyncChangesPutRequest put = new VsumSyncChangesPutRequest();
    put.setMetaModelIds(List.of(4L, 5L));
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

    verify(vsumViewService).create(vsum, 72L, "Brake", null);
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

  @Test
  void applySyncChanges_renamesViewInPlace_whenRequestHasItsId() {
    Vsum vsum = vsumWithMetaModels(100L);
    VsumView brake = view(10L, "Brake", 50L);
    VsumViewMetaModel one = link(brake, 1001L, 1L);
    VsumViewMetaModel two = link(brake, 1002L, 2L);
    givenViews(vsum, List.of(brake), List.of(one, two));

    syncViews(vsum, request(10L, "Braking", 50L, 2L, 1L));

    verify(vsumViewService).update(brake, 50L, "Braking", null);
    verify(vsumViewMetaModelService, never()).delete(anyList());
    verify(vsumViewMetaModelService, never()).create(any(), any());
    verify(vsumViewService, never()).create(any(), any(), any(), any());
    verify(vsumViewService, never()).delete(any(), any());
    verify(vsumHistoryService).create(vsum, OWNER);
  }

  @Test
  void applySyncChanges_changesFileDescriptionAndMetaModelsOfView_inPlace() {
    Vsum vsum = vsumWithMetaModels(101L);
    VsumView brake = view(10L, "Brake", 50L);
    VsumViewMetaModel one = link(brake, 1001L, 1L);
    givenViews(vsum, List.of(brake), List.of(one));

    ViewRequest edited = request(10L, "Brake", 51L, 3L, 2L);
    edited.setDescription("brake and wheel");
    syncViews(vsum, edited);

    verify(vsumViewService).update(brake, 51L, "Brake", "brake and wheel");
    verify(vsumViewMetaModelService).delete(List.of(one));
    verify(vsumViewMetaModelService).create(brake, Set.of(2L, 3L));
    verify(vsumViewService, never()).create(any(), any(), any(), any());
    verify(vsumViewService, never()).delete(any(), any());
    verify(vsumHistoryService).create(vsum, OWNER);
  }

  @Test
  void applySyncChanges_matchesViewByName_whenRequestHasNoId() {
    Vsum vsum = vsumWithMetaModels(102L);
    VsumView brake = view(10L, "Brake", 50L);
    givenViews(vsum, List.of(brake), List.of(link(brake, 1001L, 1L)));

    syncViews(vsum, request(null, "Brake", 51L, 1L));

    verify(vsumViewService).update(brake, 51L, "Brake", null);
    verify(vsumViewService, never()).create(any(), any(), any(), any());
    verify(vsumViewService, never()).delete(any(), any());
  }

  @Test
  void applySyncChanges_createsView_whenIdIsNotAViewOfTheVsum_andDeletesUnmatchedViews() {
    Vsum vsum = vsumWithMetaModels(103L);
    VsumView brake = view(10L, "Brake", 50L);
    givenViews(vsum, List.of(brake), List.of(link(brake, 1001L, 1L)));
    VsumView engine = view(11L, "Engine", 60L);
    when(vsumViewService.create(vsum, 60L, "Engine", null)).thenReturn(engine);

    syncViews(vsum, request(999L, "Engine", 60L, 1L));

    verify(vsumViewService).create(vsum, 60L, "Engine", null);
    verify(vsumViewMetaModelService).create(engine, Set.of(1L));
    verify(vsumViewMetaModelService).deleteByVsumView(brake);
    verify(vsumViewService).delete(vsum, brake);
    verify(vsumViewService, never()).update(any(), any(), any(), any());
  }

  @Test
  void applySyncChanges_matchesAllIdsBeforeNames() {
    Vsum vsum = vsumWithMetaModels(104L);
    VsumView a = view(5L, "A", 50L);
    VsumView b = view(6L, "B", 51L);
    givenViews(vsum, List.of(a, b), List.of(link(a, 1001L, 1L), link(b, 1001L, 1L)));
    VsumView created = view(7L, "A", 52L);
    when(vsumViewService.create(vsum, 52L, "A", null)).thenReturn(created);

    syncViews(vsum, request(null, "A", 52L, 1L), request(5L, "B", 50L, 1L));

    verify(vsumViewService).update(a, 50L, "B", null);
    verify(vsumViewService).create(vsum, 52L, "A", null);
    verify(vsumViewService).delete(vsum, b);
  }

  @Test
  void applySyncChanges_swapsTheNamesOfTwoViews() {
    Vsum vsum = vsumWithMetaModels(105L);
    VsumView a = view(5L, "A", 50L);
    VsumView b = view(6L, "B", 51L);
    givenViews(vsum, List.of(a, b), List.of(link(a, 1001L, 1L), link(b, 1001L, 1L)));

    syncViews(vsum, request(5L, "B", 50L, 1L), request(6L, "A", 51L, 1L));

    verify(vsumViewService).update(a, 50L, "B", null);
    verify(vsumViewService).update(b, 51L, "A", null);
    verify(vsumViewService, never()).create(any(), any(), any(), any());
    verify(vsumViewService, never()).delete(any(), any());
  }

  @Test
  void applySyncChanges_rejectsTwoViewsWithTheSameName_beforeChangingAnything() {
    Vsum vsum = vsumWithMetaModels(106L);

    assertThatThrownBy(
            () -> syncViews(vsum, request(null, "A", 50L, 1L), request(null, " A ", 51L, 2L)))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("A");

    verifyNoInteractions(vsumHistoryService, vsumViewMetaModelService);
    verify(vsumViewService, never()).create(any(), any(), any(), any());
    verify(vsumRepository, never()).save(any());
  }

  @Test
  void applySyncChanges_rejectsTheSameViewIdTwice() {
    Vsum vsum = vsumWithMetaModels(107L);
    VsumView a = view(5L, "A", 50L);
    givenViews(vsum, List.of(a), List.of(link(a, 1001L, 1L)));

    assertThatThrownBy(() -> syncViews(vsum, request(5L, "A", 50L, 1L), request(5L, "A2", 50L, 1L)))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("5");

    verifyNoInteractions(vsumHistoryService);
    verify(vsumViewService, never()).update(any(), any(), any(), any());
  }

  @Test
  void applySyncChanges_rejectsNewName_thatAViewKeepingItsNameHas() {
    Vsum vsum = vsumWithMetaModels(108L);
    VsumView a = view(5L, "A", 50L);
    givenViews(vsum, List.of(a), List.of(link(a, 1001L, 1L)));

    assertThatThrownBy(
            () -> syncViews(vsum, request(5L, null, 50L, 1L), request(null, "A", 51L, 2L)))
        .isInstanceOf(BadRequestException.class);

    verifyNoInteractions(vsumHistoryService);
  }

  @Test
  void applySyncChanges_rejectsNamesLongerThan255Characters_andCountsCodePoints() {
    Vsum vsum = vsumWithMetaModels(109L);
    String longest = new String(Character.toChars(0x1F600)).repeat(255);
    VsumView created = view(7L, longest, 50L);
    when(vsumViewService.create(vsum, 50L, longest, null)).thenReturn(created);

    syncViews(vsum, request(null, longest, 50L, 1L));
    verify(vsumViewService).create(vsum, 50L, longest, null);

    assertThatThrownBy(() -> syncViews(vsum, request(null, "x".repeat(256), 51L, 1L)))
        .isInstanceOf(BadRequestException.class);
  }

  @Test
  void applySyncChanges_rejectsViewWithNameOrId_butWithoutFile() {
    Vsum vsum = vsumWithMetaModels(114L);
    ViewRequest withoutFile = ViewRequest.builder().id(10L).metaModelIds(List.of(1L)).build();

    assertThatThrownBy(() -> syncViews(vsum, withoutFile))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("10");

    verify(vsumViewService, never()).delete(any(), any());
    verifyNoInteractions(vsumHistoryService);
  }

  @Test
  void applySyncChanges_keepsViewWithIdButWithoutMetaModels_andRemovesItsLinks() {
    Vsum vsum = vsumWithMetaModels(115L);
    VsumView brake = view(10L, "Brake", 50L);
    VsumViewMetaModel one = link(brake, 1001L, 1L);
    givenViews(vsum, List.of(brake), List.of(one));

    syncViews(vsum, request(10L, "Brake", 50L));

    verify(vsumViewService, never()).delete(any(), any());
    verify(vsumViewMetaModelService).delete(List.of(one));
    verify(vsumViewMetaModelService, never()).create(any(), any());
  }

  @Test
  void applySyncChanges_namesNewViewsWithoutNameAfterTheirFile_andNumbersTakenNames() {
    Vsum vsum = vsumWithMetaModels(110L);
    VsumView brake = view(5L, "Brake", 50L);
    givenViews(vsum, List.of(brake), List.of(link(brake, 1001L, 1L)));
    when(vsumViewService.findNeoJoinFile(90L)).thenReturn(neoJoinFile(90L, "Brake.nj"));
    when(vsumViewService.findNeoJoinFile(91L)).thenReturn(neoJoinFile(91L, "Brake.nj"));
    when(vsumViewService.findNeoJoinFile(92L)).thenReturn(neoJoinFile(92L, ".nj"));
    when(vsumViewService.create(eq(vsum), anyLong(), any(), any())).thenReturn(new VsumView());

    syncViews(
        vsum,
        request(5L, null, 50L, 1L),
        request(null, null, 90L, 1L),
        request(null, null, 91L, 2L),
        request(null, null, 92L, 2L));

    verify(vsumViewService, never()).update(any(), any(), any(), any());
    verify(vsumViewService).create(vsum, 90L, "Brake (2)", null);
    verify(vsumViewService).create(vsum, 91L, "Brake (3)", null);
    verify(vsumViewService).create(vsum, 92L, "view", null);
  }

  @Test
  void applySyncChanges_recreatesView_whenRequestWithoutNameHasOtherMetaModels() {
    Vsum vsum = vsumWithMetaModels(111L);
    VsumView brake = view(10L, "Brake", 50L);
    givenViews(vsum, List.of(brake), List.of(link(brake, 1001L, 1L), link(brake, 1002L, 2L)));
    when(vsumViewService.findNeoJoinFile(50L)).thenReturn(neoJoinFile(50L, "Brake.nj"));
    VsumView created = view(11L, "Brake", 50L);
    when(vsumViewService.create(vsum, 50L, "Brake", null)).thenReturn(created);

    syncViews(vsum, request(null, null, 50L, 1L));

    verify(vsumViewService).delete(vsum, brake);
    verify(vsumViewService).create(vsum, 50L, "Brake", null);
    verify(vsumViewMetaModelService).create(created, Set.of(1L));
  }

  @Test
  void applySyncChanges_removesDescription_whenRequestHasBlankOne() {
    Vsum vsum = vsumWithMetaModels(112L);
    VsumView brake = view(10L, "Brake", 50L);
    brake.setDescription("old");
    givenViews(vsum, List.of(brake), List.of(link(brake, 1001L, 1L)));

    ViewRequest cleared = request(10L, "Brake", 50L, 1L);
    cleared.setDescription("  ");
    syncViews(vsum, cleared);

    verify(vsumViewService).update(brake, 50L, "Brake", null);
    verify(vsumHistoryService).create(vsum, OWNER);
  }

  @Test
  void applySyncChanges_keepsDescription_whenRequestHasNone() {
    Vsum vsum = vsumWithMetaModels(116L);
    VsumView brake = view(10L, "Brake", 50L);
    brake.setDescription("brake components");
    givenViews(vsum, List.of(brake), List.of(link(brake, 1001L, 1L)));

    syncViews(vsum, request(10L, "Braking", 50L, 1L));

    verify(vsumViewService).update(brake, 50L, "Braking", "brake components");
  }

  @Test
  void applySyncChanges_doesNotTouchView_whenRequestDescribesItAsItIs() {
    Vsum vsum = vsumWithMetaModels(113L);
    VsumView brake = view(10L, "Brake", 50L);
    brake.setDescription("brake components");
    givenViews(vsum, List.of(brake), List.of(link(brake, 1001L, 1L), link(brake, 1002L, 2L)));

    ViewRequest same = request(10L, " Brake ", 50L, 2L, 1L, 2L, 99L);
    same.setDescription("brake components");
    syncViews(vsum, same);

    verify(vsumViewService, never()).update(any(), any(), any(), any());
    verifyNoInteractions(vsumViewMetaModelService, vsumHistoryService);
    verify(vsumRepository).save(vsum);
  }

  @Test
  void applySyncChanges_removesViewLinks_beforeTheMetaModelsTheyReference() {
    Vsum vsum = vsumWithMetaModels(117L);
    VsumView brake = view(10L, "Brake", 50L);
    VsumView wheel = view(11L, "Wheel", 51L);
    VsumViewMetaModel brakeToOne = link(brake, 1001L, 1L);
    VsumViewMetaModel brakeToTwo = link(brake, 1002L, 2L);
    givenViews(
        vsum, List.of(brake, wheel), List.of(brakeToOne, brakeToTwo, link(wheel, 1001L, 1L)));

    VsumSyncChangesPutRequest put = new VsumSyncChangesPutRequest();
    put.setMetaModelIds(List.of(2L, 3L));
    put.setViewRequests(List.of(request(10L, "Brake", 50L, 1L, 2L)));
    service.applySyncChanges(vsum, OWNER, put, true);

    InOrder order = inOrder(vsumViewMetaModelService, vsumViewService, vsumMetaModelService);
    order.verify(vsumViewMetaModelService).deleteByVsumView(wheel);
    order.verify(vsumViewService).delete(vsum, wheel);
    order.verify(vsumViewMetaModelService).delete(List.of(brakeToOne));
    order
        .verify(vsumMetaModelService)
        .delete(eq(vsum), argThat(list -> list.size() == 1 && sourceIdOf(list.get(0)) == 1L));
    verify(vsumViewMetaModelService, never()).create(any(), any());
  }

  @Test
  void applySyncChanges_linksViewsToNewMetaModels_afterAddingThemToTheVsum() {
    Vsum vsum = vsumWithMetaModels(118L);
    VsumView brake = view(10L, "Brake", 50L);
    givenViews(vsum, List.of(brake), List.of(link(brake, 1001L, 1L)));

    VsumSyncChangesPutRequest put = new VsumSyncChangesPutRequest();
    put.setMetaModelIds(List.of(1L, 2L, 3L, 4L));
    put.setViewRequests(List.of(request(10L, "Brake", 50L, 1L, 4L)));
    service.applySyncChanges(vsum, OWNER, put, true);

    InOrder order = inOrder(vsumMetaModelService, vsumViewMetaModelService);
    order.verify(vsumMetaModelService).create(vsum, Set.of(4L));
    order.verify(vsumViewMetaModelService).create(brake, Set.of(4L));
  }

  @Test
  void applySyncChanges_createsViewWithItsDescription_andWithoutABlankOne() {
    Vsum vsum = vsumWithMetaModels(119L);
    when(vsumViewService.create(eq(vsum), anyLong(), any(), any())).thenReturn(new VsumView());
    ViewRequest engine = request(null, "Engine", 60L, 1L);
    engine.setDescription(" engine parts ");
    ViewRequest wheel = request(null, "Wheel", 61L, 1L);
    wheel.setDescription("  ");

    syncViews(vsum, engine, wheel);

    verify(vsumViewService).create(vsum, 60L, "Engine", "engine parts");
    verify(vsumViewService).create(vsum, 61L, "Wheel", null);
  }

  @Test
  void applySyncChanges_rejectsViewWithNameButWithoutFile() {
    Vsum vsum = vsumWithMetaModels(120L);
    ViewRequest withoutFile = ViewRequest.builder().name("Brake").metaModelIds(List.of(1L)).build();

    assertThatThrownBy(() -> syncViews(vsum, withoutFile))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("Brake");

    verify(vsumViewService, never()).delete(any(), any());
    verifyNoInteractions(vsumHistoryService);
  }

  @Test
  void applySyncChanges_matchesViewByName_whenIdIsNotAViewOfTheVsum() {
    Vsum vsum = vsumWithMetaModels(121L);
    VsumView brake = view(9L, "Brake", 50L);
    givenViews(vsum, List.of(brake), List.of(link(brake, 1001L, 1L)));

    syncViews(vsum, request(7L, "Brake", 51L, 1L));

    verify(vsumViewService).update(brake, 51L, "Brake", null);
    verify(vsumViewService, never()).create(any(), any(), any(), any());
    verify(vsumViewService, never()).delete(any(), any());
  }

  @Test
  void applySyncChanges_doesNotMatchNamedRequest_byFileAndMetaModels() {
    Vsum vsum = vsumWithMetaModels(122L);
    VsumView brake = view(10L, "Brake", 50L);
    givenViews(vsum, List.of(brake), List.of(link(brake, 1001L, 1L)));
    when(vsumViewService.create(vsum, 50L, "Engine", null)).thenReturn(view(11L, "Engine", 50L));

    syncViews(vsum, request(null, "Engine", 50L, 1L));

    verify(vsumViewService).create(vsum, 50L, "Engine", null);
    verify(vsumViewService).delete(vsum, brake);
    verify(vsumViewService, never()).update(any(), any(), any(), any());
  }

  @Test
  void applySyncChanges_reservesRequestedNames_beforeNamingNewViewsAfterTheirFiles() {
    Vsum vsum = vsumWithMetaModels(123L);
    when(vsumViewService.findNeoJoinFile(90L)).thenReturn(neoJoinFile(90L, "Brake.nj"));
    when(vsumViewService.create(eq(vsum), anyLong(), any(), any())).thenReturn(new VsumView());

    syncViews(vsum, request(null, null, 90L, 1L), request(null, "Brake", 51L, 2L));

    verify(vsumViewService).create(vsum, 90L, "Brake (2)", null);
    verify(vsumViewService).create(vsum, 51L, "Brake", null);
  }

  @Test
  void applySyncChanges_shortensFileBasedNames_thatTheNumberMakesTooLong() {
    Vsum vsum = vsumWithMetaModels(124L);
    String smiley = new String(Character.toChars(0x1F600));
    String base = smiley.repeat(252);
    when(vsumViewService.findNeoJoinFile(90L)).thenReturn(neoJoinFile(90L, base + ".nj"));
    when(vsumViewService.findNeoJoinFile(91L)).thenReturn(neoJoinFile(91L, base + ".nj"));
    when(vsumViewService.create(eq(vsum), anyLong(), any(), any())).thenReturn(new VsumView());

    syncViews(vsum, request(null, null, 90L, 1L), request(null, null, 91L, 1L));

    verify(vsumViewService).create(vsum, 90L, base, null);
    verify(vsumViewService).create(vsum, 91L, smiley.repeat(251) + " (2)", null);
  }

  @Test
  void applySyncChanges_keepsViewOfRequestWithoutName_whenTheVsumDropsOneOfItsMetaModels() {
    Vsum vsum = vsumWithMetaModels(125L);
    VsumView brake = view(10L, "Brake", 50L);
    brake.setDescription("brake components");
    VsumViewMetaModel one = link(brake, 1001L, 1L);
    VsumViewMetaModel two = link(brake, 1002L, 2L);
    givenViews(vsum, List.of(brake), List.of(one, two));

    VsumSyncChangesPutRequest put = new VsumSyncChangesPutRequest();
    put.setMetaModelIds(List.of(1L, 3L));
    put.setViewRequests(List.of(request(null, null, 50L, 1L, 2L)));
    service.applySyncChanges(vsum, OWNER, put, true);

    verify(vsumViewMetaModelService).delete(List.of(two));
    verify(vsumViewService).update(brake, 50L, "Brake", "brake components");
    verify(vsumViewService, never()).delete(any(), any());
    verify(vsumViewService, never()).create(any(), any(), any(), any());
  }

  private static final User OWNER = User.builder().email("u@ex.com").build();

  /** A VSUM with the meta models 1, 2 and 3, which {@link #syncViews} keeps. */
  private Vsum vsumWithMetaModels(long id) {
    Vsum vsum = new Vsum();
    vsum.setId(id);
    vsum.setMetaModelRelations(new HashSet<>());
    vsum.setVsumMetaModels(new HashSet<>());
    when(vsumMetaModelRepository.findAllByVsum(vsum))
        .thenReturn(
            List.of(
                vsumMetaModel(vsum, clonedMetaModel(1001L, 1L)),
                vsumMetaModel(vsum, clonedMetaModel(1002L, 2L)),
                vsumMetaModel(vsum, clonedMetaModel(1003L, 3L))));
    return vsum;
  }

  private static long sourceIdOf(VsumMetaModel vsumMetaModel) {
    return vsumMetaModel.getMetaModel().getSource().getId();
  }

  private static VsumView view(long id, String name, long fileStorageId) {
    VsumView view = new VsumView();
    view.setId(id);
    view.setName(name);
    view.setFileStorage(neoJoinFile(fileStorageId, name + ".nj"));
    return view;
  }

  private VsumViewMetaModel link(VsumView view, long metaModelId, long sourceId) {
    VsumViewMetaModel link = new VsumViewMetaModel();
    link.setVsumView(view);
    link.setMetaModel(clonedMetaModel(metaModelId, sourceId));
    return link;
  }

  private static ViewRequest request(
      Long id, String name, long fileStorageId, Long... metaModelIds) {
    return ViewRequest.builder()
        .id(id)
        .name(name)
        .fileStorageId(fileStorageId)
        .metaModelIds(List.of(metaModelIds))
        .build();
  }

  private void givenViews(Vsum vsum, List<VsumView> views, List<VsumViewMetaModel> links) {
    when(vsumViewRepository.findAllByVsum(vsum)).thenReturn(views);
    when(vsumViewMetaModelRepository.findAllByVsumViewIn(views)).thenReturn(links);
  }

  /** Syncs the views of a VSUM made by {@link #vsumWithMetaModels}, keeping its meta models. */
  private void syncViews(Vsum vsum, ViewRequest... requests) {
    VsumSyncChangesPutRequest put = new VsumSyncChangesPutRequest();
    put.setMetaModelIds(List.of(1L, 2L, 3L));
    put.setViewRequests(List.of(requests));
    service.applySyncChanges(vsum, OWNER, put, true);
  }
}
