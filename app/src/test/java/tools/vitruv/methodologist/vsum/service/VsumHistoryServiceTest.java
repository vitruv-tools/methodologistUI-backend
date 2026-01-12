package tools.vitruv.methodologist.vsum.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tools.vitruv.methodologist.messages.Error.USER_DOSE_NOT_HAVE_ACCESS;
import static tools.vitruv.methodologist.messages.Error.VSUM_HISTORY_ID_NOT_FOUND_ERROR;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import tools.vitruv.methodologist.exception.NotFoundException;
import tools.vitruv.methodologist.user.model.User;
import tools.vitruv.methodologist.user.model.repository.UserRepository;
import tools.vitruv.methodologist.vsum.VsumRepresentation;
import tools.vitruv.methodologist.vsum.controller.dto.request.VsumSyncChangesPutRequest;
import tools.vitruv.methodologist.vsum.controller.dto.response.VsumHistoryResponse;
import tools.vitruv.methodologist.vsum.mapper.VsumHistoryMapper;
import tools.vitruv.methodologist.vsum.model.Vsum;
import tools.vitruv.methodologist.vsum.model.VsumHistory;
import tools.vitruv.methodologist.vsum.model.VsumUser;
import tools.vitruv.methodologist.vsum.model.repository.VsumHistoryRepository;
import tools.vitruv.methodologist.vsum.model.repository.VsumUserRepository;

@ExtendWith(MockitoExtension.class)
class VsumHistoryServiceTest {

  @Mock VsumHistoryRepository vsumHistoryRepository;
  @Mock VsumHistoryMapper vsumHistoryMapper;
  @Mock UserRepository userRepository;
  @Mock VsumUserRepository vsumUserRepository;
  @Mock VsumService vsumService;

  private VsumHistoryService service;

  @BeforeEach
  void setUp() {
    service =
        new VsumHistoryService(
            vsumHistoryRepository,
            vsumHistoryMapper,
            5L,
            userRepository,
            vsumUserRepository,
            vsumService);
  }

  @Test
  void create_savesSnapshot_withMappedRepresentation_andNoDeletion_whenCountEqualsLimit() {
    Vsum vsum = new Vsum();
    vsum.setId(1L);
    User creator = new User();
    creator.setEmail("u@ex.com");
    VsumRepresentation vsumRepresentation = new VsumRepresentation();

    when(vsumHistoryRepository.countByVsum(vsum)).thenReturn(5L);
    when(vsumHistoryMapper.toVsumRepresentation(vsum)).thenReturn(vsumRepresentation);
    when(vsumHistoryRepository.save(any(VsumHistory.class))).thenAnswer(inv -> inv.getArgument(0));

    VsumHistory saved = service.create(vsum, creator);

    verify(vsumHistoryRepository, never()).findTopByVsumOrderByCreatedAtDesc(any());
    verify(vsumHistoryRepository, never()).delete(any(VsumHistory.class));

    ArgumentCaptor<VsumHistory> cap = ArgumentCaptor.forClass(VsumHistory.class);
    verify(vsumHistoryRepository).save(cap.capture());
    VsumHistory toSave = cap.getValue();

    assertThat(toSave.getVsum()).isSameAs(vsum);
    assertThat(toSave.getCreator()).isSameAs(creator);
    assertThat(toSave.getRepresentation()).isEqualTo(vsumRepresentation);

    assertThat(saved).isSameAs(toSave);
  }

  @Test
  void create_deletesNewest_whenCountExceedsLimit_andThenSaves() {
    Vsum vsum = new Vsum();
    vsum.setId(2L);
    User creator = new User();
    creator.setEmail("u@ex.com");
    VsumRepresentation vsumRepresentation = new VsumRepresentation();

    when(vsumHistoryRepository.countByVsum(vsum)).thenReturn(6L);
    VsumHistory newest = VsumHistory.builder().id(99L).vsum(vsum).build();
    when(vsumHistoryRepository.findTopByVsumOrderByCreatedAtDesc(vsum))
        .thenReturn(Optional.of(newest));

    when(vsumHistoryMapper.toVsumRepresentation(vsum)).thenReturn(vsumRepresentation);
    when(vsumHistoryRepository.save(any(VsumHistory.class))).thenAnswer(inv -> inv.getArgument(0));

    VsumHistory saved = service.create(vsum, creator);

    verify(vsumHistoryRepository).findTopByVsumOrderByCreatedAtDesc(vsum);
    verify(vsumHistoryRepository).delete(newest);

    ArgumentCaptor<VsumHistory> cap = ArgumentCaptor.forClass(VsumHistory.class);
    verify(vsumHistoryRepository).save(cap.capture());
    assertThat(cap.getValue().getRepresentation()).isEqualTo(vsumRepresentation);
    assertThat(saved).isSameAs(cap.getValue());
  }

