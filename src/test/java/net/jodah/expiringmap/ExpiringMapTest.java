package net.jodah.expiringmap;

import static net.jodah.expiringmap.Testing.threadedRun;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.Arrays;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.testng.annotations.Test;

import net.jodah.concurrentunit.ConcurrentTestCase;

/**
 * Tests {@link ExpiringMap}.
 */
@Test
public class ExpiringMapTest extends ConcurrentTestCase {
  private static final int VALUE_PREFIX = 12345;
  private static final String KEY_PREFIX = "key prefix:";

  /**
   * Tests {@link ExpiringMap#create()}.
   */
  public void testCreate() {
    assertNotNull(ExpiringMap.create());
  }

  /**
   * Tests {@link ExpiringMap#values()}.
   */
  public void testValues() {
    ExpiringMap<String, String> map = ExpiringMap.create();
    Collection<String> values = map.values();
    assertTrue(values.isEmpty());

    String[] fixtures = new String[] { "a", "b", "c", "d" };
    for (String fixture : fixtures)
      map.put(fixture, fixture);

    values = map.values();
    assertEquals(values, Arrays.asList(fixtures));
  }

  /**
   * Asserts that concurrent modification throws an exception.
   */
  @Test(expectedExceptions = ConcurrentModificationException.class)
  public void shouldThrowOnValuesConcurrentModification() {
    ExpiringMap<String, String> map = ExpiringMap.create();
    map.put("a", "a");

    Iterator<String> valuesIterator = map.values().iterator();
    valuesIterator.next();
    map.put("c", "c");
    valuesIterator.next();
  }

  /**
   * Tests {@link ExpiringMap#put(Object, Object)}. Asserts that values put in the map expire.
   */
  public void shouldExpirePutValues() throws Exception {
    Map<String, Integer> map = ExpiringMap.builder().expiration(100, TimeUnit.MILLISECONDS).build();

    for (int i = 0; i < 10; i++) {
      map.put(KEY_PREFIX + i, VALUE_PREFIX + i);
      assertEquals(Integer.valueOf(VALUE_PREFIX + i), map.get(KEY_PREFIX + i));
    }

    Thread.sleep(150);
    assertTrue(map.isEmpty());
  }

  /**
   * Asserts that putting an object with the same value does not result in a reschedule.
   */
  public void shouldNotReschedulePutsWithSameValue() throws Exception {
    Map<String, Integer> map = ExpiringMap.builder().expiration(100, TimeUnit.MILLISECONDS).build();

    map.put("foo", 1);
    Thread.sleep(55);
    map.put("foo", 1);
    Thread.sleep(55);

    assertNull(map.get("foo"));
  }

