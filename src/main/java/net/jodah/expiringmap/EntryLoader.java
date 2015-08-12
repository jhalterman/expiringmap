package net.jodah.expiringmap;

/**
 * Loads entries on demand.
 * 
 * @param <K> Key type
 * @param <V> Value type
 */
public interface EntryLoader<K, V> {
  /**
   * Called to load a new value for the {@code key} into an expiring map.
   * 
   * @param key to load a value for
   * @return new value to load
   */
  V load(K key);
}