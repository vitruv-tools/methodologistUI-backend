package tools.vitruv.methodologist.log;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Tests when {@link AuditLog} writes its entries. */
class AuditLogTest {

  private final List<String> written = new ArrayList<>();

  @AfterEach
  void clearSynchronization() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  void writesTheEntryRightAway_withoutTransaction() {
    AuditLog.afterCommit(() -> written.add("entry"));

    assertThat(written).containsExactly("entry");
  }

  @Test
  void writesTheEntryOnlyAfterTheTransactionCommits() {
    TransactionSynchronizationManager.initSynchronization();

    AuditLog.afterCommit(() -> written.add("entry"));
    assertThat(written).isEmpty();

    TransactionSynchronizationManager.getSynchronizations()
        .forEach(TransactionSynchronization::afterCommit);
    assertThat(written).containsExactly("entry");
  }

  @Test
  void doesNotWriteTheEntry_whenTheTransactionRollsBack() {
    TransactionSynchronizationManager.initSynchronization();

    AuditLog.afterCommit(() -> written.add("entry"));
    TransactionSynchronizationManager.getSynchronizations()
        .forEach(
            synchronization ->
                synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

    assertThat(written).isEmpty();
  }
}
