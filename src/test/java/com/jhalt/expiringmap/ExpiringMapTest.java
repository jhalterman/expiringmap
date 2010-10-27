package com.jhalt.expiringmap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.concurrentunit.Examples;
import org.junit.Ignore;
import org.junit.Test;

import com.jhalt.expiringmap.ExpiringMap.ExpirationListener;
import com.jhalt.expiringmap.ExpiringMap.ExpirationPolicy;

/**
 * Tests {@link ExpiringMap}.
 */
public class ExpiringMapTest extends Examples {
  private static final int VALUE_PREFIX = 12345;
  private static final String KEY_PREFIX = "key prefix:";

  /**
   * Tests {@link ExpiringMap#create()}.
   */
  @Test
  public void testExpiringMapCreate() {
    assertNotNull(ExpiringMap.create());
  }

  /**
   * Tests {@link ExpiringMap#valuesIterator()}.
   */
  @Test
  public void testValuesIterator() {
    ExpiringMap<String, String> map = ExpiringMap.create();
    Iterator<String> valuesIterator = map.valuesIterator();
    assertFalse(valuesIterator.hasNext());

    String[] values = new String[] { "a", "b", "c", "d" };

    for (int i = 0; i < values.length; i++)
      map.put(values[i], values[i]);

    valuesIterator = map.valuesIterator();

    for (int i = 0; valuesIterator.hasNext(); i++)
      assertEquals(values[i], valuesIterator.next());
  }

  /**
   * Ensures that concurrent modification throws an exception.
   */
  @Test(expected = ConcurrentModificationException.class)
  public void testConcurrentModification() {
    ExpiringMap<String, String> map = ExpiringMap.create();
    map.put("a", "a");

    Iterator<String> valuesIterator = map.valuesIterator();
    valuesIterator.next();
    map.put("c", "c");
    valuesIterator.next();
  }

  /**
   * Tests {@link ExpiringMap#put(Object, Object)}.
   */
  @Test
  public void testPut() throws Exception {
    Map<String, Integer> map = ExpiringMap.builder().expiration(100, TimeUnit.MILLISECONDS).build();

    for (int i = 0; i < 10; i++) {
      map.put(KEY_PREFIX + i, VALUE_PREFIX + i);
      assertEquals(Integer.valueOf(VALUE_PREFIX + i), map.get(KEY_PREFIX + i));
    }

    Thread.sleep(150);

    assertTrue(map.isEmpty());
  }

  /**
   * Tests that a deadlock does not occur when concurrently reading/writing the same entries.
   */
  @Test
  public void deadlockShouldNotOccur() throws Throwable {
    final long duration = 1000;
    final String finalKey = "final";
    int threadCount = 5;

    final ExpiringMap<String, Long> map = ExpiringMap.builder()
        .expiration(duration, TimeUnit.MILLISECONDS)
        .expirationListener(new ExpirationListener<String, Long>() {
          public void expired(String key, Long startTime) {
            if (key.equals(finalKey))
              resume();
          }
        }).build();

    threadedRun(threadCount, new Runnable() {
      public void run() {
        try {
          Random random = new Random();

          for (int i = 0; i < 1000; i++) {
            map.put("key" + random.nextInt(5), System.currentTimeMillis());
            Thread.sleep(1);
          }
        } catch (Exception e) {
          threadFail(e);
        } finally {
          resume();
        }
      }
    });

    threadWait(10000, threadCount);
    map.put(finalKey, System.currentTimeMillis());
    threadWait(2000);
  }

  /**
   * Asserts that a remove on a map that is empty after the remove causes no side effect.
   */
  @Test
  public void testRemoveFromMapWithOneEntry() {
    Map<String, String> map = ExpiringMap.create();
    map.put("test", "test");
    map.remove("test");
    map = ExpiringMap.builder().variableExpiration().build();
    map.put("test", "test");
    map.remove("test");
  }

  /**
   * Performs 10000 puts in each of 50 threads across 10 maps and ensures that expiration times are
   * within 1/10s of expected times.
   */
  @Test
  @Ignore
  public void test50ThreadsAcross10MapsWith1SecondExpiration() throws Throwable {
    putTest(50, 10, 1000);
  }