  /**
   * Tests that a deadlock does not occur when concurrently reading/writing the same entries.
   */
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
        })
        .build();

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

    await(10000, threadCount);
    map.put(finalKey, System.currentTimeMillis());
    await(2000);
  }

  /**
   * Asserts that a remove on a map that is empty after the remove causes no side effect.
   */
  public void testRemoveFromMapWithOneEntry() {
    Map<String, String> map = ExpiringMap.create();
    map.put("test", "test");
    map.remove("test");
    map = ExpiringMap.builder().variableExpiration().build();
    map.put("test", "test");
    map.remove("test");
  }

  public void shouldAllowNullValue() {
    ExpiringMap<String, String> map = ExpiringMap.create();
    map.put("test", "value1");
    map.put("test", null);
    map.put("test", "value2");
    assertEquals(map.get("test"), "value2");
  }

  /**
   * Tests that an expiration listener is called as expected.
   */
  public void expirationListenerShouldBeCalled() throws Throwable {
    final String key = "a";
    final String value = "v";

    Map<String, String> map = ExpiringMap.builder()
        .expiration(100, TimeUnit.MILLISECONDS)
        .expirationListener(new ExpirationListener<String, String>() {
          public void expired(String thekey, String thevalue) {
            threadAssertEquals(key, thekey);
            threadAssertEquals(value, thevalue);
            resume();
          }
        })
        .build();

    map.put(key, value);

    await(5000);
  }

  /**
   * Ensures that long running expiration listeners should be threaded.
   */
  public void expirationListenerShouldBeThreaded() throws Throwable {
    final ExpiringMap<String, String> map = ExpiringMap.builder().expiration(100, TimeUnit.MILLISECONDS).build();
    final Integer[] counter = new Integer[] { 0 };
    map.addExpirationListener(new ExpirationListener<String, String>() {
      public void expired(String thekey, String thevalue) {
        try {
          if (counter[0]++ > 0)
            threadAssertEquals(1, ExpiringMap.LISTENER_SERVICE.getActiveCount());

          Thread.sleep(110);
        } catch (InterruptedException e) {
        }

        resume();
      }
    });

    map.put("a", "a");
    await(500);
    map.put("a", "a");
    await(2000);
  }

  /**
   * Ensures that short running expiration listeners should be threaded.
   */
  public void expirationListenerShouldNotBeThreaded() throws Throwable {
    final ExpiringMap<String, String> map = ExpiringMap.builder().expiration(100, TimeUnit.MILLISECONDS).build();
    final Integer[] counter = new Integer[] { 0 };
    map.addExpirationListener(new ExpirationListener<String, String>() {
      public void expired(String thekey, String thevalue) {
        if (counter[0]++ > 0)
          threadAssertEquals(0, ExpiringMap.LISTENER_SERVICE.getActiveCount());

        resume();
      }
    });

    map.put("a", "a");
    await(500);
    map.put("a", "a");
    await(2000);
  }

  /**
   * Tests {@link ExpiringMap#removeExpirationListener(ExpirationListener)}.
   */
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
  public void testSetExpiration() throws Exception {
    ExpiringMap<String, String> map = ExpiringMap.builder()
        .variableExpiration()
        .expiration(100, TimeUnit.MILLISECONDS)
        .build();
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
  public void testSetExpirationPolicy() throws Exception {
    ExpiringMap<String, String> map = ExpiringMap.builder()
        .variableExpiration()
        .expiration(100, TimeUnit.MILLISECONDS)
        .build();
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
  public void testEntrySetExpirationPolicy() throws Exception {
    ExpiringMap<String, String> map = ExpiringMap.builder()
        .variableExpiration()
        .expiration(100, TimeUnit.MILLISECONDS)
        .build();
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
  public void shouldReplaceEntries() throws Throwable {
    final ExpiringMap<String, String> map = ExpiringMap.builder().expiration(100, TimeUnit.MILLISECONDS).build();

    map.put("John", "Doe");
    Thread.sleep(50);
    map.put("John", "Moe");
    Thread.sleep(60);
    assertEquals("Moe", map.get("John"));
    Thread.sleep(50);
    assertTrue(map.isEmpty());
  }

  /**
   * Verifies that rescheduled map entries expire as expected.
   */
  public void shouldExpireRescheduledEntry() throws Exception {
    ExpiringMap<String, String> map = ExpiringMap.builder().expiration(100, TimeUnit.MILLISECONDS).build();
    map.put("John", "Doe");
    assertTrue(map.containsKey("John"));
    map.put("John", "Joe");
    Thread.sleep(150);
    assertTrue(map.isEmpty());
  }

  /**
   * Ensures that bulk expiration is supported.
   */
  public void shouldExpireAllEntries() throws Exception {
    ExpiringMap<String, String> map = ExpiringMap.builder().expiration(100, TimeUnit.MILLISECONDS).build();

    for (int i = 0; i < 100; i++)
      map.put("John" + i, "Joe");

    Thread.sleep(150);
    assertTrue(map.isEmpty());
  }

  /**
   * Verifies that entries are loaded from an EntryLoader.
   */
  public void shouldLoadEntries() throws Exception {
    ExpiringMap<String, String> map = ExpiringMap.builder()
        .expiration(100, TimeUnit.MILLISECONDS)
        .entryLoader(new EntryLoader<String, String>() {
          int count;

          @Override
          public String load(String key) {
            return key + ++count;
          }
        })
        .build();

    assertEquals(map.get("foo"), "foo1");
    assertEquals(map.get("foo"), "foo1");
    assertEquals(map.get("bar"), "bar2");
    assertEquals(map.get("bar"), "bar2");
    map.remove("foo");
    assertEquals(map.get("foo"), "foo3");
    assertEquals(map.get("foo"), "foo3");
  }

  /**
   * Verifies that entries are loaded from an ExpiringEntryLoader.
   */
  public void shouldLoadExpiringEntries() throws Exception {
    ExpiringMap<String, String> map = ExpiringMap.builder()
        .expiringEntryLoader(new ExpiringEntryLoader<String, String>() {
          int count;

          @Override
          public ExpiringValue<String> load(String key) {
            return new ExpiringValue<String>(key + ++count, 100 * count, TimeUnit.MILLISECONDS);
          }
        })
        .build();

    assertEquals(map.get("foo"), "foo1");
    assertEquals(map.get("foo"), "foo1");
    assertEquals(map.getExpiration("foo"), 100);
    assertEquals(map.get("bar"), "bar2");
    assertEquals(map.get("bar"), "bar2");
    map.remove("foo");
    assertEquals(map.get("foo"), "foo3");
    assertEquals(map.get("foo"), "foo3");

    map.get("baz");
    assertEquals(map.getExpiration("baz"), 400);
  }

  public void expiringValueShouldUseMapDefaultsWhenUnspecified() throws Exception {
    long mapDefaultDuration = 100;
    ExpirationPolicy mapDefaultPolicy = ExpirationPolicy.CREATED;

    final ExpiringValue<String> useAllDefaults = new ExpiringValue<String>("useAllDefaults");
    final ExpiringValue<String> useDefaultPolicy = new ExpiringValue<String>("useDefaultPolicy", mapDefaultDuration * 2,
        TimeUnit.MILLISECONDS);
    final ExpiringValue<String> useDefaultDuration = new ExpiringValue<String>("useDefaultDuration",
        ExpirationPolicy.ACCESSED);
    final ExpiringValue<String> useNoDefaults = new ExpiringValue<String>("useNoDefaults", ExpirationPolicy.ACCESSED,
        mapDefaultDuration * 3, TimeUnit.MILLISECONDS);

    ExpiringMap<String, String> map = ExpiringMap.builder()
        .expirationPolicy(mapDefaultPolicy)
        .expiration(mapDefaultDuration, TimeUnit.MILLISECONDS)
        .expiringEntryLoader(new ExpiringEntryLoader<String, String>() {
          @Override
          public ExpiringValue<String> load(String key) {
            if (key.equals("useAllDefaults")) {
              return useAllDefaults;
            } else if (key.equals("useDefaultPolicy")) {
              return useDefaultPolicy;
            } else if (key.equals("useDefaultDuration")) {
              return useDefaultDuration;
            } else if (key.equals("useNoDefaults")) {
              return useNoDefaults;
            } else {
              throw new IllegalStateException("Unexpected get");
            }
          }
        })
        .build();

    map.get("useAllDefaults");
    assertEquals(map.getExpiration("useAllDefaults"), mapDefaultDuration);
    assertEquals(map.getExpirationPolicy("useAllDefaults"), mapDefaultPolicy);
    map.get("useDefaultPolicy");
    assertEquals(map.getExpiration("useDefaultPolicy"), useDefaultPolicy.getDuration());
    assertEquals(map.getExpirationPolicy("useAllDefaults"), mapDefaultPolicy);
    map.get("useDefaultDuration");
    assertEquals(map.getExpiration("useDefaultDuration"), mapDefaultDuration);
    assertNotEquals(useDefaultDuration.getExpirationPolicy(), mapDefaultDuration, "test validity check");
    assertEquals(map.getExpirationPolicy("useDefaultDuration"), useDefaultDuration.getExpirationPolicy());
    map.get("useNoDefaults");
    assertEquals(map.getExpiration("useNoDefaults"), useNoDefaults.getDuration());
    assertNotEquals(useNoDefaults.getExpirationPolicy(), mapDefaultDuration, "test validity check");
    assertEquals(map.getExpirationPolicy("useNoDefaults"), useNoDefaults.getExpirationPolicy());
  }

  public void loadNullExpiringValue() throws Exception {
    ExpiringMap<String, String> map = ExpiringMap.builder()
        .expiringEntryLoader(new ExpiringEntryLoader<String, String>() {
          @Override
          public ExpiringValue<String> load(String key) {
            return null;
          }
        })
        .build();

    assertNull(map.get("foo"));
  }

  /**
   * Tests {@link ExpiringMap#getExpectedExpiration(Object)}.
   */
  public void testExpectedExpiration() throws Exception {
    ExpiringMap<String, String> map = ExpiringMap.builder().expiration(100, TimeUnit.MILLISECONDS).build();

    map.put("key", "value");

    long exp = map.getExpectedExpiration("key");
    assertEquals(map.getExpiration("key"), 100);
    assertTrue(exp > 0 && exp < 100);

    Thread.sleep(130);

    try {
      exp = map.getExpectedExpiration("key");
      fail();
    } catch (NoSuchElementException expected) {
    }
  }

  public void shouldNotRescheduleWithCreatedPolicy() throws Throwable {
    final AtomicBoolean expired = new AtomicBoolean();
    ExpiringMap<String, String> map = ExpiringMap.builder()
        .expiration(180, TimeUnit.MILLISECONDS)
        .expirationListener(new ExpirationListener<String, String>() {
          @Override
          public void expired(String key, String value) {
            expired.set(true);
          }
        })
        .build();

    map.put("test", "test");
    Thread.sleep(100);
    map.put("test", "test");
    assertFalse(expired.get());
    Thread.sleep(100);

    assertTrue(expired.get());
  }

  @Test(expectedExceptions = IllegalStateException.class)
  public void behaviorWhenBothTypesOfLoadersProvided() {
    ExpiringMap.builder().entryLoader(new EntryLoader<Object, Object>() {
      @Override
      public Object load(Object key) {
        return null;
      }
    }).expiringEntryLoader(new ExpiringEntryLoader<Object, Object>() {
      @Override
      public ExpiringValue<Object> load(Object key) {
        return null;
      }
    });
  }

  public void testThreadFactory() throws Throwable {
    ExpiringMap.EXPIRER = null;
    ExpiringMap.LISTENER_SERVICE = null;
    
    ExpiringMap.setThreadFactory(Executors.defaultThreadFactory());
    ExpiringMap<String, String> map = ExpiringMap.builder().expiration(100, TimeUnit.MILLISECONDS).build();

    map.put("foo", "bar");

    Thread.sleep(150);
    assertTrue(map.isEmpty());
  }
}
