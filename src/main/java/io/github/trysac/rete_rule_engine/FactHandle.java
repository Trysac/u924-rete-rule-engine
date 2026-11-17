package io.github.trysac.rete_rule_engine;

/**
 * Handle to an inserted {@link Fact} within a {@link Session}, carrying its monotonically
 * increasing recency identifier.
 */
public final class FactHandle {

  private final long id;
  private final Fact fact;

  FactHandle(long id, Fact fact) {
    this.id = id;
    this.fact = fact;
  }

  /**
   * Returns the unique, monotonically increasing recency identifier assigned when the fact was
   * inserted (starting at 1).
   *
   * @return the fact identifier
   */
  public long id() {
    return id;
  }

  /**
   * Returns the immutable fact associated with this handle.
   *
   * @return the fact
   */
  public Fact fact() {
    return fact;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof FactHandle that)) {
      return false;
    }
    return id == that.id;
  }

  @Override
  public int hashCode() {
    return Long.hashCode(id);
  }

  @Override
  public String toString() {
    return "FactHandle[" + id + ": " + fact.type() + "]";
  }
}
