package net.jodah.expiringmap;

import java.util.concurrent.TimeUnit;

public class ExpiringValue<V> {

  private final V value;

  private final ExpiringMap.ExpirationPolicy expirationPolicy;

  private final long duration;

  private final TimeUnit timeUnit;

  /**
   * Creates an ExpiringValue builder.
   *
   * @return New ExpiringValue builder
   */
  public static <V> Builder<V> builder() {
    return new Builder<V>();
  }

  public ExpiringValue(V value, ExpiringMap.ExpirationPolicy expirationPolicy, long duration, TimeUnit timeUnit) {
    this.value = value;
    this.expirationPolicy = expirationPolicy;
    this.duration = duration;
    this.timeUnit = timeUnit;
  }

  public V getValue() {
    return value;
  }

  public ExpiringMap.ExpirationPolicy getExpirationPolicy() {
    return expirationPolicy;
  }

  public long getDuration() {
    return duration;
  }

  public TimeUnit getTimeUnit() {
    return timeUnit;
  }

  @Override
  public int hashCode() {
    return value != null ? value.hashCode() : 0;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    ExpiringValue<?> that = (ExpiringValue<?>) o;
    return !(value != null ? !value.equals(that.value) : that.value != null)
        && expirationPolicy == that.expirationPolicy
        && duration == that.duration
        && timeUnit == that.timeUnit;

  }

  @Override
  public String toString() {
    return "ExpiringValue{" +
        "value=" + value +
        ", expirationPolicy=" + expirationPolicy +
        ", duration=" + duration +
        ", timeUnit=" + timeUnit +
        '}';
  }

  /**
   * Builds ExpiringValue instances. Defaults to ExpirationPolicy.CREATED and expiration of 60
   * TimeUnit.SECONDS.
   */
  public static class Builder<V> {
    private V value;
    private ExpiringMap.ExpirationPolicy expirationPolicy = ExpiringMap.ExpirationPolicy.CREATED;
    private long duration = 60;
    private TimeUnit timeUnit = TimeUnit.SECONDS;

    public Builder<V> value(V value) {
      this.value = value;
      return this;
    }

    public Builder<V> expirationPolicy(ExpiringMap.ExpirationPolicy expirationPolicy) {
      this.expirationPolicy = expirationPolicy;
      return this;
    }

    public Builder<V> duration(long duration) {
      this.duration = duration;
      return this;
    }

    public Builder<V> timeUnit(TimeUnit timeUnit) {
      this.timeUnit = timeUnit;
      return this;
    }

    public ExpiringValue<V> build() {
      return new ExpiringValue<V>(value, expirationPolicy, duration, timeUnit);
    }
  }
}
