package net.jodah.expiringmap;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.SortedSet;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A thread-safe map that expires entries. Optional features include expiration policies, variable
 * entry settings, and expiration listeners.
 * 
 * <p>
 * Entries are tracked by expiration time and expired by a single static {@link Timer}.
 * 
 * <p>
 * Expiration listeners will automatically be assigned to run in the context of the Timer thread or
 * in a separate thread based on their first timed duration.
 * 
 * <p>
 * When variable expiration is disabled (default), put/remove operations are constant O(n). When
 * variable expiration is enabled, put/remove operations impose an <i>O(log n)</i> cost.
 * 
 * <p>
 * Example usages:
 * 
 * <pre> 
 * Map<String, Integer> map = ExpiringMap.create(); 
 * Map<String, Integer> map = ExpiringMap.builder().expiration(30, TimeUnit.SECONDS).build();
 * Map<String, Connection> map = ExpiringMap.builder()
 *   .expiration(10, TimeUnit.MINUTES)
 *   .entryLoader(new EntryLoader<String, Connection>() {
 *     public Connection load(String address) {
 *       return new Connection(address);
 *     }
 *   })
 *   .expirationListener(new ExpirationListener<String, Connection>() { 
 *     public void expired(String key, Connection connection) { 
 *       connection.close();
 *     } 
 *   })
 *   .build(); 
 * </pre>
 * 
 * @author Jonathan Halterman
 * @param <K> Key type
 * @param <V> Value type
 */
public class ExpiringMap<K, V> implements ConcurrentMap<K, V> {
  static final Timer timer = new Timer("ExpiringMap", true);
  static final ThreadPoolExecutor listenerService = NamedThreadFactory.decorate(
    (ThreadPoolExecutor) Executors.newCachedThreadPool(), "ExpiringMap");
  private static final long LISTENER_EXECUTION_THRESHOLD = 100;
  List<ExpirationListenerConfig<K, V>> expirationListeners;
  private AtomicLong expirationMillis;
  private final AtomicReference<ExpirationPolicy> expirationPolicy;
  private final EntryLoader<? super K, ? extends V> entryLoader;
  private final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
  private final Lock readLock = readWriteLock.readLock();
  private final Lock writeLock = readWriteLock.writeLock();
  /** Guarded by "readWriteLock" */
  private final EntryMap<K, V> entries;
  private final boolean variableExpiration;

  /**
   * Creates a new instance of ExpiringMap.
   * 
   * @param builder The map builder
   */
  private ExpiringMap(Builder<K, V> builder) {
    variableExpiration = builder.variableExpiration;
    entries = variableExpiration ? new EntryTreeHashMap<K, V>() : new EntryLinkedHashMap<K, V>();
    if (builder.expirationListeners != null)
      expirationListeners = new CopyOnWriteArrayList<ExpirationListenerConfig<K, V>>(
        builder.expirationListeners);
    expirationPolicy = new AtomicReference<ExpirationPolicy>(builder.expirationPolicy);
    expirationMillis = new AtomicLong(TimeUnit.MILLISECONDS.convert(builder.duration,
      builder.timeUnit));
    entryLoader = builder.entryLoader;
  }

  /**
   * Builds ExpiringMap instances. Defaults to ExpirationPolicy.CREATED and expiration of 60
   * TimeUnit.SECONDS.
   */
  public static final class Builder<K, V> {
    private ExpirationPolicy expirationPolicy = ExpirationPolicy.CREATED;
    private List<ExpirationListenerConfig<K, V>> expirationListeners;
    private TimeUnit timeUnit = TimeUnit.SECONDS;
    private boolean variableExpiration;
    private long duration = 60;
    private EntryLoader<K, V> entryLoader;

    /**
     * Creates a new Builder object.
     */
    private Builder() {
    }

    /**
     * Builds and returns an expiring map.
     * 
     * @param <K1> Key type
     * @param <V1> Value type
     */
    @SuppressWarnings("unchecked")
    public <K1 extends K, V1 extends V> ExpiringMap<K1, V1> build() {
      return new ExpiringMap<K1, V1>((Builder<K1, V1>) this);
    }

