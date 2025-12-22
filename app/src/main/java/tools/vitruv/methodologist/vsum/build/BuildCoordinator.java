package tools.vitruv.methodologist.vsum.build;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * Coordinates concurrent build requests so that identical builds are executed at most once.
 *
 * <p>This component maintains a map of in-flight builds keyed by {@code BuildKey}. Callers should
 * use {@link #startOrGet(BuildKey, java.util.function.Supplier)} to obtain a {@link
 * java.util.concurrent.CompletableFuture} that represents the result of a build. If a build for the
 * same key is already running, the existing future is returned; otherwise the provided supplier is
 * used to start the build and its result is stored and shared.
 *
 * <p>Thread-safety: this class is safe for concurrent use. It relies on a {@link
 * java.util.concurrent.ConcurrentHashMap} and {@link java.util.concurrent.CompletableFuture} to
 * coordinate concurrent callers.
 */
@Component
public class BuildCoordinator {

  private final ConcurrentHashMap<BuildKey, CompletableFuture<byte[]>> inFlight =
      new ConcurrentHashMap<>();

  /**
   * Returns whether a build for the given key is currently in flight.
   *
   * @param key the build key to check; must not be {@code null}
   * @return {@code true} if a build for {@code key} is currently running, {@code false} otherwise
   * @throws NullPointerException if {@code key} is {@code null}
   */
  public boolean isInFlight(BuildKey key) {
    return inFlight.containsKey(key);
  }

  /**
   * Start a build if absent, or return the existing in-flight future.
   *
   * <p>The method returns a {@link java.util.concurrent.CompletableFuture} representing the build
   * result. The supplied {@code buildSupplier} is invoked only by the caller that successfully
   * registers the new future in the internal map; other concurrent callers receive the same future.
   *
   * <p>Important: this method does NOT block or join the returned future. Callers that want the
   * result must explicitly call {@code future.join()} or otherwise handle completion.
   *
   * <p>Implementations typically remove the completed future from the internal map to avoid memory
   * leaks; callers should not rely on the future remaining present after completion.
   *
   * @param key the build key; must not be {@code null}
   * @param buildSupplier a non-null supplier that performs the build and returns the result bytes
   * @return a {@link java.util.concurrent.CompletableFuture} that will complete with the build
   *     bytes
   * @throws NullPointerException if {@code key} or {@code buildSupplier} is {@code null}
   */
  public CompletableFuture<byte[]> startOrGet(BuildKey key, Supplier<byte[]> buildSupplier) {
    Objects.requireNonNull(key);
    Objects.requireNonNull(buildSupplier);

    return inFlight.computeIfAbsent(
        key,
        k ->
            CompletableFuture.supplyAsync(
                () -> {
                  try {
                    return buildSupplier.get();
                  } finally {
                    inFlight.remove(k);
                  }
                }));
  }

  /**
   * Blocking variant that starts the build if absent, or waits for an in-flight build and returns
   * its result.
   *
   * <p>This method delegates to {@link #startOrGet(BuildKey, java.util.function.Supplier)} and
   * invokes {@code CompletableFuture.join()} to block the calling thread until the build completes.
   *
   * @param key the build key; must not be {@code null}
   * @param buildSupplier a supplier that performs the build when this caller registers the new
   *     future; must not be {@code null}
   * @return the build artifact bytes produced by the supplier
   * @throws NullPointerException if {@code key} or {@code buildSupplier} is {@code null}
   * @throws java.util.concurrent.CompletionException if the build completed exceptionally (the
   *     cause will be the original exception thrown by the supplier)
   * @throws java.util.concurrent.CancellationException if the build was cancelled
   */
  public byte[] runOncePerKey(BuildKey key, Supplier<byte[]> buildSupplier) {
    return startOrGet(key, buildSupplier).join();
  }
}
