package net.jodah.expiringmap.functional;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.testng.annotations.Test;

import net.jodah.expiringmap.ExpiringMap;

/**
 * Tests ExpiringMap expiration behaviors.
 */
@Test
public class ExpirationTest {
  /**
   * Ensures that entry expiration is supported.
   */
  public void shouldExpireEntries() throws Exception {
    // Given
    ExpiringMap<String, String> map = ExpiringMap.builder().expiration(100, TimeUnit.MILLISECONDS).build();

    // When
    for (int i = 0; i < 100; i++)
      map.put("John" + i, "Joe");
    Thread.sleep(150);

    // Then
    assertTrue(map.isEmpty());
  }

  /**
   * Verifies that map entries expire.
   * 
   * @throws Exception
   */
  public void shouldExpireEntriesInOrder() throws Exception {
    // Given
    Map<String, String> map = ExpiringMap.builder().expiration(100, TimeUnit.MILLISECONDS).build();

    // When
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

    // Then
    assertTrue(map.isEmpty());
  }

  public void testPutShouldNotRescheduleWithCreatedPolicy() throws Throwable {
    final AtomicBoolean expired = new AtomicBoolean();
    ExpiringMap<String, String> map = ExpiringMap.builder()
        .expiration(180, TimeUnit.MILLISECONDS)
        .expirationListener((k, v) -> expired.set(true))
        .build();

    map.put("test", "test");
    Thread.sleep(100);
    map.put("test", "test");
    assertFalse(expired.get());
    Thread.sleep(100);

    assertTrue(expired.get());
  }
}
