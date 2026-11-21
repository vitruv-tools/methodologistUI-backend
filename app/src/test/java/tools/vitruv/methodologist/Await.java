package tools.vitruv.methodologist;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;

/** Waits in tests for a condition that another thread makes true. */
public final class Await {

  private Await() {}

  /**
   * Checks the condition at a fixed interval until it holds.
   *
   * @param description what is awaited, for the failure message
   * @param timeout how long to wait at most
   * @param interval the time between two checks
   * @param condition the condition to wait for
   * @throws AssertionError if the condition does not hold within the timeout
   */
  public static void until(
      String description, Duration timeout, Duration interval, BooleanSupplier condition) {
    CompletableFuture<Void> satisfied = new CompletableFuture<>();
    ScheduledExecutorService poller = Executors.newSingleThreadScheduledExecutor();
    try {
      poller.scheduleWithFixedDelay(
          () -> {
            try {
              if (condition.getAsBoolean()) {
                satisfied.complete(null);
              }
            } catch (RuntimeException | AssertionError e) {
              satisfied.completeExceptionally(e);
            }
          },
          0,
          interval.toMillis(),
          TimeUnit.MILLISECONDS);
      satisfied.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      throw new AssertionError("Not " + description + " within " + timeout, e);
    } catch (ExecutionException e) {
      throw new AssertionError("Checking whether " + description + " failed", e.getCause());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError("Interrupted while waiting until " + description, e);
    } finally {
      poller.shutdownNow();
    }
  }
}
