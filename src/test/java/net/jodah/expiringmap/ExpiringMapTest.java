package net.jodah.expiringmap;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Tests {@link ExpiringMap}.
 * <p>
 * See the net.jodah.expiringmap.functional package for testing of specific features and scenarios.
 */
@Test
public class ExpiringMapTest {
  @BeforeMethod
  protected void beforeMethod() {
    ExpiringMap.THREAD_FACTORY = null;
  }

  /**
   * Tests {@link ExpiringMap#addAsyncExpirationListener(ExpirationListener)}.
   */
  public void testAddAndGetAsyncExpirationListener() {
    // Given
    ExpiringMap<String, String> map = ExpiringMap.create();
    ExpirationListener<String, String> listener1 = (k, v) -> {
    };
    ExpirationListener<String, String> listener2 = (k, v) -> {
    };

    // When
    map.addAsyncExpirationListener(listener1);
    map.addAsyncExpirationListener(listener2);

    // Then
    assertEquals(listener1, map.asyncExpirationListeners.get(0));
    assertEquals(listener2, map.asyncExpirationListeners.get(1));
  }

  /**
   * Tests {@link ExpiringMap#addExpirationListener(ExpirationListener)}.
   */
  public void testAddExpirationListener() {
    // Given
    ExpiringMap<String, String> map = ExpiringMap.create();
    ExpirationListener<String, String> listener1 = (k, v) -> {
    };
    ExpirationListener<String, String> listener2 = (k, v) -> {
    };

    // When
    map.addExpirationListener(listener1);
    map.addExpirationListener(listener2);

    // Then
    assertEquals(listener1, map.expirationListeners.get(0));
    assertEquals(listener2, map.expirationListeners.get(1));
  }

  /**
   * Tests {@link ExpiringMap#containsKey(Object)}
   */
  public void testContainsKey() {
    // Given
    ExpiringMap<String, String> map = ExpiringMap.create();
    map.put("a", "a");

    // When / Then
    assertTrue(map.containsKey("a"));
    assertFalse(map.containsKey("b"));
  }

  /**
   * Tests {@link ExpiringMap#containsValue(Object)}
   */
  public void testContainsValue() {
    // Given
    ExpiringMap<String, String> map = ExpiringMap.create();
    ExpiringMap<String, String> variableMap = ExpiringMap.builder().variableExpiration().build();

    // When
    map.put("a", "a");
    variableMap.put("a", "a");

    // Then
    assertTrue(map.containsValue("a"));
    assertFalse(map.containsValue("b"));
    assertTrue(variableMap.containsValue("a"));
    assertFalse(variableMap.containsValue("b"));
  }

  /**
   * Tests {@link ExpiringMap#create()}.
   */
  public void testCreate() {
    assertNotNull(ExpiringMap.create());
  }

  /**
   * Tests {@link ExpiringMap#entrySet()}.
   */
  public void testEntrySet() {
    ExpiringMap<String, String> map = ExpiringMap.create();
    Set<Map.Entry<String, String>> entrySet = map.entrySet();
    assertTrue(entrySet.isEmpty());

    String[] fixtures = new String[] { "a", "b", "c", "d" };
    for (String fixture : fixtures)
      map.put(fixture, fixture);

    List<String> fixturesList = Arrays.asList(fixtures);
    for (Map.Entry<String, String> entry : entrySet)
      assertTrue(fixturesList.contains(entry.getKey()));
  }

  /**
   * Tests {@link ExpiringMap#getExpectedExpiration(Object)}, ensuring that the expected expiration for an entry changes
   * when it's rescheduled.
   */
  public void testGetExpectedExpiration() throws Exception {
    // Given
    ExpiringMap<String, Integer> map = ExpiringMap.builder().expiration(100, TimeUnit.MILLISECONDS).build();

    // When / Then
    map.put("foo", 1);
    Thread.sleep(55);
    assertTrue(map.getExpectedExpiration("foo") < 50);
    map.put("foo", 2);
    assertTrue(map.getExpectedExpiration("foo") > 50);
  }

  /**
   * Tests {@link ExpiringMap#put(Object, Object)} and {@link ExpiringMap#get(Object)}.
   */
  public void testPutAndGet() throws Exception {
    Map<Integer, Integer> map = ExpiringMap.builder().expiration(100, TimeUnit.MILLISECONDS).build();

    for (int i = 0; i < 10; i++)
      map.put(i, i);

    for (int i = 0; i < 10; i++)
      assertEquals(Integer.valueOf(i), map.get(i));

    Thread.sleep(150);

    for (int i = 0; i < 10; i++)
      assertEquals(null, map.get(i));
  }

  @Test(expectedExceptions = NullPointerException.class)
  public void testPutWithNullKey() {
    ExpiringMap.create().put(null, "foo");
  }

  public void testPutWithNullValue() {
    // Given
    ExpiringMap<String, String> map = ExpiringMap.create();

    // When
    map.put("test", null);

    // Then
    assertEquals(map.get("test"), null);
  }