  /**
   * Performs 10000 puts in each of 20 threads across 50 maps and ensures that expiration times are
   * within 1/10s of expected times.
   */
  @Test
  @Ignore
  public void test20ThreadsAcross50MapsWith1SecondExpiration() throws Throwable {
    putTest(20, 50, 1000);
  }

  /**
   * A test that performs 10000 puts in each of {@code threadCount} threads across {@code mapCount}
   * maps and asserts that expiration times are within 1/10th of a second of the expected times.
   * 
   * <p>
   * Note: Thread puts timeout at 60 seconds.
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  private void putTest(final int threadCount, final int mapCount, final long duration)
      throws Throwable {
    final String finalKey = "final";

    ExpirationListener<String, Long> expirationListener = new ExpirationListener<String, Long>() {
      public void expired(String key, Long startTime) {
        // Assert that expiration is within 1/10 second of expected time
        threadAssertTrue(System.currentTimeMillis() - (startTime + duration) < 100);

        if (key.equals(finalKey))
          resume();
      }
    };

    ExpiringMap.Builder builder = ExpiringMap.builder().expiration(duration, TimeUnit.MILLISECONDS)
        .expirationListener(expirationListener);
    final ExpiringMap[] maps = new ExpiringMap[mapCount];

    for (int i = 0; i < mapCount; i++)
      maps[i] = builder.build();

    threadedRun(threadCount, new Runnable() {
      public void run() {
        Random mapRandom = new Random();
        Random keyRandom = new Random();
        Random sleepRandom = new Random();

        try {
          for (int i = 0; i < 10000; i++) {
            maps[mapRandom.nextInt(mapCount)].put("key" + keyRandom.nextInt(1000),
                System.currentTimeMillis());
            Thread.sleep(sleepRandom.nextInt(2) + 1);
          }
        } catch (Exception e) {
          threadFail(e);
        } finally {
          resume();
        }
      }
    });

    threadWait(60000, threadCount);

    for (int i = 0; i < mapCount; i++)
      maps[i].put(finalKey, System.currentTimeMillis());

    threadWait(10000, mapCount);

    for (int i = 0; i < mapCount; i++)
      assertTrue(maps[i].isEmpty());
  }

  /**
   * Tests that an expiration listener is called as expected.
   */
  @Test
  public void expirationListenerShouldBeCalled() throws Throwable {
    final String key = "a";
    final String value = "v";

    Map<String, String> map = ExpiringMap.builder().expiration(100, TimeUnit.MILLISECONDS)
        .expirationListener(new ExpirationListener<String, String>() {
          public void expired(String thekey, String thevalue) {
            threadAssertEquals(key, thekey);
            threadAssertEquals(value, thevalue);
            resume();
          }
        }).build();

    map.put(key, value);

    threadWait(5000);
  }

  /**
   * Ensures that long running expiration listeners should be threaded.
   */
  @Test
  public void expirationListenerShouldBeThreaded() throws Throwable {
    final ExpiringMap<String, String> map = ExpiringMap.builder()
        .expiration(100, TimeUnit.MILLISECONDS).build();
    final Integer[] counter = new Integer[] { 0 };
    map.addExpirationListener(new ExpirationListener<String, String>() {
      public void expired(String thekey, String thevalue) {
        try {
          if (counter[0]++ > 0)
            threadAssertEquals(1, ExpiringMap.listenerService.getActiveCount());

          Thread.sleep(110);
        } catch (InterruptedException e) {
        }

        resume();
      }
    });

    map.put("a", "a");
    threadWait(500);
    map.put("a", "a");
    threadWait(2000);
  }

  /**
   * Ensures that short running expiration listeners should be threaded.
   */
  @Test
  public void expirationListenerShouldNotBeThreaded() throws Throwable {
    final ExpiringMap<String, String> map = ExpiringMap.builder()
        .expiration(100, TimeUnit.MILLISECONDS).build();
    final Integer[] counter = new Integer[] { 0 };
    map.addExpirationListener(new ExpirationListener<String, String>() {
      public void expired(String thekey, String thevalue) {
        if (counter[0]++ > 0)
          threadAssertEquals(0, ExpiringMap.listenerService.getActiveCount());

        resume();
      }
    });

    map.put("a", "a");
    threadWait(500);
    map.put("a", "a");
    threadWait(2000);
  }

