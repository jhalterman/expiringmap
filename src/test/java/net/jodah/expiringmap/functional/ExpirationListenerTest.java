package net.jodah.expiringmap.functional;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import net.jodah.expiringmap.CustomValueTicker;
import net.jodah.expiringmap.internal.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import net.jodah.concurrentunit.Waiter;
import net.jodah.expiringmap.ExpiringMap;

import static org.testng.Assert.assertTrue;

@Test
public class ExpirationListenerTest {
  private Waiter waiter;

  @BeforeMethod
  protected void beforeMethod() {
    waiter = new Waiter();
  }

  /**
   * Tests that an expiration listener is called as expected.
   */
  public void shouldCallExpirationListener() throws Throwable {
    final String key = "a";
    final String value = "v";

    Map<String, String> map = ExpiringMap.builder()
        .expiration(100, TimeUnit.MILLISECONDS)
        .expirationListener((thekey, thevalue) -> {
          waiter.assertEquals(key, thekey);
          waiter.assertEquals(value, thevalue);
          waiter.resume();
        })
        .build();

    map.put(key, value);

    waiter.await(5000);
  }

  /**
   * Tests that an expiration listener is called as expected.
   * Custom ticker without sleeping needs some additional call to force expiration.
   */
  public void shouldCallExpirationListenerWithCustomTicker() throws Throwable {
    final String key = "a";
    final String value = "v";

    CustomValueTicker ticker = new CustomValueTicker();
    Map<String, String> map = ExpiringMap.builder()
        .expiration(100, TimeUnit.MILLISECONDS)
        .expirationListener((thekey, thevalue) -> {
          waiter.assertEquals(key, thekey);
          waiter.assertEquals(value, thevalue);
          waiter.resume();
        })
        .ticker(ticker)
        .build();

    map.put(key, value);

    ticker.setValue(150);
    assertTrue(map.isEmpty());

    waiter.await(10);
  }

  /**
   * Tests that an async expiration listener is called as expected.
   */
  public void shouldCallAsyncExpirationListener() throws Throwable {
    final String key = "a";
    final String value = "v";

    Map<String, String> map = ExpiringMap.builder()
        .expiration(100, TimeUnit.MILLISECONDS)
        .expirationListener((thekey, thevalue) -> {
          waiter.assertEquals(key, thekey);
          waiter.assertEquals(value, thevalue);
          waiter.resume();
        })
        .build();

    map.put(key, value);

    waiter.await(5000);
  }

  /**
   * Tests that an async expiration listener is called as expected.
   */
  public void shouldCallAsyncExpirationListenerWithCustomTicker() throws Throwable {
    final String key = "a";
    final String value = "v";

    CustomValueTicker ticker = new CustomValueTicker();
    Map<String, String> map = ExpiringMap.builder()
        .expiration(100, TimeUnit.MILLISECONDS)
        .expirationListener((thekey, thevalue) -> {
          waiter.assertEquals(key, thekey);
          waiter.assertEquals(value, thevalue);
          waiter.resume();
        })
        .ticker(ticker)
        .build();

    map.put(key, value);

    ticker.setValue(150);
    assertTrue(map.isEmpty());

    waiter.await(10);
  }
}
