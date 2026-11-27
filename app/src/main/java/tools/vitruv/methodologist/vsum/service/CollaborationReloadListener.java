package tools.vitruv.methodologist.vsum.service;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.vitruv.methodologist.apihandler.CollaborationServiceApiHandler;
import tools.vitruv.methodologist.config.CollaborationReloadConfig;
import tools.vitruv.methodologist.general.event.UserRemovedEvent;
import tools.vitruv.methodologist.general.event.VsumAccessChangedEvent;
import tools.vitruv.methodologist.general.event.VsumChangedByUserEvent;
import tools.vitruv.methodologist.general.event.VsumRemovedEvent;

/**
 * Tells the collaboration server to reload a VSUM after a user's change to it has been committed,
 * so the collaboration server's next writeback does not undo the change, and to close a VSUM after
 * it was moved to the trash or deleted, so its users stop editing it. After a member was removed
 * from a VSUM, or a user removed their account, it tells the collaboration server to check the open
 * connections of that VSUM or user again, so users who lost access stop editing.
 *
 * <p>The notifications are sent in the background, so the user's request does not wait for them,
 * and in the order of the commits. Each is sent at most once per committed change; a rolled-back
 * change sends none. A failing or unreachable collaboration server is only logged and never affects
 * the user's request.
 */
@Component
@Slf4j
public class CollaborationReloadListener {

  private static final String VSUM = "VSUM";
  private static final String USER = "user";

  private final CollaborationServiceApiHandler collaborationServiceApiHandler;
  private final Executor executor;

  /**
   * Creates the listener.
   *
   * @param collaborationServiceApiHandler sends the notifications to the collaboration server
   * @param executor runs the notifications in the background, see {@link
   *     CollaborationReloadConfig#collaborationReloadExecutor()}
   */
  public CollaborationReloadListener(
      CollaborationServiceApiHandler collaborationServiceApiHandler,
      @Qualifier(CollaborationReloadConfig.COLLABORATION_RELOAD_EXECUTOR) Executor executor) {
    this.collaborationServiceApiHandler = collaborationServiceApiHandler;
    this.executor = executor;
  }

  /**
   * Schedules the reload request for the changed VSUM. Runs only after the change has been
   * committed; does nothing if the collaboration integration is disabled.
   *
   * @param event the committed change of a VSUM by a user
   */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onVsumChangedByUser(VsumChangedByUserEvent event) {
    schedule("Reload", VSUM, event.vsumId(), this::requestReload);
  }

  /**
   * Schedules the close request for the removed VSUM. Runs only after the removal has been
   * committed; does nothing if the collaboration integration is disabled.
   *
   * @param event the committed removal of a VSUM
   */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onVsumRemoved(VsumRemovedEvent event) {
    schedule("Close", VSUM, event.vsumId(), this::requestClose);
  }

  /**
   * Schedules the access check of the VSUM whose members changed. Runs only after the change has
   * been committed; does nothing if the collaboration integration is disabled.
   *
   * @param event the committed change that may have taken access to a VSUM away
   */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onVsumAccessChanged(VsumAccessChangedEvent event) {
    schedule("Access check", VSUM, event.vsumId(), this::requestAccessCheck);
  }

  /**
   * Schedules the access check of the removed user's connections. Runs only after the removal has
   * been committed; does nothing if the collaboration integration is disabled.
   *
   * @param event the committed removal of a user's account
   */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onUserRemoved(UserRemovedEvent event) {
    schedule("Access check", USER, event.userId(), this::requestUserAccessCheck);
  }

  /**
   * Runs the notification on the executor.
   *
   * @param notification the kind of notification, for the log
   * @param subject what the id identifies, {@link #VSUM} or {@link #USER}, for the log
   * @param id the id of the VSUM or user the notification is about
   * @param send sends the notification
   */
  private void schedule(String notification, String subject, Long id, Consumer<Long> send) {
    if (!collaborationServiceApiHandler.isEnabled()) {
      return;
    }
    try {
      executor.execute(() -> send.accept(id));
    } catch (RejectedExecutionException e) {
      // the queue is full because the collaboration server hangs, or the application shuts down
      log.warn(
          "{} of {} {} not sent to the collaboration server: not scheduled",
          notification,
          subject,
          id);
    }
  }

  void requestReload(Long vsumId) {
    try {
      collaborationServiceApiHandler.requestReload(vsumId);
      log.debug("Collaboration server asked to reload VSUM {}", vsumId);
    } catch (RuntimeException e) {
      log.warn("Collaboration server could not be asked to reload VSUM {}", vsumId, e);
    }
  }

  void requestClose(Long vsumId) {
    try {
      collaborationServiceApiHandler.requestClose(vsumId);
      log.debug("Collaboration server asked to close VSUM {}", vsumId);
    } catch (RuntimeException e) {
      log.warn("Collaboration server could not be asked to close VSUM {}", vsumId, e);
    }
  }

  void requestAccessCheck(Long vsumId) {
    try {
      collaborationServiceApiHandler.requestAccessCheck(vsumId);
      log.debug("Collaboration server asked to check the access to VSUM {}", vsumId);
    } catch (RuntimeException e) {
      log.warn("Collaboration server could not be asked to check the access to VSUM {}", vsumId, e);
    }
  }

  void requestUserAccessCheck(Long userId) {
    try {
      collaborationServiceApiHandler.requestUserAccessCheck(userId);
      log.debug("Collaboration server asked to check the access of user {}", userId);
    } catch (RuntimeException e) {
      log.warn("Collaboration server could not be asked to check the access of user {}", userId, e);
    }
  }
}
