package tools.vitruv.methodologist.vsum.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.vitruv.methodologist.vsum.model.MetaModelRelation;
import tools.vitruv.methodologist.vsum.model.Vsum;
import tools.vitruv.methodologist.vsum.model.VsumMetaModel;
import tools.vitruv.methodologist.vsum.model.VsumUser;
import tools.vitruv.methodologist.vsum.model.VsumView;
import tools.vitruv.methodologist.vsum.model.repository.VsumRepository;

@ExtendWith(MockitoExtension.class)
class VsumCleanupServiceTest {

  @InjectMocks private VsumCleanupService service;
  @Mock private VsumRepository vsumRepository;
  @Mock private VsumHistoryService vsumHistoryService;
  @Mock private VsumUserService vsumUserService;
  @Mock private VsumViewService vsumViewService;
  @Mock private MetaModelRelationService metaModelRelationService;
  @Mock private VsumMetaModelService vsumMetaModelService;
  @Mock private VsumBuildService vsumBuildService;

  @Test
  void delete_invokesSubservices_forEachOldVsum_andUses30DayCutoff() {
    Vsum a = new Vsum();
    a.setId(1L);
    Vsum b = new Vsum();
    b.setId(2L);

    when(vsumRepository.findAllByRemovedAtBefore(any(Instant.class))).thenReturn(List.of(a, b));

    service.delete();

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

    service.delete();

    verify(vsumRepository).findAllByRemovedAtBefore(any(Instant.class));
    verifyNoInteractions(vsumUserService, vsumMetaModelService, metaModelRelationService);
  }

  @Test
  void deleteProjectData_deletesViewsAndRelationsBeforeTheMetaModelCopies() {
    Vsum entity = new Vsum();
    entity.setId(42L);
    entity.setRemovedAt(Instant.now());

    service.deleteProjectData(entity);

    // Views and relations reference the meta-model copies, so they must go first.
    InOrder order =
        inOrder(vsumViewService, metaModelRelationService, vsumMetaModelService, vsumBuildService);
    order.verify(vsumViewService).deleteByVsum(entity);
    order.verify(metaModelRelationService).deleteByVsum(entity);
    order.verify(vsumMetaModelService).delete(entity);
    order.verify(vsumBuildService).deleteByVsum(entity);
    verify(vsumHistoryService).delete(entity);
    verify(vsumUserService).delete(entity);
  }

  @Test
  void deleteProjectData_detachesDeletedChildrenBeforeTheNextStep_soAFlushCannotRestoreThem() {
    Vsum entity = new Vsum();
    entity.setId(43L);
    entity.setRemovedAt(Instant.now());
    entity.getVsumUsers().add(new VsumUser());
    entity.getVsumMetaModels().add(new VsumMetaModel());
    entity.getMetaModelRelations().add(new MetaModelRelation());
    entity.getViews().add(new VsumView());

    // Each step runs queries that flush the persistence context, and the flush cascades from the
    // Vsum to whatever its collections still hold. So the children deleted by the previous step
    // must already be gone from the Vsum when the next step starts.
    doAnswer(
            inv -> {
              assertThat(entity.getVsumUsers()).isEmpty();
              return null;
            })
        .when(vsumViewService)
        .deleteByVsum(entity);
    doAnswer(
            inv -> {
              assertThat(entity.getViews()).isEmpty();
              return null;
            })
        .when(metaModelRelationService)
        .deleteByVsum(entity);
    doAnswer(
            inv -> {
              assertThat(entity.getMetaModelRelations()).isEmpty();
              return null;
            })
        .when(vsumMetaModelService)
        .delete(entity);
    doAnswer(
            inv -> {
              assertThat(entity.getVsumMetaModels()).isEmpty();
              return null;
            })
        .when(vsumBuildService)
        .deleteByVsum(entity);

    service.deleteProjectData(entity);

    verify(vsumBuildService).deleteByVsum(entity);
  }
}
