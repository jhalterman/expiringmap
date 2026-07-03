package net.jodah.expiringmap;

import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Expiring map entry implementation. */
class ExpiringEntry<K, V> implements Comparable<ExpiringEntry<K, V>> {
  final AtomicLong expirationNanos;
  /** Epoch time at which the entry is expected to expire */
  final AtomicLong expectedExpiration;
  final AtomicReference<ExpirationPolicy> expirationPolicy;
  final K key;
  /** Guarded by "this" */
  volatile Future<?> entryFuture;
  /** Guarded by "this" */
  V value;
  /** Guarded by "this" */
  volatile boolean scheduled;

  /**
   * Creates a new ExpiringEntry object.
   * 
   * @param key for the entry
   * @param value for the entry
   * @param expirationPolicy for the entry
   * @param expirationNanos for the entry
   */
  ExpiringEntry(K key, V value, AtomicReference<ExpirationPolicy> expirationPolicy, AtomicLong expirationNanos) {
    this.key = key;
    this.value = value;
    this.expirationPolicy = expirationPolicy;
    this.expirationNanos = expirationNanos;
    this.expectedExpiration = new AtomicLong();
    resetExpiration();
  }

  @Override
  public int compareTo(ExpiringEntry<K, V> other) {
    if (key.equals(other.key))
      return 0;
    return expectedExpiration.get() < other.expectedExpiration.get() ? -1 : 1;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((key == null) ? 0 : key.hashCode());
    result = prime * result + ((value == null) ? 0 : value.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    ExpiringEntry<?, ?> other = (ExpiringEntry<?, ?>) obj;
    if (!key.equals(other.key))
      return false;
    if (value == null) {
      if (other.value != null)
        return false;
    } else if (!value.equals(other.value))
      return false;
    return true;
  }

  @Override
  public String toString() {
    return value.toString();
  }

  /**
   * Marks the entry as canceled.
   * 
   * @return true if the entry was scheduled
   */
  synchronized boolean cancel() {
    boolean result = scheduled;
    if (entryFuture != null)
      entryFuture.cancel(false);

    entryFuture = null;
    scheduled = false;
    return result;
  }

  /** Gets the entry value. */
  synchronized V getValue() {
    return value;
  }

  /** Resets the entry's expected expiration. */
  void resetExpiration() {
    expectedExpiration.set(expirationNanos.get() + System.nanoTime());
  }

  /** Marks the entry as scheduled. */
  synchronized void schedule(Future<?> entryFuture) {
    this.entryFuture = entryFuture;
    scheduled = true;
  }

  /** Sets the entry value. */
  synchronized void setValue(V value) {
    this.value = value;
  }
}