  @Test
  void create_overLimit_butNoTopFound_skipsDeletion_andStillSaves() {
    Vsum vsum = new Vsum();
    vsum.setId(3L);
    User creator = new User();
    creator.setEmail("u@ex.com");
    VsumRepresentation vsumRepresentation = new VsumRepresentation();

    when(vsumHistoryRepository.countByVsum(vsum)).thenReturn(10L);
    when(vsumHistoryRepository.findTopByVsumOrderByCreatedAtDesc(vsum))
        .thenReturn(Optional.empty());
    when(vsumHistoryMapper.toVsumRepresentation(vsum)).thenReturn(vsumRepresentation);
    when(vsumHistoryRepository.save(any(VsumHistory.class))).thenAnswer(inv -> inv.getArgument(0));

    VsumHistory saved = service.create(vsum, creator);

    verify(vsumHistoryRepository).findTopByVsumOrderByCreatedAtDesc(vsum);
    verify(vsumHistoryRepository, never()).delete(any());

    verify(vsumHistoryRepository).save(any(VsumHistory.class));
    assertThat(saved.getRepresentation()).isEqualTo(vsumRepresentation);
    assertThat(saved.getVsum()).isSameAs(vsum);
    assertThat(saved.getCreator()).isSameAs(creator);
  }

  @Test
  void create_withZeroLimit_deletesNewestWhenAnyExists_thenSaves() {
    service =
        new VsumHistoryService(vsumHistoryRepository, vsumHistoryMapper, 0L, null, null, null);

    Vsum vsum = new Vsum();
    vsum.setId(4L);
    User creator = new User();
    creator.setEmail("u@ex.com");
    VsumRepresentation vsumRepresentation = new VsumRepresentation();

    when(vsumHistoryRepository.countByVsum(vsum)).thenReturn(1L);
    VsumHistory newest = VsumHistory.builder().id(7L).vsum(vsum).build();
    when(vsumHistoryRepository.findTopByVsumOrderByCreatedAtDesc(vsum))
        .thenReturn(Optional.of(newest));
    when(vsumHistoryMapper.toVsumRepresentation(vsum)).thenReturn(vsumRepresentation);
    when(vsumHistoryRepository.save(any(VsumHistory.class))).thenAnswer(inv -> inv.getArgument(0));

    VsumHistory saved = service.create(vsum, creator);

    verify(vsumHistoryRepository).delete(newest);
    verify(vsumHistoryRepository).save(any(VsumHistory.class));
    assertThat(saved.getRepresentation()).isEqualTo(vsumRepresentation);
  }

  @Test
  void findAllByVsumId_returnsMappedList_whenHistoriesExist() {
    String callerEmail = "user@example.com";
    Long vsumId = 42L;
    VsumHistory h1 = VsumHistory.builder().id(1L).build();
    VsumHistory h2 = VsumHistory.builder().id(2L).build();

    when(vsumHistoryRepository.getVsumHistories(vsumId, callerEmail)).thenReturn(List.of(h1, h2));

    VsumHistoryResponse r1 = VsumHistoryResponse.builder().id(1L).build();
    VsumHistoryResponse r2 = VsumHistoryResponse.builder().id(2L).build();

    when(vsumHistoryMapper.toVsumHistoryResponse(h1)).thenReturn(r1);
    when(vsumHistoryMapper.toVsumHistoryResponse(h2)).thenReturn(r2);

    List<VsumHistoryResponse> result = service.findAllByVsumId(callerEmail, vsumId);

    assertThat(result).containsExactly(r1, r2);
    verify(vsumHistoryRepository).getVsumHistories(vsumId, callerEmail);
    verify(vsumHistoryMapper).toVsumHistoryResponse(h1);
    verify(vsumHistoryMapper).toVsumHistoryResponse(h2);
  }