    /**
     * Sets the default map entry expiration.
     * 
     * @param duration the length of time after an entry is created that it should be removed
     * @param timeUnit the unit that {@code duration} is expressed in
     */
    public Builder<K, V> expiration(long duration, TimeUnit timeUnit) {
      this.duration = duration;
      this.timeUnit = timeUnit;
      return this;
    }

    /**
     * Sets the EntryLoader to use when loading entries.
     * 
     * @param listener to set
     */
    @SuppressWarnings("unchecked")
    public <K1 extends K, V1 extends V> Builder<K1, V1> entryLoader(
      EntryLoader<? super K1, ? super V1> loader) {
      entryLoader = (EntryLoader<K, V>) loader;
      return (Builder<K1, V1>) this;
    }

    /**
     * Sets the expiration listener which will receive notifications upon each map entry's
     * expiration.
     * 
     * @param listener to set
     */
    @SuppressWarnings("unchecked")
    public <K1 extends K, V1 extends V> Builder<K1, V1> expirationListener(
      ExpirationListener<? super K1, ? super V1> listener) {
      if (expirationListeners == null)
        expirationListeners = new ArrayList<ExpirationListenerConfig<K, V>>();
      expirationListeners.add(new ExpirationListenerConfig<K, V>(
        (ExpirationListener<K, V>) listener));
      return (Builder<K1, V1>) this;
    }

    /**
     * Sets expiration listeners which will receive notifications upon each map entry's expiration.
     * 
     * @param listeners to set
     */
    @SuppressWarnings("unchecked")
    public <K1 extends K, V1 extends V> Builder<K1, V1> expirationListeners(
      List<ExpirationListener<? super K1, ? super V1>> listeners) {
      if (expirationListeners == null)
        expirationListeners = new ArrayList<ExpirationListenerConfig<K, V>>(listeners.size());
      for (ExpirationListener<? super K1, ? super V1> listener : listeners)
        expirationListeners.add(new ExpirationListenerConfig<K, V>(
          (ExpirationListener<K, V>) listener));
      return (Builder<K1, V1>) this;
    }

    /**
     * Sets the map entry expiration policy.
     * 
     * @param expirationPolicy
     */
    public Builder<K, V> expirationPolicy(ExpirationPolicy expirationPolicy) {
      this.expirationPolicy = expirationPolicy;
      return this;
    }

    /**
     * Allows for map entries to have individual expirations and for expirations to be changed.
     */
    public Builder<K, V> variableExpiration() {
      variableExpiration = true;
      return this;
    }
  }

  /**
   * A listener for expired object events.
   * 
   * @param <K> Key type
   * @param <V> Value type
   */
  public interface ExpirationListener<K, V> {
    /**
     * Called when a map entry expires.
     * 
     * @param key Expired key
     * @param value Expired value
     */
    void expired(K key, V value);
  }

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

  /** Map entry expiration policy. */
  public enum ExpirationPolicy {
    /** Expires entries based on when they were last accessed */
    ACCESSED,
    /** Expires entries based on when they were created */
    CREATED;
  }

  /** Entry map definition. */
  interface EntryMap<K, V> extends Map<K, ExpiringEntry<K, V>> {
    /** Returns the first entry in the map or null if the map is empty. */
    ExpiringEntry<K, V> first();

    /**
     * Reorders the given entry in the map.
     * 
     * @param entry to reorder
     */
    void reorder(ExpiringEntry<K, V> entry);

    /** Returns a values iterator. */
    Iterator<ExpiringEntry<K, V>> valuesIterator();
  }

  /** Entry LinkedHashMap implementation. */
  static class EntryLinkedHashMap<K, V> extends LinkedHashMap<K, ExpiringEntry<K, V>> implements
    EntryMap<K, V> {
    private static final long serialVersionUID = 1L;

    @Override
    public ExpiringEntry<K, V> first() {
      return isEmpty() ? null : values().iterator().next();
    }

    @Override
    public void reorder(ExpiringEntry<K, V> value) {
      remove(value.key);
      put(value.key, value);
    }

    @Override
    public Iterator<ExpiringEntry<K, V>> valuesIterator() {
      return values().iterator();
    }
  }

