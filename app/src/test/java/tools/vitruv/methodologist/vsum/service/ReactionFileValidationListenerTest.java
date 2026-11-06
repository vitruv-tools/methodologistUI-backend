package tools.vitruv.methodologist.vsum.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.vitruv.methodologist.general.event.ReactionFileUpdatedEvent;
import tools.vitruv.methodologist.vsum.model.repository.FineGranularMetaModelRelationRepository;
import tools.vitruv.methodologist.vsum.model.repository.MetaModelRelationRepository;

@ExtendWith(MockitoExtension.class)
class ReactionFileValidationListenerTest {

  private static final Long FILE_ID = 21L;

  @Mock private MetaModelRelationRepository metaModelRelationRepository;
  @Mock private FineGranularMetaModelRelationRepository fineGranularMetaModelRelationRepository;
  @Mock private VsumValidationService vsumValidationService;

  private ReactionFileValidationListener listener;

  @BeforeEach
  void setUp() {
    listener =
        new ReactionFileValidationListener(
            metaModelRelationRepository,
            fineGranularMetaModelRelationRepository,
            vsumValidationService);
  }

  @AfterEach
  void tearDown() {
    listener.shutdown();
  }

  @Test
  void requestsValidationOnce_forEveryVsumUsingTheFile() {
    when(metaModelRelationRepository.findVsumIdsByReactionFileId(FILE_ID))
        .thenReturn(List.of(1L, 2L));
    when(fineGranularMetaModelRelationRepository.findVsumIdsByReactionFileId(FILE_ID))
        .thenReturn(List.of(2L, 3L));

    listener.requestValidationForAffectedVsums(FILE_ID);

    InOrder order = inOrder(vsumValidationService);
    order.verify(vsumValidationService).requestValidation(1L);
    order.verify(vsumValidationService).requestValidation(2L);
    order.verify(vsumValidationService).requestValidation(3L);
    verifyNoMoreInteractions(vsumValidationService);
  }

  @Test
  void requestsNothing_whenNoVsumUsesTheFile() {
    when(metaModelRelationRepository.findVsumIdsByReactionFileId(FILE_ID)).thenReturn(List.of());
    when(fineGranularMetaModelRelationRepository.findVsumIdsByReactionFileId(FILE_ID))
        .thenReturn(List.of());

    listener.requestValidationForAffectedVsums(FILE_ID);

    verifyNoInteractions(vsumValidationService);
  }

  @Test
  void swallowsLookupFailures() {
    when(metaModelRelationRepository.findVsumIdsByReactionFileId(FILE_ID))
        .thenThrow(new IllegalStateException("database down"));

    assertThatCode(() -> listener.requestValidationForAffectedVsums(FILE_ID))
        .doesNotThrowAnyException();
    verifyNoInteractions(vsumValidationService);
  }

  @Test
  void swallowsFailuresWhileRequestingValidation() {
    when(metaModelRelationRepository.findVsumIdsByReactionFileId(FILE_ID)).thenReturn(List.of(1L));
    when(fineGranularMetaModelRelationRepository.findVsumIdsByReactionFileId(FILE_ID))
        .thenReturn(List.of());
    doThrow(new IllegalStateException("rejected"))
        .when(vsumValidationService)
        .requestValidation(1L);

    assertThatCode(() -> listener.requestValidationForAffectedVsums(FILE_ID))
        .doesNotThrowAnyException();
  }

  @Test
  void onReactionFileUpdated_looksUpTheVsumsInTheBackground() {
    when(metaModelRelationRepository.findVsumIdsByReactionFileId(FILE_ID)).thenReturn(List.of(1L));
    when(fineGranularMetaModelRelationRepository.findVsumIdsByReactionFileId(FILE_ID))
        .thenReturn(List.of());

    listener.onReactionFileUpdated(new ReactionFileUpdatedEvent(FILE_ID));

    verify(vsumValidationService, timeout(5_000)).requestValidation(1L);
  }

  @Test
  void onReactionFileUpdated_doesNotThrow_afterShutdown() {
    listener.shutdown();

    assertThatCode(() -> listener.onReactionFileUpdated(new ReactionFileUpdatedEvent(FILE_ID)))
        .doesNotThrowAnyException();
    verifyNoInteractions(metaModelRelationRepository, vsumValidationService);
  }
}