  /**
   * Tests {@link ExpiringMap#removeExpirationListener(ExpirationListener)}.
   */
  @Test
  public void testRemoveExpirationListener() {
    ExpiringMap<String, String> map = ExpiringMap.create();
    ExpirationListener<String, String> listener1 = new ExpirationListener<String, String>() {
      public void expired(String key, String value) {
      }
    };

    ExpirationListener<String, String> listener2 = new ExpirationListener<String, String>() {
      public void expired(String key, String value) {
      }
    };

    map.addExpirationListener(listener1);
    map.addExpirationListener(listener2);

    assertEquals(2, map.expirationListeners.size());
    map.removeExpirationListener(listener2);
    assertEquals(listener1, map.expirationListeners.get(0).expirationListener);
    map.removeExpirationListener(listener1);
    assertTrue(map.expirationListeners.isEmpty());
  }

  /**
   * Tests {@link ExpiringMap#addExpirationListener(ExpirationListener)}.
   */
  @Test
  public void testAddExpirationListener() {
    ExpiringMap<String, String> map = ExpiringMap.create();
    ExpirationListener<String, String> listener1 = new ExpirationListener<String, String>() {
      public void expired(String key, String value) {
      }
    };

    ExpirationListener<String, String> listener2 = new ExpirationListener<String, String>() {
      public void expired(String key, String value) {
      }
    };

    map.addExpirationListener(listener1);
    map.addExpirationListener(listener2);
    assertEquals(listener1, map.expirationListeners.get(0).expirationListener);
    assertEquals(listener2, map.expirationListeners.get(1).expirationListener);
  }

  /**
   * Tests {@link ExpiringMap#get(Object)}.
   */
  @Test
  public void testGet() throws Exception {
    Map<String, Integer> map = ExpiringMap.builder().expiration(100, TimeUnit.MILLISECONDS).build();

    for (int i = 0; i < 10; i++)
      map.put(KEY_PREFIX + i, VALUE_PREFIX + i);

    for (int i = 0; i < 10; i++)
      assertEquals(Integer.valueOf(i + VALUE_PREFIX), map.get(KEY_PREFIX + i));

    Thread.sleep(150);

    for (int i = 0; i < 10; i++)
      assertEquals(null, map.get(KEY_PREFIX + i));
  }

  /**
   * Tests that {@link ExpiringMap#setExpiration(long, TimeUnit)} works as expected.
   */
  @Test
  public void testSetExpiration() throws Exception {
    ExpiringMap<String, String> map = ExpiringMap.builder().variableExpiration()
        .expiration(100, TimeUnit.MILLISECONDS).build();
    map.put("a", "a");
    Thread.sleep(150);
    assertTrue(map.isEmpty());
    map.setExpiration(500, TimeUnit.MILLISECONDS);
    map.put("a", "a");
    Thread.sleep(200);
    assertTrue(map.containsKey("a"));
    Thread.sleep(350);
    assertTrue(map.isEmpty());
  }

  /**
   * Tests that {@link ExpiringMap#setExpirationPolicy(ExpirationPolicy) works as expected.
   */
  @Test
  public void testSetExpirationPolicy() throws Exception {
    ExpiringMap<String, String> map = ExpiringMap.builder().variableExpiration()
        .expiration(100, TimeUnit.MILLISECONDS).build();
    map.put("a", "a");
    map.put("b", "b");
    map.put("c", "c");
    Thread.sleep(50);
    map.setExpirationPolicy(ExpirationPolicy.ACCESSED);
    map.put("d", "d");
    Thread.sleep(50);
    map.get("d");
    Thread.sleep(50);
    map.get("d");
    Thread.sleep(50);
    assertFalse(map.containsKey("a"));
    assertFalse(map.containsKey("b"));
    assertFalse(map.containsKey("c"));
    assertTrue(map.containsKey("d"));
    Thread.sleep(60);
    assertTrue(map.isEmpty());
  }