  /** Entry TreeHashMap implementation. */
  static class EntryTreeHashMap<K, V> extends HashMap<K, ExpiringEntry<K, V>> implements
    EntryMap<K, V> {
    private static final long serialVersionUID = 1L;
    SortedSet<ExpiringEntry<K, V>> sortedSet = new TreeSet<ExpiringEntry<K, V>>();

    @Override
    public void clear() {
      super.clear();
      sortedSet.clear();
    }

    @Override
    public ExpiringEntry<K, V> first() {
      return sortedSet.isEmpty() ? null : sortedSet.first();
    }

    @Override
    public ExpiringEntry<K, V> put(K key, ExpiringEntry<K, V> value) {
      sortedSet.add(value);
      return super.put(key, value);
    }

    @Override
    public ExpiringEntry<K, V> remove(Object key) {
      ExpiringEntry<K, V> entry = super.remove(key);
      if (entry != null)
        sortedSet.remove(entry);

      return entry;
    }

    @Override
    public void reorder(ExpiringEntry<K, V> value) {
      sortedSet.remove(value);
      sortedSet.add(value);
    }

    @Override
    public Iterator<ExpiringEntry<K, V>> valuesIterator() {
      return new Iterator<ExpiringEntry<K, V>>() {
        private final Iterator<ExpiringEntry<K, V>> iterator = sortedSet.iterator();
        private ExpiringEntry<K, V> next;

        @Override
        public boolean hasNext() {
          return iterator.hasNext();
        }

        @Override
        public ExpiringEntry<K, V> next() {
          next = iterator.next();
          return next;
        }

        @Override
        public void remove() {
          EntryTreeHashMap.super.remove(next.key);
          iterator.remove();
        }
      };
    }
  }

  /** Provides an expiration listener configuration. */
  static class ExpirationListenerConfig<K, V> {
    final ExpirationListener<K, V> expirationListener;
    int executionPolicy = -1;

    /** Constructs a new ExpirationListenerConfig. */
    ExpirationListenerConfig(ExpirationListener<K, V> expirationListener) {
      this.expirationListener = expirationListener;
    }
  }

  /** Expiring map entry implementation. */
  static class ExpiringEntry<K, V> implements Comparable<ExpiringEntry<K, V>> {
    final AtomicLong expirationMillis;
    final AtomicReference<Date> expiration;
    final AtomicReference<ExpirationPolicy> expirationPolicy;
    final K key;
    /** Guarded by "this" */
    volatile TimerTask timerTask;
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
     * @param expirationMillis for the entry
     */
    ExpiringEntry(K key, V value, AtomicReference<ExpirationPolicy> expirationPolicy,
      AtomicLong expirationMillis) {
      this.key = key;
      this.value = value;
      this.expirationPolicy = expirationPolicy;
      this.expirationMillis = expirationMillis;
      this.expiration = new AtomicReference<Date>();
      resetExpiration();
    }

    @Override
    public int compareTo(ExpiringEntry<K, V> pOther) {
      if (key.equals(pOther.key))
        return 0;
      int result = expiration.get().compareTo(pOther.expiration.get());
      return result == 0 ? 1 : result;
    }

    @Override
    public boolean equals(Object pOther) {
      return key.equals(((ExpiringEntry<?, ?>) pOther).key);
    }

    @Override
    public int hashCode() {
      return key.hashCode();
    }

    /**
     * Marks the entry as canceled and resets the expiration if {@code resetExpiration} is true.
     * 
     * @param resetExpiration whether the entry's expiration should be reset
     * @return true if the entry was scheduled
     */
    synchronized boolean cancel(boolean resetExpiration) {
      boolean result = scheduled;
      if (timerTask != null)
        timerTask.cancel();

      timerTask = null;
      scheduled = false;

      if (resetExpiration)
        resetExpiration();
      return result;
    }

    /** Gets the entry value. */
    synchronized V getValue() {
      return value;
    }

    /** Resets the entry's expiration date. */
    void resetExpiration() {
      expiration.set(new Date(expirationMillis.get() + System.currentTimeMillis()));
    }

    /** Marks the entry as scheduled. */
    synchronized void schedule(TimerTask timerTask) {
      this.timerTask = timerTask;
      scheduled = true;
    }

