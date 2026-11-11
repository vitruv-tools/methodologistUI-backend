package tools.vitruv.methodologist.vsum.service;

import jakarta.annotation.PreDestroy;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.vitruv.methodologist.general.event.ReactionFileUpdatedEvent;
import tools.vitruv.methodologist.vsum.model.repository.FineGranularMetaModelRelationRepository;
import tools.vitruv.methodologist.vsum.model.repository.MetaModelRelationRepository;

/**
 * Requests a validation of every VSUM using a reaction file after an update of that file has been
 * committed. The affected VSUMs are looked up in the background, so the update request does not
 * wait for them.
 */
@Component
@Slf4j
public class ReactionFileValidationListener {

  static final String THREAD_NAME_PREFIX = "reaction-validation-lookup-";

  private final MetaModelRelationRepository metaModelRelationRepository;
  private final FineGranularMetaModelRelationRepository fineGranularMetaModelRelationRepository;
  private final VsumValidationService vsumValidationService;
  private final ExecutorService executor;

  /**
   * Creates the listener with its own executor for the background lookups.
   *
   * @param metaModelRelationRepository finds the VSUMs using the file in a relation
   * @param fineGranularMetaModelRelationRepository finds the VSUMs using the file in a
   *     fine-granular relation
   * @param vsumValidationService validates the affected VSUMs
   */
  public ReactionFileValidationListener(
      MetaModelRelationRepository metaModelRelationRepository,
      FineGranularMetaModelRelationRepository fineGranularMetaModelRelationRepository,
      VsumValidationService vsumValidationService) {
    this.metaModelRelationRepository = metaModelRelationRepository;
    this.fineGranularMetaModelRelationRepository = fineGranularMetaModelRelationRepository;
    this.vsumValidationService = vsumValidationService;
    this.executor =
        Executors.newSingleThreadExecutor(
            Thread.ofPlatform().name(THREAD_NAME_PREFIX, 0).daemon(true).factory());
  }

  /**
   * Schedules the validation of the VSUMs using the updated reaction file. Runs only after the
   * update has been committed; failures are logged and never reach the update request.
   *
   * @param event the committed update of a reaction file
   */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onReactionFileUpdated(ReactionFileUpdatedEvent event) {
    Long fileId = event.fileId();
    try {
      executor.execute(() -> requestValidationForAffectedVsums(fileId));
    } catch (RejectedExecutionException e) {
      // only happens while the application shuts down
      log.debug("Validation for reaction file {} not scheduled: executor shut down", fileId);
    }
  }

  @PreDestroy
  void shutdown() {
    executor.shutdownNow();
  }

  void requestValidationForAffectedVsums(Long fileId) {
    try {
      Set<Long> vsumIds =
          new LinkedHashSet<>(metaModelRelationRepository.findVsumIdsByReactionFileId(fileId));
      vsumIds.addAll(fineGranularMetaModelRelationRepository.findVsumIdsByReactionFileId(fileId));
      vsumIds.forEach(vsumValidationService::requestValidation);
    } catch (RuntimeException e) {
      log.error("Validation for reaction file {} could not be requested", fileId, e);
    }
  }
}
