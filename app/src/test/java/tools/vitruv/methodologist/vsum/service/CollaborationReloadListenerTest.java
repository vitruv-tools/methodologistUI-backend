package tools.vitruv.methodologist.vsum.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import tools.vitruv.methodologist.apihandler.CollaborationServiceApiHandler;
import tools.vitruv.methodologist.general.event.UserRemovedEvent;
import tools.vitruv.methodologist.general.event.VsumAccessChangedEvent;
import tools.vitruv.methodologist.general.event.VsumChangedByUserEvent;
import tools.vitruv.methodologist.general.event.VsumRemovedEvent;

@ExtendWith(MockitoExtension.class)
class CollaborationReloadListenerTest {

  private static final Long VSUM_ID = 7L;
  private static final Long USER_ID = 42L;

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

  @Test
  void onVsumRemoved_schedulesTheClose_insteadOfSendingItOnTheCallersThread() {
    when(collaborationServiceApiHandler.isEnabled()).thenReturn(true);

    listener.onVsumRemoved(new VsumRemovedEvent(VSUM_ID));

    assertThat(scheduled).hasSize(1);
    verify(collaborationServiceApiHandler, never()).requestClose(anyLong());

    runScheduled();

    verify(collaborationServiceApiHandler, times(1)).requestClose(VSUM_ID);
    verify(collaborationServiceApiHandler, never()).requestReload(anyLong());
  }

  @Test
  void onVsumRemoved_schedulesNothing_whenTheIntegrationIsDisabled() {
    when(collaborationServiceApiHandler.isEnabled()).thenReturn(false);

    listener.onVsumRemoved(new VsumRemovedEvent(VSUM_ID));

    assertThat(scheduled).isEmpty();
  }

  @Test
  void notifications_areSentInTheOrderOfTheEvents() {
    when(collaborationServiceApiHandler.isEnabled()).thenReturn(true);

    listener.onVsumChangedByUser(new VsumChangedByUserEvent(VSUM_ID));
    listener.onVsumRemoved(new VsumRemovedEvent(VSUM_ID));
    runScheduled();

    InOrder inOrder = Mockito.inOrder(collaborationServiceApiHandler);
    inOrder.verify(collaborationServiceApiHandler).requestReload(VSUM_ID);
    inOrder.verify(collaborationServiceApiHandler).requestClose(VSUM_ID);
  }

  @Test
  void onVsumRemoved_dropsTheNotificationWithoutThrowing_whenTheExecutorRejectsIt() {
    when(collaborationServiceApiHandler.isEnabled()).thenReturn(true);
    Executor rejecting =
        task -> {
          throw new TaskRejectedException("queue full or shut down");
        };
    CollaborationReloadListener rejectingListener =
        new CollaborationReloadListener(collaborationServiceApiHandler, rejecting);

    assertThatCode(() -> rejectingListener.onVsumRemoved(new VsumRemovedEvent(VSUM_ID)))
        .doesNotThrowAnyException();
    verify(collaborationServiceApiHandler, never()).requestClose(anyLong());
  }

  @Test
  void requestClose_swallowsFailuresOfTheCollaborationServer() {
    doThrow(WebClientResponseException.create(503, "Service Unavailable", null, null, null))
        .when(collaborationServiceApiHandler)
        .requestClose(VSUM_ID);

    assertThatCode(() -> listener.requestClose(VSUM_ID)).doesNotThrowAnyException();
  }

  @Test
  void onVsumAccessChanged_schedulesTheAccessCheck_insteadOfSendingItOnTheCallersThread() {
    when(collaborationServiceApiHandler.isEnabled()).thenReturn(true);

    listener.onVsumAccessChanged(new VsumAccessChangedEvent(VSUM_ID));

    assertThat(scheduled).hasSize(1);
    verify(collaborationServiceApiHandler, never()).requestAccessCheck(anyLong());

    runScheduled();

    verify(collaborationServiceApiHandler, times(1)).requestAccessCheck(VSUM_ID);
    verify(collaborationServiceApiHandler, never()).requestReload(anyLong());
    verify(collaborationServiceApiHandler, never()).requestClose(anyLong());
  }

