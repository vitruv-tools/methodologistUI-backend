package tools.vitruv.methodologist.vsum.build;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class BuildCoordinator {

  private final ConcurrentHashMap<BuildKey, CompletableFuture<byte[]>> inFlight =
      new ConcurrentHashMap<>();

  public byte[] runOncePerKey(BuildKey key, Supplier<byte[]> buildSupplier) {
    Objects.requireNonNull(key);
    Objects.requireNonNull(buildSupplier);

    CompletableFuture<byte[]> future =
        inFlight.computeIfAbsent(
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

    return future.join();
  }
}