  @Test
  void findAllByVsumId_returnsEmptyList_whenNoHistoriesExist() {
    Long vsumId = 777L;
    String callerEmail = "user@example.com";

    when(vsumHistoryRepository.getVsumHistories(vsumId, callerEmail)).thenReturn(List.of());

    List<VsumHistoryResponse> result = service.findAllByVsumId(callerEmail, vsumId);

    assertThat(result).isEmpty();
    verify(vsumHistoryRepository).getVsumHistories(vsumId, callerEmail);
    verify(vsumHistoryMapper, never()).toVsumHistoryResponse(any(VsumHistory.class));
  }

  @Test
  void revert_throwsAccessDenied_whenCallerNotFoundOrInactive() {
    String callerEmail = "missing@ex.com";
    Long historyId = 10L;

    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull(callerEmail))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.revert(callerEmail, historyId))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessageContaining(USER_DOSE_NOT_HAVE_ACCESS);

    verify(vsumHistoryRepository, never()).findById(any());
    verify(vsumUserRepository, never())
        .findByVsum_IdAndUser_EmailAndUser_RemovedAtIsNullAndVsum_RemovedAtIsNull(any(), any());
    verify(vsumService, never()).applySyncChanges(any(), any(), any(), anyBoolean());
  }

  @Test
  void revert_throwsNotFound_whenHistoryEntryDoesNotExist() {
    String callerEmail = "u@ex.com";
    Long historyId = 11L;

    User user = new User();
    user.setEmail(callerEmail);
    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull(callerEmail))
        .thenReturn(Optional.of(user));

    when(vsumHistoryRepository.findById(historyId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.revert(callerEmail, historyId))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining(VSUM_HISTORY_ID_NOT_FOUND_ERROR);

    verify(vsumUserRepository, never())
        .findByVsum_IdAndUser_EmailAndUser_RemovedAtIsNullAndVsum_RemovedAtIsNull(any(), any());
    verify(vsumService, never()).applySyncChanges(any(), any(), any(), anyBoolean());
  }

  @Test
  void revert_throwsAccessDenied_whenCallerHasNoAccessToVsumOfHistory() {
    String callerEmail = "u@ex.com";
    Long historyId = 12L;

    User user = new User();
    user.setEmail(callerEmail);
    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull(callerEmail))
        .thenReturn(Optional.of(user));

    Vsum vsum = new Vsum();
    vsum.setId(100L);

    VsumHistory history = VsumHistory.builder().id(historyId).vsum(vsum).build();
    when(vsumHistoryRepository.findById(historyId)).thenReturn(Optional.of(history));

    when(vsumUserRepository
            .findByVsum_IdAndUser_EmailAndUser_RemovedAtIsNullAndVsum_RemovedAtIsNull(
                vsum.getId(), callerEmail))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.revert(callerEmail, historyId))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessageContaining(USER_DOSE_NOT_HAVE_ACCESS);

    verify(vsumService, never()).applySyncChanges(any(), any(), any(), anyBoolean());
  }

  @Test
  void revert_createsAuditSnapshot_thenAppliesRecordedChanges_withoutCreatingHistoryAgain() {
    String callerEmail = "u@ex.com";
    Long historyId = 13L;

    User user = new User();
    user.setEmail(callerEmail);
    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull(callerEmail))
        .thenReturn(Optional.of(user));

    Vsum vsum = new Vsum();
    vsum.setId(101L);

    VsumRepresentation.MetaModelRelation rel =
        VsumRepresentation.MetaModelRelation.builder()
            .sourceId(1L)
            .targetId(2L)
            .relationFileStorage(9L)
            .build();

    VsumRepresentation rep =
        VsumRepresentation.builder()
            .metaModels(Set.of(1L, 2L, 3L))
            .metaModelsRealation(Set.of(rel))
            .build();

    VsumHistory history =
        VsumHistory.builder().id(historyId).vsum(vsum).representation(rep).build();
    when(vsumHistoryRepository.findById(historyId)).thenReturn(Optional.of(history));

    when(vsumUserRepository
            .findByVsum_IdAndUser_EmailAndUser_RemovedAtIsNullAndVsum_RemovedAtIsNull(
                vsum.getId(), callerEmail))
        .thenReturn(Optional.of(new VsumUser()));

    service.revert(callerEmail, historyId);

    ArgumentCaptor<VsumSyncChangesPutRequest> reqCap =
        ArgumentCaptor.forClass(VsumSyncChangesPutRequest.class);
    verify(vsumService).applySyncChanges(eq(vsum), eq(user), reqCap.capture(), eq(false));

    VsumSyncChangesPutRequest applied = reqCap.getValue();
    assertThat(applied.getMetaModelIds()).containsExactlyInAnyOrder(1L, 2L, 3L);
    assertThat(applied.getMetaModelRelationRequests())
        .hasSize(1)
        .first()
        .satisfies(
            r -> {
              assertThat(r.getSourceId()).isEqualTo(1L);
              assertThat(r.getTargetId()).isEqualTo(2L);
              assertThat(r.getReactionFileId()).isEqualTo(9L);
            });
  }

  @Test
  void revert_mapsNullCollectionsToEmptyLists() {
    String callerEmail = "u@ex.com";
    Long historyId = 14L;

    User user = new User();
    user.setEmail(callerEmail);
    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull(callerEmail))
        .thenReturn(Optional.of(user));

    Vsum vsum = new Vsum();
    vsum.setId(102L);

    VsumRepresentation rep =
        VsumRepresentation.builder().metaModels(null).metaModelsRealation(null).build();

    VsumHistory history =
        VsumHistory.builder().id(historyId).vsum(vsum).representation(rep).build();
    when(vsumHistoryRepository.findById(historyId)).thenReturn(Optional.of(history));

    when(vsumUserRepository
            .findByVsum_IdAndUser_EmailAndUser_RemovedAtIsNullAndVsum_RemovedAtIsNull(
                vsum.getId(), callerEmail))
        .thenReturn(Optional.of(new VsumUser()));

    service.revert(callerEmail, historyId);

    ArgumentCaptor<VsumSyncChangesPutRequest> reqCap =
        ArgumentCaptor.forClass(VsumSyncChangesPutRequest.class);
    verify(vsumService).applySyncChanges(eq(vsum), eq(user), reqCap.capture(), eq(false));

    VsumSyncChangesPutRequest applied = reqCap.getValue();
    assertThat(applied.getMetaModelIds()).isNotNull().isEmpty();
    assertThat(applied.getMetaModelRelationRequests()).isNotNull().isEmpty();
  }

  @Test
  void revert_filtersNullRelationsWhileMapping() {
    String callerEmail = "u@ex.com";
    Long historyId = 15L;

    User user = new User();
    user.setEmail(callerEmail);
    when(userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull(callerEmail))
        .thenReturn(Optional.of(user));

    Vsum vsum = new Vsum();
    vsum.setId(103L);

    VsumRepresentation.MetaModelRelation r1 =
        VsumRepresentation.MetaModelRelation.builder()
            .sourceId(10L)
            .targetId(20L)
            .relationFileStorage(30L)
            .build();

    VsumRepresentation rep =
        VsumRepresentation.builder()
            .metaModels(Set.of(10L, 20L))
            .metaModelsRealation(new HashSet<>(java.util.Arrays.asList(null, r1)))
            .build();

    VsumHistory history =
        VsumHistory.builder().id(historyId).vsum(vsum).representation(rep).build();
    when(vsumHistoryRepository.findById(historyId)).thenReturn(Optional.of(history));

    when(vsumUserRepository
            .findByVsum_IdAndUser_EmailAndUser_RemovedAtIsNullAndVsum_RemovedAtIsNull(
                vsum.getId(), callerEmail))
        .thenReturn(Optional.of(new VsumUser()));

    service.revert(callerEmail, historyId);

    ArgumentCaptor<VsumSyncChangesPutRequest> reqCap =
        ArgumentCaptor.forClass(VsumSyncChangesPutRequest.class);
    verify(vsumService).applySyncChanges(eq(vsum), eq(user), reqCap.capture(), eq(false));

    assertThat(reqCap.getValue().getMetaModelRelationRequests()).hasSize(1);
  }
}