    /** Sets the entry value. */
    synchronized void setValue(V value) {
      this.value = value;
    }
  }

  /**
   * Creates an ExpiringMap builder.
   * 
   * @return New ExpiringMap builder
   */
  public static Builder<Object, Object> builder() {
    return new Builder<Object, Object>();
  }

  /**
   * Creates a new instance of ExpiringMap with ExpirationPolicy.CREATED and expiration duration of
   * 60 TimeUnit.SECONDS.
   */
  @SuppressWarnings("unchecked")
  public static <K, V> ExpiringMap<K, V> create() {
    return new ExpiringMap<K, V>((Builder<K, V>) ExpiringMap.builder());
  }

  /**
   * Adds an expiration listener.
   * 
   * @param listener to add
   * @throws NullPointerException if listener is null
   */
  public void addExpirationListener(ExpirationListener<K, V> listener) {
    if (listener == null)
      throw new NullPointerException();
    if (expirationListeners == null)
      expirationListeners = new CopyOnWriteArrayList<ExpirationListenerConfig<K, V>>();
    expirationListeners.add(new ExpirationListenerConfig<K, V>(listener));
  }

  @Override
  public void clear() {
    writeLock.lock();
    try {
      for (ExpiringEntry<K, V> entry : entries.values())
        entry.cancel(false);
      entries.clear();
    } finally {
      writeLock.unlock();
    }
  }

