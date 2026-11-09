package tools.vitruv.methodologist.log;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Writes audit log entries for changes made in a transaction only once the transaction has
 * committed, so that a rolled back change does not appear in the log as done.
 */
public final class AuditLog {

  private AuditLog() {}

  /**
   * Writes the entry after the current transaction commits, or right away if there is no
   * transaction. If the transaction rolls back, the entry is not written.
   *
   * @param entry writes the log entry
   */
  public static void afterCommit(Runnable entry) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      entry.run();
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            entry.run();
          }
        });
  }
}
