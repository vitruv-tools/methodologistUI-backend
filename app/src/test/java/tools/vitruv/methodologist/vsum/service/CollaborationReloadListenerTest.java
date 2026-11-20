package tools.vitruv.methodologist.vsum.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import tools.vitruv.methodologist.apihandler.CollaborationServiceApiHandler;
import tools.vitruv.methodologist.general.event.VsumChangedByUserEvent;

@ExtendWith(MockitoExtension.class)
class CollaborationReloadListenerTest {

  private static final Long VSUM_ID = 7L;

  @Mock private CollaborationServiceApiHandler collaborationServiceApiHandler;

  /** Collects the scheduled notifications, so the test decides when they run. */
  private final List<Runnable> scheduled = new ArrayList<>();

  private CollaborationReloadListener listener;

  @BeforeEach
  void setUp() {
    listener = new CollaborationReloadListener(collaborationServiceApiHandler, scheduled::add);
  }

  @Test
  void onVsumChangedByUser_schedulesTheReload_insteadOfSendingItOnTheCallersThread() {
    when(collaborationServiceApiHandler.isEnabled()).thenReturn(true);

    listener.onVsumChangedByUser(new VsumChangedByUserEvent(VSUM_ID));

    assertThat(scheduled).hasSize(1);
    verify(collaborationServiceApiHandler, never()).requestReload(anyLong());

    runScheduled();

    verify(collaborationServiceApiHandler, times(1)).requestReload(VSUM_ID);
  }

  @Test
  void onVsumChangedByUser_schedulesNothing_whenTheIntegrationIsDisabled() {
    when(collaborationServiceApiHandler.isEnabled()).thenReturn(false);

    listener.onVsumChangedByUser(new VsumChangedByUserEvent(VSUM_ID));

    assertThat(scheduled).isEmpty();
    verify(collaborationServiceApiHandler, never()).requestReload(anyLong());
  }

  @Test
  void onVsumChangedByUser_sendsOneReloadPerEvent() {
    when(collaborationServiceApiHandler.isEnabled()).thenReturn(true);

    listener.onVsumChangedByUser(new VsumChangedByUserEvent(1L));
    listener.onVsumChangedByUser(new VsumChangedByUserEvent(2L));
    runScheduled();

    verify(collaborationServiceApiHandler, times(1)).requestReload(1L);
    verify(collaborationServiceApiHandler, times(1)).requestReload(2L);
    verify(collaborationServiceApiHandler, times(2)).requestReload(anyLong());
  }

  @Test
  void onVsumChangedByUser_dropsTheNotificationWithoutThrowing_whenTheExecutorRejectsIt() {
    when(collaborationServiceApiHandler.isEnabled()).thenReturn(true);
    Executor rejecting =
        task -> {
          throw new TaskRejectedException("queue full or shut down");
        };
    CollaborationReloadListener rejectingListener =
        new CollaborationReloadListener(collaborationServiceApiHandler, rejecting);

    assertThatCode(() -> rejectingListener.onVsumChangedByUser(new VsumChangedByUserEvent(VSUM_ID)))
        .doesNotThrowAnyException();
    verify(collaborationServiceApiHandler, never()).requestReload(anyLong());
  }

  @Test
  void requestReload_swallowsFailuresOfTheCollaborationServer() {
    doThrow(WebClientResponseException.create(503, "Service Unavailable", null, null, null))
        .when(collaborationServiceApiHandler)
        .requestReload(VSUM_ID);

    assertThatCode(() -> listener.requestReload(VSUM_ID)).doesNotThrowAnyException();
  }

  @Test
  void requestReload_swallowsFailuresWhileFetchingTheToken() {
    doThrow(new IllegalStateException("Keycloak down"))
        .when(collaborationServiceApiHandler)
        .requestReload(VSUM_ID);

    assertThatCode(() -> listener.requestReload(VSUM_ID)).doesNotThrowAnyException();
  }

  private void runScheduled() {
    List<Runnable> tasks = List.copyOf(scheduled);
    scheduled.clear();
    tasks.forEach(Runnable::run);
  }
}
