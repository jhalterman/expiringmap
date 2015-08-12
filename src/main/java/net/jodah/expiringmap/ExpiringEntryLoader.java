package net.jodah.expiringmap;

/**
 * Loads entries on demand, with control over each value's expiry duration (i.e. variable expiration).
 *
 * @param <K> Key type
 * @param <V> Value type
 */
public interface ExpiringEntryLoader<K, V> {
  /**
   * Called to load a new value for the {@code key} into an expiring map.
   *
   * @param key to load a value for
   * @return contains new value to load along with its expiry duration
   */
  ExpiringValue<V> load(K key);
}