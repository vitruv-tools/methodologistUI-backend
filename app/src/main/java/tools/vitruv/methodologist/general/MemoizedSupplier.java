package tools.vitruv.methodologist.general;

import java.util.function.Supplier;

/**
 * A supplier that memoizes its result. The first call to {@link #get()} computes the value using
 * the provided supplier, and subsequent calls return the same value.
 *
 * @param <T> the type of results supplied by this supplier
 */
public class MemoizedSupplier<T> implements Supplier<T> {

  private final Supplier<T> supplier;
  private boolean computed = false;
  private T value;

  /**
   * Constructs a new MemoizedSupplier with the given supplier.
   *
   * @param supplier the supplier to be memoized
   */
  public MemoizedSupplier(Supplier<T> supplier) {
    this.supplier = supplier;
  }

  /**
   * Returns the memoized value. If it has not been computed yet, it is computed using the
   * underlying supplier.
   *
   * @return the memoized value
   */
  @Override
  public T get() {
    if (!computed) {
      value = supplier.get();
      computed = true;
    }
    return value;
  }

  /**
   * Returns whether the value has already been computed.
   *
   * @return true if the value has been computed, false otherwise
   */
  public boolean wasComputed() {
    return computed;
  }
}