  /**
   * Asserts that putting an entry with an existing key reschedules its expiration.
   */
  public void testPutWithSameKey() throws Throwable {
    // Given
    ExpiringMap<String, String> map = ExpiringMap.builder().expiration(100, TimeUnit.MILLISECONDS).build();

    // When
    map.put("John", "Doe");
    Thread.sleep(50);
    map.put("John", "Moe");
    Thread.sleep(60);
    assertEquals(map.get("John"), "Moe");
    Thread.sleep(50);

    // Then
    assertTrue(map.isEmpty());
  }

  /**
   * Asserts that putting an entry with the same value does not result in a reschedule.
   */
  public void testPutWithSameValue() throws Exception {
    // Given
    Map<String, Integer> map = ExpiringMap.builder().expiration(100, TimeUnit.MILLISECONDS).build();

    // When
    map.put("foo", 1);
    Thread.sleep(55);
    map.put("foo", 1);
    Thread.sleep(55);

    // Then
    assertNull(map.get("foo"));
  }

  /**
   * Asserts that putting an entry beyond the max size results in the oldest one being expired.
   */
  public void testPutWithMaxSize() throws Exception {
    // Given
    Map<String, Integer> map = ExpiringMap.builder().maxSize(2).build();

    // When
    map.put("foo", 1);
    Thread.sleep(55);
    map.put("bar", 1);
    Thread.sleep(55);
    map.put("abc", 1);
    Thread.sleep(55);

    // Then
    assertNull(map.get("foo"));
    assertNotNull(map.get("bar"));
    assertNotNull(map.get("abc"));
  }

  /**
   * Tests {@link ExpiringMap#removeAsyncExpirationListener(ExpirationListener)}.
   */
  public void testRemoveAsyncExpirationListener() {
    // Given
    ExpiringMap<String, String> map = ExpiringMap.create();
    ExpirationListener<String, String> listener1 = (k, v) -> {
    };
    ExpirationListener<String, String> listener2 = (k, v) -> {
    };

    // When
    map.addAsyncExpirationListener(listener1);
    map.addAsyncExpirationListener(listener2);

    // Then
    assertEquals(2, map.asyncExpirationListeners.size());
    map.removeAsyncExpirationListener(listener2);
    assertEquals(listener1, map.asyncExpirationListeners.get(0));
    map.removeAsyncExpirationListener(listener1);
    assertTrue(map.asyncExpirationListeners.isEmpty());
  }

  /**
   * Tests {@link ExpiringMap#removeExpirationListener(ExpirationListener)}.
   */
  public void testRemoveExpirationListener() {
    // Given
    ExpiringMap<String, String> map = ExpiringMap.create();
    ExpirationListener<String, String> listener1 = (k, v) -> {
    };
    ExpirationListener<String, String> listener2 = (k, v) -> {
    };

    // When
    map.addExpirationListener(listener1);
    map.addExpirationListener(listener2);

    // Then
    assertEquals(2, map.expirationListeners.size());
    map.removeExpirationListener(listener2);
    assertEquals(listener1, map.expirationListeners.get(0));
    map.removeExpirationListener(listener1);
    assertTrue(map.expirationListeners.isEmpty());
  }

  /**
   * Asserts that a remove on a map that is empty after the remove causes no side effect.
   */
  public void testRemoveWithOneEntry() {
    Map<String, String> map = ExpiringMap.create();
    map.put("test", "test");
    map.remove("test");
    map = ExpiringMap.builder().variableExpiration().build();
    map.put("test", "test");
    map.remove("test");
  }

  /**
   * Tests that {@link ExpiringMap#setExpiration(Object, long, TimeUnit)} works as expected.
   */
  public void testSetEntryExpiration() throws Exception {
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
   * Tests that {@link ExpiringMap#setExpirationPolicy(Object, ExpirationPolicy) works as expected.
   */
  public void testSetEntryExpirationPolicy() throws Exception {
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

  public void testSetThreadFactory() throws Throwable {
    ExpiringMap.EXPIRER = null;
    ExpiringMap.LISTENER_SERVICE = null;

    ExpiringMap.setThreadFactory(Executors.defaultThreadFactory());
    ExpiringMap<String, String> map = ExpiringMap.builder().expiration(100, TimeUnit.MILLISECONDS).build();

    map.put("foo", "bar");

    Thread.sleep(150);
    assertTrue(map.isEmpty());
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

    assertEquals(values, Arrays.asList(fixtures));
    assertTrue(values.containsAll(Arrays.asList(fixtures)));
  }

  /**
   * Asserts that concurrent modification throws an exception.
   */
  @Test(expectedExceptions = ConcurrentModificationException.class)
  public void testValuesThrowsOnConcurrentModification() {
    ExpiringMap<String, String> map = ExpiringMap.create();
    map.put("a", "a");

    Iterator<String> valuesIterator = map.values().iterator();
    valuesIterator.next();
    map.put("c", "c");
    valuesIterator.next();
  }
}
