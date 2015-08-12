package net.jodah.expiringmap;

import java.util.concurrent.TimeUnit;

/**
 * A value which should be stored in an {@link ExpiringMap} with optional control over its expiration.
 * @param <V> the type of value being stored
 */
public abstract class ExpiringValue<V> {
  private static final long UNSET_DURATION = -1L;

  private final V value;

  private final ExpiringMap.ExpirationPolicy expirationPolicy;

  private final long duration;

  private final TimeUnit timeUnit;

  /**
   * Creates an ExpiringValue to be stored in an {@link ExpiringMap}. The map's default values for
   * {@link ExpiringMap.ExpirationPolicy expiration policy} and
   * {@link ExpiringMap#getExpiration()} expiration} will be used.
   *
   * @param value the value to store
   * @see ExpiringMap#put(Object, Object)
   */
  public static <V> ExpiringValue<V> of(V value) {
    return new ExpiringValue<V>(value, null, UNSET_DURATION, null) {
      @Override
      <K> void put(ExpiringMap<K, ? super V> map, K key) {
        map.put(key, getValue());
      }
    };
  }

  /**
   * Creates an ExpiringValue to be stored in an {@link ExpiringMap}. The map's default
   * {@link ExpiringMap#getExpiration()} expiration} will be used.
   *
   * @param value the value to store
   * @param expirationPolicy the expiration policy for the value
   * @see ExpiringMap#put(Object, Object, ExpiringMap.ExpirationPolicy)
   */
  public static <V> ExpiringValue<V> of(V value, ExpiringMap.ExpirationPolicy expirationPolicy) {
    return new ExpiringValue<V>(value, expirationPolicy, UNSET_DURATION, null) {
      @Override
      <K> void put(ExpiringMap<K, ? super V> map, K key) {
        map.put(key, getValue(), getExpirationPolicy());
      }
    };
  }

  /**
   * Creates an ExpiringValue to be stored in an {@link ExpiringMap}. The map's default
   * {@link ExpiringMap.ExpirationPolicy expiration policy} will be used.
   *
   * @param value the value to store
   * @param duration the length of time after an entry is created that it should be removed
   * @param timeUnit the unit that {@code duration} is expressed in
   * @see ExpiringMap#put(Object, Object, long, TimeUnit)
   * @throws NullPointerException on null timeUnit
   */
  public static <V> ExpiringValue<V> of(V value, long duration, TimeUnit timeUnit) {
    if (timeUnit == null) {
      throw new NullPointerException();
    }
    return new ExpiringValue<V>(value, null, duration, timeUnit) {
      @Override
      <K> void put(ExpiringMap<K, ? super V> map, K key) {
        map.put(key, getValue(), getDuration(), getTimeUnit());
      }
    };
  }

  /**
   * Creates an ExpiringValue to be stored in an {@link ExpiringMap}.
   *
   * @param value the value to store
   * @param duration the length of time after an entry is created that it should be removed
   * @param timeUnit the unit that {@code duration} is expressed in
   * @param expirationPolicy the expiration policy for the value
   * @see ExpiringMap#put(Object, Object, ExpiringMap.ExpirationPolicy, long, TimeUnit)
   * @throws NullPointerException on null timeUnit
   */
  public static <V> ExpiringValue<V> of(V value, ExpiringMap.ExpirationPolicy expirationPolicy,
      long duration, TimeUnit timeUnit) {
    if (timeUnit == null) {
      throw new NullPointerException();
    }
    return new ExpiringValue<V>(value, expirationPolicy, duration, timeUnit) {
      @Override
      <K> void put(ExpiringMap<K, ? super V> map, K key) {
        map.put(key, getValue(), getExpirationPolicy(), getDuration(), getTimeUnit());
      }
    };
  }

  private ExpiringValue(V value, ExpiringMap.ExpirationPolicy expirationPolicy, long duration, TimeUnit timeUnit) {
    this.value = value;
    this.expirationPolicy = expirationPolicy;
    this.duration = duration;
    this.timeUnit = timeUnit;
  }

  abstract <K> void put(ExpiringMap<K, ? super V> map, K key);

  V getValue() {
    return value;
  }

  ExpiringMap.ExpirationPolicy getExpirationPolicy() {
    return expirationPolicy;
  }

  long getDuration() {
    return duration;
  }

  TimeUnit getTimeUnit() {
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
}
