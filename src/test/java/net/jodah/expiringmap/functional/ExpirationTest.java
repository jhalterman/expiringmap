package net.jodah.expiringmap.functional;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import net.jodah.expiringmap.CustomValueTicker;
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
    CustomValueTicker ticker = new CustomValueTicker();
    ExpiringMap<String, String> map = ExpiringMap.builder().expiration(100, TimeUnit.MILLISECONDS).ticker(ticker).build();

    // When
    for (int i = 0; i < 100; i++)
      map.put("John" + i, "Joe");
    ticker.setValue(150);

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
    CustomValueTicker ticker = new CustomValueTicker();
    Map<String, String> map = ExpiringMap.builder().expiration(100, TimeUnit.MILLISECONDS).ticker(ticker).build();

    // When
    map.put("John", "Doe");
    ticker.setValue(50);
    assertTrue(map.containsKey("John"));
    map.put("Moe", "Doe");
    ticker.setValue(120);
    assertFalse(map.containsKey("John"));
    assertTrue(map.containsKey("Moe"));
    map.put("Joe", "Doe");
    ticker.setValue(170);
    assertFalse(map.containsKey("Moe"));
    assertTrue(map.containsKey("Joe"));
    ticker.setValue(230);

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

  public void testPutShouldNotRescheduleWithCreatedPolicyWithCustomTicker() throws Throwable {
    final AtomicBoolean expired = new AtomicBoolean();
    CustomValueTicker ticker = new CustomValueTicker();
    ExpiringMap<String, String> map = ExpiringMap.builder()
            .expiration(180, TimeUnit.MILLISECONDS)
            .expirationListener((k, v) -> expired.set(true))
            .ticker(ticker)
            .build();

    map.put("test", "test");
    ticker.setValue(100);
    map.put("test", "test");
    assertFalse(expired.get());
    ticker.setValue(200);

    //only to push map to check expiration
    assertTrue(map.isEmpty());

    assertTrue(expired.get());
  }
}
