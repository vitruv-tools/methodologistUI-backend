package tools.vitruv.methodologist.vsum.service;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.vitruv.methodologist.apihandler.CollaborationServiceApiHandler;
import tools.vitruv.methodologist.config.CollaborationReloadConfig;
import tools.vitruv.methodologist.general.event.VsumChangedByUserEvent;

/**
 * Tells the collaboration server to reload a VSUM after a user's change to it has been committed,
 * so the collaboration server's next writeback does not undo the change.
 *
 * <p>The notification is sent in the background, so the user's request does not wait for it, and it
 * is sent at most once per committed change; a rolled-back change sends none. A failing or
 * unreachable collaboration server is only logged and never affects the user's request.
 */
@Component
@Slf4j
public class CollaborationReloadListener {

  private final CollaborationServiceApiHandler collaborationServiceApiHandler;
  private final Executor executor;

  /**
   * Creates the listener.
   *
   * @param collaborationServiceApiHandler sends the reload requests to the collaboration server
   * @param executor runs the reload requests in the background, see {@link
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
    if (!collaborationServiceApiHandler.isEnabled()) {
      return;
    }
    Long vsumId = event.vsumId();
    try {
      executor.execute(() -> requestReload(vsumId));
    } catch (RejectedExecutionException e) {
      // the queue is full because the collaboration server hangs, or the application shuts down
      log.warn("Reload of VSUM {} not sent to the collaboration server: not scheduled", vsumId);
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
}
