package net.jodah.expiringmap;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class EvictionExpirationTest {
  @DataProvider
  public Object[][] expirationModes() {
    List<Object[]> modes = new ArrayList<Object[]>();
    for (boolean variable : new boolean[] { false, true })
      for (boolean async : new boolean[] { false, true })
        for (int maxSize : new int[] { 1, 2, 4 })
          modes.add(new Object[] { variable, async, maxSize });
    return modes.toArray(new Object[modes.size()][]);
  }

  @DataProvider
  public Object[][] listenerModes() {
    return new Object[][] { { false }, { true } };
  }

  @Test(dataProvider = "expirationModes")
  public void shouldNotifyEachMappingOnce(boolean variable, boolean async, int maxSize) throws Exception {
    String[] keys = { "a", "b", "c", "d" };
    ExpirationEvents events = new ExpirationEvents(keys.length,
        Arrays.copyOfRange(keys, keys.length - maxSize, keys.length));
    ExpiringMap.Builder<Object, Object> builder = ExpiringMap.builder()
        .maxSize(maxSize)
        .expiration(1, TimeUnit.SECONDS);
    if (variable)
      builder.variableExpiration();
    ExpiringMap<String, String> map = async ? builder.asyncExpirationListener(events).build()
        : builder.expirationListener(events).build();

    try {
      for (String key : keys) {
        map.put(key, key);
        events.retainedEntries.add(map.getEntry(key));
      }

      assertTrue(events.remainingExpired.await(5, TimeUnit.SECONDS), "Remaining entries must still expire");
      assertTrue(events.allDistinctMappings.await(5, TimeUnit.SECONDS), "All mappings must be reported");
      assertFalse(events.duplicate.await(100, TimeUnit.MILLISECONDS), "Each mapping must expire only once");
      assertEquals(events.counts.size(), keys.length);
      for (String key : keys)
        assertEquals(events.counts.get(key).get(), 1, "Expiration count for " + key);
      assertTrue(map.isEmpty());
    } finally {
      map.clear();
    }
  }

  @Test(dataProvider = "listenerModes")
  public void shouldKeepReinsertedValueAfterEvictedTaskDeadline(boolean async) throws Exception {
    ExpirationEvents events = new ExpirationEvents(2, "a", "b");
    ExpiringMap.Builder<Object, Object> builder = ExpiringMap.builder()
        .variableExpiration()
        .maxSize(1)
        .expiration(1, TimeUnit.SECONDS);
    ExpiringMap<String, String> map = async ? builder.asyncExpirationListener(events).build()
        : builder.expirationListener(events).build();
    CountDownLatch oldDeadlinePassed = new CountDownLatch(1);
    ExpiringMap<String, String> deadline = ExpiringMap.builder()
        .expiration(2, TimeUnit.SECONDS)
        .expirationListener((key, value) -> oldDeadlinePassed.countDown())
        .build();

    try {
      map.put("a", "old");
      events.retainedEntries.add(map.getEntry("a"));
      map.put("b", "other");
      events.retainedEntries.add(map.getEntry("b"));
      map.put("a", "replacement", 30, TimeUnit.SECONDS);
      deadline.put("deadline", "deadline");

      assertTrue(events.remainingExpired.await(5, TimeUnit.SECONDS), "Both evicted mappings must be reported");
      assertTrue(events.allDistinctMappings.await(5, TimeUnit.SECONDS), "Both old mappings must be reported");
      assertTrue(oldDeadlinePassed.await(5, TimeUnit.SECONDS), "The old entry's deadline must pass");
      assertEquals(map.get("a"), "replacement", "The evicted task must not remove a new mapping for the same key");
      assertEquals(events.counts.get("old").get(), 1);
      assertEquals(events.counts.get("other").get(), 1);
      assertFalse(events.counts.containsKey("replacement"));
    } finally {
      map.clear();
      deadline.clear();
    }
  }

  private static class ExpirationEvents implements ExpirationListener<String, String> {
    final ConcurrentHashMap<String, AtomicInteger> counts = new ConcurrentHashMap<String, AtomicInteger>();
    final CountDownLatch duplicate = new CountDownLatch(1);
    final CountDownLatch remainingExpired;
    final CountDownLatch allDistinctMappings;
    final Set<String> remaining;
    final Set<String> observed = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    // Keep evicted entries alive so their old weak-reference tasks cannot disappear during the regression.
    final List<ExpiringMap.ExpiringEntry<String, String>> retainedEntries =
        new ArrayList<ExpiringMap.ExpiringEntry<String, String>>();

    ExpirationEvents(int expectedMappings, String... remainingKeys) {
      remaining = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
      remaining.addAll(Arrays.asList(remainingKeys));
      remainingExpired = new CountDownLatch(remaining.size());
      allDistinctMappings = new CountDownLatch(expectedMappings);
    }

    @Override
    public void expired(String key, String value) {
      if (counts.computeIfAbsent(value, ignored -> new AtomicInteger()).incrementAndGet() == 1)
        allDistinctMappings.countDown();
      else
        duplicate.countDown();
      if (remaining.contains(key) && observed.add(key))
        remainingExpired.countDown();
    }
  }
}
