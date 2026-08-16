package tools.vitruv.methodologist.general;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link MemoizedSupplier}. */
class MemoizedSupplierTest {

  @Test
  void getComputesValueOnceAndReturnsTheSameResult() {
    AtomicInteger calls = new AtomicInteger();
    MemoizedSupplier<Integer> supplier =
        new MemoizedSupplier<>(() -> calls.incrementAndGet() + 10);

    assertThat(supplier.wasComputed()).isFalse();
    assertThat(supplier.get()).isEqualTo(11);
    assertThat(supplier.get()).isEqualTo(11);
    assertThat(calls.get()).isEqualTo(1);
    assertThat(supplier.wasComputed()).isTrue();
  }
}