  /**
   * Tests that {@link ExpiringMap#setExpirationPolicy(Object, ExpirationPolicy) works as expected.
   */
  @Test
  public void testEntrySetExpirationPolicy() throws Exception {
    ExpiringMap<String, String> map = ExpiringMap.builder().variableExpiration()
        .expiration(100, TimeUnit.MILLISECONDS).build();
    map.put("a", "a");
    map.put("b", "b");
    map.put("c", "c");
    map.get("a");
    map.get("c");
    Thread.sleep(110);
    assertFalse(map.containsKey("a"));
    assertFalse(map.containsKey("b"));
    assertFalse(map.containsKey("c"));
    map.put("a", "a");
    map.put("b", "b");
    map.put("c", "c");
    map.setExpirationPolicy("b", ExpirationPolicy.ACCESSED);
    Thread.sleep(50);
    map.get("b");
    Thread.sleep(60);
    assertFalse(map.containsKey("a"));
    assertTrue(map.containsKey("b"));
    assertFalse(map.containsKey("c"));
    Thread.sleep(50);
    assertTrue(map.isEmpty());
  }

  /**
   * Tests that {@link ExpiringMap#setExpiration(Object, long, TimeUnit)} works as expected.
   */
  @Test
  public void testEntrySetExpiration() throws Exception {
    ExpiringMap<String, String> map = ExpiringMap.builder().variableExpiration().build();
    map.put("a", "a");
    map.put("b", "b");
    map.put("c", "c");
    map.put("d", "d");
    map.setExpiration("c", 100, TimeUnit.MILLISECONDS);
    Thread.sleep(120);
    assertTrue(map.containsKey("a"));
    assertTrue(map.containsKey("b"));
    assertFalse(map.containsKey("c"));
    assertTrue(map.containsKey("d"));
  }

  /**
   * Verifies that map entries expire.
   * 
   * @throws Exception
   */
  @Test
  public void entriesShouldExpireInOrder() throws Exception {
    Map<String, String> map = ExpiringMap.builder().expiration(100, TimeUnit.MILLISECONDS).build();
    map.put("John", "Doe");
    Thread.sleep(50);
    assertTrue(map.containsKey("John"));
    map.put("Moe", "Doe");
    Thread.sleep(70);
    assertFalse(map.containsKey("John"));
    assertTrue(map.containsKey("Moe"));
    map.put("Joe", "Doe");
    Thread.sleep(50);
    assertFalse(map.containsKey("Moe"));
    assertTrue(map.containsKey("Joe"));
    Thread.sleep(60);
    assertTrue(map.isEmpty());
  }

  /**
   * Ensures that entries are replaced as expected.
   * 
   * @throws Throwable
   */
  @Test
  public void entriesShouldBeReplaced() throws Throwable {
    final ExpiringMap<String, String> map = ExpiringMap.builder()
        .expiration(100, TimeUnit.MILLISECONDS).build();

    map.put("John", "Doe");
    Thread.sleep(50);
    map.put("John", "Moe");
    Thread.sleep(60);
    assertEquals("Moe", map.get("John"));
    Thread.sleep(50);
    assertTrue(map.isEmpty());
  }

  /**
   * Runs the {@code runnable} across {@code threadCount} threads.
   * 
   * @param threadCount
   * @param runnable
   */
  private void threadedRun(int threadCount, Runnable runnable) {
    Thread[] threads = new Thread[threadCount];

    for (int i = 0; i < threadCount; i++)
      threads[i] = new Thread(runnable);

    for (int i = 0; i < threadCount; i++)
      threads[i].start();
  }

  /**
   * Verifies that rescheduled map entries expire as expected.
   * 
   * @throws Exception
   */
  @Test
  public void rescheduledEntryShouldExpire() throws Exception {
    ExpiringMap<String, String> map = ExpiringMap.builder().expiration(100, TimeUnit.MILLISECONDS)
        .build();
    map.put("John", "Doe");
    assertTrue(map.containsKey("John"));
    map.put("John", "Joe");
    Thread.sleep(150);
    assertTrue(map.isEmpty());
  }

  /**
   * Ensures that bulk expiration is supported.
   * 
   * @throws Exception
   */
  @Test
  public void shouldBulkExpire() throws Exception {
    ExpiringMap<String, String> map = ExpiringMap.builder().expiration(100, TimeUnit.MILLISECONDS)
        .build();

    for (int i = 0; i < 100; i++)
      map.put("John" + i, "Joe");

    Thread.sleep(150);
    assertTrue(map.isEmpty());
  }
}