  @Override
  public boolean containsKey(Object key) {
    readLock.lock();
    try {
      return entries.containsKey(key);
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public boolean containsValue(Object value) {
    readLock.lock();
    try {
      return entries.containsValue(value);
    } finally {
      readLock.unlock();
    }
  }

  /**
   * Not currently supported. Use this{@link #keySet()} and this{@link #entrySetIterable()} instead.
   * 
   * @throws UnsupportedOperationException
   */
  @Override
  public Set<Map.Entry<K, V>> entrySet() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean equals(Object obj) {
    readLock.lock();
    try {
      return entries.equals(obj);
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public V get(Object key) {
    ExpiringEntry<K, V> entry = null;

    readLock.lock();
    try {
      entry = entries.get(key);
    } finally {
      readLock.unlock();
    }

    if (entry == null) {
      if (entryLoader == null)
        return null;

      @SuppressWarnings("unchecked")
      K typedKey = (K) key;
      V value = entryLoader.load(typedKey);
      put(typedKey, value);
      return value;
    } else if (ExpirationPolicy.ACCESSED.equals(entry.expirationPolicy.get()))
      resetEntry(entry, false);

    return entry.getValue();
  }

  /**
   * Returns the map's default expiration duration in milliseconds.
   * 
   * @return The expiration duration (milliseconds)
   */
  public long getExpiration() {
    return expirationMillis.get();
  }

  /**
   * Gets the expiration duration in milliseconds for the entry corresponding to the given key.
   * 
   * @param key
   * @return The expiration duration in milliseconds
   * @throws NoSuchElementException If no entry exists for the given key
   */
  public long getExpiration(K key) {
    ExpiringEntry<K, V> entry = null;
    readLock.lock();
    try {
      entry = entries.get(key);
    } finally {
      readLock.unlock();
    }

    if (entry == null)
      throw new NoSuchElementException();

    return entry.expirationMillis.get();
  }

  @Override
  public int hashCode() {
    readLock.lock();
    try {
      return entries.hashCode();
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public boolean isEmpty() {
    readLock.lock();
    try {
      return entries.isEmpty();
    } finally {
      readLock.unlock();
    }
  }

  public Set<K> keySet() {
    readLock.lock();
    try {
      return entries.keySet();
    } finally {
      readLock.unlock();
    }
  }

  /**
   * Puts {@code value} in the map for {@code key}. Resets the entry's expiration unless an entry
   * already exists for the same {@code key} and {@code value}.
   * 
   * @param key to put value for
   * @param value to put for key
   * @return the old value
   * @throws NullPointerException on null key
   */
  @Override
  public V put(K key, V value) {
    if (key == null)
      throw new NullPointerException();

    return putInternal(key, value, expirationPolicy.get(), getExpiration());
  }

  /**
   * @see this{@link #put(Object, Object, ExpirationPolicy, long, TimeUnit)}
   */
  public V put(K key, V value, ExpirationPolicy expirationPolicy) {
    return put(key, value, expirationPolicy, expirationMillis.get(), TimeUnit.MILLISECONDS);
  }

  /**
   * Puts {@code value} in the map for {@code key}. Resets the entry's expiration unless an entry
   * already exists for the same {@code key} and {@code value}. Requires that variable expiration be
   * enabled.
   * 
   * @param key Key to put value for
   * @param value Value to put for key
   * @param duration the length of time after an entry is created that it should be removed
   * @param timeUnit the unit that {@code duration} is expressed in
   * @return the old value
   * @throws UnsupportedOperationException If variable expiration is not enabled
   * @throws NullPointerException on null key or timeUnit
   */
  public V put(K key, V value, ExpirationPolicy expirationPolicy, long duration, TimeUnit timeUnit) {
    if (!variableExpiration)
      throw new UnsupportedOperationException("Variable expiration is not enabled");

    if (key == null || timeUnit == null)
      throw new NullPointerException();

    return putInternal(key, value, expirationPolicy,
      TimeUnit.MILLISECONDS.convert(duration, timeUnit));
  }

  /**
   * @see this{@link #put(Object, Object, ExpirationPolicy, long, TimeUnit)}
   */
  public V put(K key, V value, long duration, TimeUnit timeUnit) {
    return put(key, value, expirationPolicy.get(), duration, timeUnit);
  }

  /** @see this{@link #put(Object, Object)}. */
  @Override
  public void putAll(Map<? extends K, ? extends V> map) {
    if (map == null)
      throw new NullPointerException();

    long expiration = getExpiration();
    ExpirationPolicy expirationPolicy = this.expirationPolicy.get();

    writeLock.lock();
    try {
      for (Map.Entry<? extends K, ? extends V> entry : map.entrySet())
        putInternal(entry.getKey(), entry.getValue(), expirationPolicy, expiration);
    } finally {
      writeLock.unlock();
    }
  }

  @Override
  public V putIfAbsent(K key, V value) {
    writeLock.lock();
    try {
      if (!entries.containsKey(key))
        return putInternal(key, value, expirationPolicy.get(), getExpiration());
      else
        return entries.get(key).getValue();
    } finally {
      writeLock.unlock();
    }
  }

  @Override
  public V remove(Object key) {
    ExpiringEntry<K, V> entry = null;

    writeLock.lock();
    try {
      entry = entries.remove(key);
    } finally {
      writeLock.unlock();
    }

    if (entry == null)
      return null;
    if (entry.cancel(false))
      scheduleEntry(entries.first());

    return entry.getValue();
  }

  @Override
  public boolean remove(Object key, Object value) {
    writeLock.lock();
    try {
      ExpiringEntry<K, V> entry = entries.get(key);
      if (entry != null && entry.getValue().equals(value)) {
        entries.remove(key);
        if (entry.cancel(false))
          scheduleEntry(entries.first());
        return true;
      } else
        return false;
    } finally {
      writeLock.unlock();
    }
  }

  @Override
  public V replace(K key, V value) {
    writeLock.lock();
    try {
      if (entries.containsKey(key)) {
        return putInternal(key, value, expirationPolicy.get(), getExpiration());
      } else
        return null;
    } finally {
      writeLock.unlock();
    }
  }

  @Override
  public boolean replace(K key, V oldValue, V newValue) {
    writeLock.lock();
    try {
      ExpiringEntry<K, V> entry = entries.get(key);
      if (entry != null && entry.getValue().equals(oldValue)) {
        putInternal(key, newValue, expirationPolicy.get(), getExpiration());
        return true;
      } else
        return false;
    } finally {
      writeLock.unlock();
    }
  }

  /**
   * Removes an expiration listener.
   * 
   * @param listener
   */
  public void removeExpirationListener(ExpirationListener<K, V> listener) {
    for (int i = 0; i < expirationListeners.size(); i++) {
      if (expirationListeners.get(i).expirationListener.equals(listener)) {
        expirationListeners.remove(i);
        return;
      }
    }
  }

  /**
   * Resets expiration for the entry corresponding to {@code key}.
   * 
   * @param key to reset expiration for
   */
  public void resetExpiration(K key) {
    ExpiringEntry<K, V> entry = null;

    readLock.lock();
    try {
      entry = entries.get(key);
    } finally {
      readLock.unlock();
    }

    if (entry != null)
      resetEntry(entry, false);
  }

  /**
   * Sets the expiration duration for the entry corresponding to the given key. Supported only if
   * variable expiration is enabled.
   * 
   * @param key Key to set expiration for
   * @param duration the length of time after an entry is created that it should be removed
   * @param timeUnit the unit that {@code duration} is expressed in
   * @throws UnsupportedOperationException If variable expiration is not enabled
   */
  public void setExpiration(K key, long duration, TimeUnit timeUnit) {
    if (!variableExpiration)
      throw new UnsupportedOperationException("Variable expiration is not enabled");

    writeLock.lock();
    try {
      ExpiringEntry<K, V> entry = entries.get(key);
      entry.expirationMillis.set(TimeUnit.MILLISECONDS.convert(duration, timeUnit));
      resetEntry(entry, true);
    } finally {
      writeLock.unlock();
    }
  }

  /**
   * Updates the default map entry expiration. Supported only if variable expiration is enabled.
   * 
   * @param duration the length of time after an entry is created that it should be removed
   * @param timeUnit the unit that {@code duration} is expressed in
   */
  public void setExpiration(long duration, TimeUnit timeUnit) {
    if (!variableExpiration)
      throw new UnsupportedOperationException("Variable expiration is not enabled");

    expirationMillis.set(TimeUnit.MILLISECONDS.convert(duration, timeUnit));
  }

  /**
   * Sets the global expiration policy for the map.
   * 
   * @param expirationPolicy
   */
  public void setExpirationPolicy(ExpirationPolicy expirationPolicy) {
    this.expirationPolicy.set(expirationPolicy);
  }

  /**
   * Sets the expiration policy for the entry corresponding to the given key.
   * 
   * @param key to set policy for
   * @param expirationPolicy to set
   * @throws UnsupportedOperationException If variable expiration is not enabled
   */
  public void setExpirationPolicy(K key, ExpirationPolicy expirationPolicy) {
    if (!variableExpiration)
      throw new UnsupportedOperationException("Variable expiration is not enabled");

    ExpiringEntry<K, V> entry = null;
    readLock.lock();
    try {
      entry = entries.get(key);
    } finally {
      readLock.unlock();
    }

    if (entry != null)
      entry.expirationPolicy.set(expirationPolicy);
  }

  @Override
  public int size() {
    readLock.lock();
    try {
      return entries.size();
    } finally {
      readLock.unlock();
    }
  }

  /**
   * Not currently supported. Use {@link #valuesIterator()} instead.
   * 
   * @throws UnsupportedOperationException
   */
  @Override
  public Collection<V> values() {
    throw new UnsupportedOperationException();
  }

  /**
   * Returns an iterator over the map values.
   * 
   * @throws ConcurrentModificationException if the map's size changes while iterating, excluding
   *           calls to {@link Iterator#remove()}.
   */
  public Iterator<V> valuesIterator() {
    return new Iterator<V>() {
      private final Iterator<ExpiringEntry<K, V>> iterator = entries.valuesIterator();

      /** {@inheritDoc} */
      public boolean hasNext() {
        return iterator.hasNext();
      }

      /** {@inheritDoc} */
      public V next() {
        return iterator.next().getValue();
      }

      /** {@inheritDoc} */
      public void remove() {
        iterator.remove();
      }
    };
  }

  /**
   * Notifies expiration listeners that the given entry expired. Utilizes an expiration policy to
   * invoke the listener. If the listener's initial execution exceeds LISTENER_EXECUTION_THRESHOLD
   * then the listener will be invoked within the context of {@code listenerService}, else it will
   * be invoked within the context of {@code timer}. Must not be called from within a locked
   * context.
   * 
   * @param entry Entry to expire
   */
  void notifyListeners(final ExpiringEntry<K, V> entry) {
    if (expirationListeners == null)
      return;

    for (final ExpirationListenerConfig<K, V> listener : expirationListeners) {
      if (listener.executionPolicy == 0)
        listener.expirationListener.expired(entry.key, entry.getValue());
      else if (listener.executionPolicy == 1)
        listenerService.execute(new Runnable() {
          public void run() {
            listener.expirationListener.expired(entry.key, entry.getValue());
          }
        });
      else {
        long startTime = System.currentTimeMillis();
        listener.expirationListener.expired(entry.key, entry.getValue());
        long endTime = System.currentTimeMillis();
        listener.executionPolicy = startTime + LISTENER_EXECUTION_THRESHOLD > endTime ? 0 : 1;
      }
    }
  }

  /**
   * Puts the given key/value in storage, scheduling the new entry for expiration if needed. If a
   * previous value existed for the given key, it is first cancelled and the entries reordered to
   * reflect the new expiration.
   */
  V putInternal(K key, V value, ExpirationPolicy expirationPolicy, long expirationMillis) {
    writeLock.lock();
    try {
      ExpiringEntry<K, V> entry = entries.get(key);
      V oldValue = null;

      if (entry == null) {
        entry = new ExpiringEntry<K, V>(key, value,
          variableExpiration ? new AtomicReference<ExpirationPolicy>(expirationPolicy)
            : this.expirationPolicy, variableExpiration ? new AtomicLong(expirationMillis)
            : this.expirationMillis);
        entries.put(key, entry);
        if (entries.size() == 1 || entries.first().equals(entry))
          scheduleEntry(entry);
      } else {
        oldValue = entry.getValue();
        if ((oldValue == null && value == null) || (oldValue != null && oldValue.equals(value)))
          return value;

        entry.setValue(value);
        resetEntry(entry, false);
      }

      return oldValue;
    } finally {
      writeLock.unlock();
    }
  }

  /**
   * Resets the given entry's schedule canceling any existing scheduled expiration and reordering
   * the entry in the internal map. Schedules the next entry in the map if the given {@code entry}
   * was scheduled or if {@code scheduleNext} is true.
   * 
   * @param entry to reset
   * @param scheduleFirstEntry whether the first entry should be automatically scheduled
   */
  void resetEntry(ExpiringEntry<K, V> entry, boolean scheduleFirstEntry) {
    writeLock.lock();
    try {
      boolean scheduled = entry.cancel(true);
      entries.reorder(entry);

      if (scheduled || scheduleFirstEntry)
        scheduleEntry(entries.first());
    } finally {
      writeLock.unlock();
    }
  }

  /**
   * Schedules an entry for expiration. Guards against concurrent schedule/schedule, cancel/schedule
   * and schedule/cancel calls.
   * 
   * @param entry Entry to schedule
   */
  void scheduleEntry(ExpiringEntry<K, V> entry) {
    if (entry == null || entry.scheduled)
      return;

    TimerTask timerTask = null;
    synchronized (entry) {
      if (entry.scheduled)
        return;

      final WeakReference<ExpiringEntry<K, V>> entryReference = new WeakReference<ExpiringEntry<K, V>>(
        entry);
      timerTask = new TimerTask() {
        @Override
        public void run() {
          ExpiringEntry<K, V> entry = entryReference.get();

          writeLock.lock();
          try {
            if (entry != null && entry.scheduled) {
              entries.remove(entry.key);
              notifyListeners(entry);
            }

            try {
              // Expires entries and schedules the next entry
              Iterator<ExpiringEntry<K, V>> iterator = entries.valuesIterator();
              boolean schedulePending = true;

              while (iterator.hasNext() && schedulePending) {
                ExpiringEntry<K, V> nextEntry = iterator.next();
                if (nextEntry.expiration.get().getTime() <= System.currentTimeMillis()) {
                  iterator.remove();
                  notifyListeners(nextEntry);
                } else {
                  scheduleEntry(nextEntry);
                  schedulePending = false;
                }
              }
            } catch (NoSuchElementException ignored) {
            }
          } finally {
            writeLock.unlock();
          }
        }
      };

      entry.schedule(timerTask);
    }

    timer.schedule(timerTask, entry.expiration.get());
  }
}
