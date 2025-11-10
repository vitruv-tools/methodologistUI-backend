package tools.vitruv.methodologist.vsum.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.vitruv.methodologist.user.model.User;
import tools.vitruv.methodologist.vsum.VsumRepresentation;
import tools.vitruv.methodologist.vsum.controller.dto.response.VsumHistoryResponse;
import tools.vitruv.methodologist.vsum.mapper.VsumHistoryMapper;
import tools.vitruv.methodologist.vsum.model.Vsum;
import tools.vitruv.methodologist.vsum.model.VsumHistory;
import tools.vitruv.methodologist.vsum.model.repository.VsumHistoryRepository;

@ExtendWith(MockitoExtension.class)
class VsumHistoryServiceTest {

  @Mock VsumHistoryRepository vsumHistoryRepository;
  @Mock VsumHistoryMapper vsumHistoryMapper;

  private VsumHistoryService service;

  @BeforeEach
  void setUp() {
    service = new VsumHistoryService(vsumHistoryRepository, vsumHistoryMapper, 5L);
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
    service = new VsumHistoryService(vsumHistoryRepository, vsumHistoryMapper, 0L);

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

    when(vsumHistoryRepository
            .findAllByVsum_IdAndVsum_User_EmailAndVsum_User_RemovedAtIsNullAndVsum_RemovedAtIsNullOrderByCreatedAtDesc(
                vsumId, callerEmail))
        .thenReturn(List.of(h1, h2));

    VsumHistoryResponse r1 = VsumHistoryResponse.builder().id(1L).build();
    VsumHistoryResponse r2 = VsumHistoryResponse.builder().id(2L).build();

    when(vsumHistoryMapper.toVsumHistoryResponse(h1)).thenReturn(r1);
    when(vsumHistoryMapper.toVsumHistoryResponse(h2)).thenReturn(r2);

    java.util.List<VsumHistoryResponse> result = service.findAllByVsumId(callerEmail, vsumId);

    assertThat(result).containsExactly(r1, r2);
    verify(vsumHistoryRepository)
        .findAllByVsum_IdAndVsum_User_EmailAndVsum_User_RemovedAtIsNullAndVsum_RemovedAtIsNullOrderByCreatedAtDesc(
            vsumId, callerEmail);
    verify(vsumHistoryMapper).toVsumHistoryResponse(h1);
    verify(vsumHistoryMapper).toVsumHistoryResponse(h2);
  }

  @Test
  void findAllByVsumId_returnsEmptyList_whenNoHistoriesExist() {
    Long vsumId = 777L;
    String callerEmail = "user@example.com";

    when(vsumHistoryRepository
            .findAllByVsum_IdAndVsum_User_EmailAndVsum_User_RemovedAtIsNullAndVsum_RemovedAtIsNullOrderByCreatedAtDesc(
                vsumId, callerEmail))
        .thenReturn(java.util.List.of());

    java.util.List<VsumHistoryResponse> result = service.findAllByVsumId(callerEmail, vsumId);

    assertThat(result).isEmpty();
    verify(vsumHistoryRepository)
        .findAllByVsum_IdAndVsum_User_EmailAndVsum_User_RemovedAtIsNullAndVsum_RemovedAtIsNullOrderByCreatedAtDesc(
            vsumId, callerEmail);
    verify(vsumHistoryMapper, never()).toVsumHistoryResponse(any(VsumHistory.class));
  }
}