  @Test
  void onUserRemoved_schedulesTheUserAccessCheck_insteadOfSendingItOnTheCallersThread() {
    when(collaborationServiceApiHandler.isEnabled()).thenReturn(true);

    listener.onUserRemoved(new UserRemovedEvent(USER_ID));

    assertThat(scheduled).hasSize(1);
    verify(collaborationServiceApiHandler, never()).requestUserAccessCheck(anyLong());

    runScheduled();

    verify(collaborationServiceApiHandler, times(1)).requestUserAccessCheck(USER_ID);
    verify(collaborationServiceApiHandler, never()).requestAccessCheck(anyLong());
  }

  @Test
  void accessChecks_scheduleNothing_whenTheIntegrationIsDisabled() {
    when(collaborationServiceApiHandler.isEnabled()).thenReturn(false);

    listener.onVsumAccessChanged(new VsumAccessChangedEvent(VSUM_ID));
    listener.onUserRemoved(new UserRemovedEvent(USER_ID));

    assertThat(scheduled).isEmpty();
  }

  @Test
  void accessChecks_areDroppedAndLoggedWithoutThrowing_whenTheExecutorRejectsThem() {
    when(collaborationServiceApiHandler.isEnabled()).thenReturn(true);
    Executor rejecting =
        task -> {
          throw new TaskRejectedException("queue full or shut down");
        };
    CollaborationReloadListener rejectingListener =
        new CollaborationReloadListener(collaborationServiceApiHandler, rejecting);

    List<ILoggingEvent> events =
        logsOf(
            () -> {
              rejectingListener.onVsumAccessChanged(new VsumAccessChangedEvent(VSUM_ID));
              rejectingListener.onUserRemoved(new UserRemovedEvent(USER_ID));
            });

    assertThat(events).extracting(ILoggingEvent::getLevel).containsExactly(Level.WARN, Level.WARN);
    assertThat(events)
        .extracting(ILoggingEvent::getFormattedMessage)
        .containsExactly(
            "Access check of VSUM 7 not sent to the collaboration server: not scheduled",
            "Access check of user 42 not sent to the collaboration server: not scheduled");
    verify(collaborationServiceApiHandler, never()).requestAccessCheck(anyLong());
    verify(collaborationServiceApiHandler, never()).requestUserAccessCheck(anyLong());
  }

  @Test
  void requestAccessCheck_swallowsFailuresOfTheCollaborationServer() {
    doThrow(WebClientResponseException.create(503, "Service Unavailable", null, null, null))
        .when(collaborationServiceApiHandler)
        .requestAccessCheck(VSUM_ID);

    assertThatCode(() -> listener.requestAccessCheck(VSUM_ID)).doesNotThrowAnyException();
  }

  @Test
  void requestUserAccessCheck_swallowsFailuresWhileFetchingTheToken() {
    doThrow(new IllegalStateException("Keycloak down"))
        .when(collaborationServiceApiHandler)
        .requestUserAccessCheck(USER_ID);

    assertThatCode(() -> listener.requestUserAccessCheck(USER_ID)).doesNotThrowAnyException();
  }

  @Test
  void accessChecks_areSentInTheOrderOfTheEvents_betweenReloadsAndCloses() {
    when(collaborationServiceApiHandler.isEnabled()).thenReturn(true);

    listener.onVsumChangedByUser(new VsumChangedByUserEvent(VSUM_ID));
    listener.onVsumAccessChanged(new VsumAccessChangedEvent(VSUM_ID));
    listener.onUserRemoved(new UserRemovedEvent(USER_ID));
    listener.onVsumRemoved(new VsumRemovedEvent(VSUM_ID));
    runScheduled();

    InOrder inOrder = Mockito.inOrder(collaborationServiceApiHandler);
    inOrder.verify(collaborationServiceApiHandler).requestReload(VSUM_ID);
    inOrder.verify(collaborationServiceApiHandler).requestAccessCheck(VSUM_ID);
    inOrder.verify(collaborationServiceApiHandler).requestUserAccessCheck(USER_ID);
    inOrder.verify(collaborationServiceApiHandler).requestClose(VSUM_ID);
  }

  /** Runs the code and returns what {@link CollaborationReloadListener} logged. */
  private static List<ILoggingEvent> logsOf(Runnable code) {
    Logger logger = (Logger) LoggerFactory.getLogger(CollaborationReloadListener.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    try {
      code.run();
    } finally {
      logger.detachAppender(appender);
    }
    return appender.list;
  }

  private void runScheduled() {
    List<Runnable> tasks = List.copyOf(scheduled);
    scheduled.clear();
    tasks.forEach(Runnable::run);
  }
}
