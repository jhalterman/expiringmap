package net.jodah.expiringmap.functional;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import net.jodah.concurrentunit.Waiter;
import net.jodah.expiringmap.ExpiringMap;

import static org.testng.Assert.assertEquals;

public class ExpirationListenerTest {
  private Waiter waiter;

  @BeforeMethod
  protected void beforeMethod() {
    waiter = new Waiter();
  }

  /**
   * Tests that an expiration listener is called as expected.
   */
  @Test(priority = 100)
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
   * Tests that an async expiration listener is called as expected.
   */
  @Test(priority = 100)
  public void shouldCallAsyncExpirationListener() throws Throwable {
    final String key = "a";
    final String value = "v";

    Map<String, String> map = ExpiringMap.builder()
        .expiration(100, TimeUnit.MILLISECONDS)
        .asyncExpirationListener((thekey, thevalue) -> {
          waiter.assertEquals(key, thekey);
          waiter.assertEquals(value, thevalue);
          waiter.resume();
        })
        .build();

    map.put(key, value);

    waiter.await(5000);
  }

    @Test(priority = 10)
    public void shouldCallListenerWhenAddedOnMap() throws Throwable {
        final String key = "a";
        final String value = "v";

        ExpiringMap<String, String> map = ExpiringMap.builder()
                .expiration(100, TimeUnit.MILLISECONDS)
                .build();

        map.addExpirationListener((thekey, thevalue) -> {
            waiter.assertEquals(key, thekey);
            waiter.assertEquals(value, thevalue);
            waiter.resume();
        });

        map.put(key, value);

        waiter.await(5000);
    }

    @Test(priority = 10)
    public void shouldCallAsyncListenerWhenAddedOnMap() throws Throwable {
        final String key = "a";
        final String value = "v";

        ExpiringMap<String, String> map = ExpiringMap.builder()
                .expiration(100, TimeUnit.MILLISECONDS)
                .build();

        map.addAsyncExpirationListener((thekey, thevalue) -> {
            waiter.assertEquals(key, thekey);
            waiter.assertEquals(value, thevalue);
            waiter.resume();
        });

        map.put(key, value);

        waiter.await(5000);
    }

    @Test(priority = 1)
    public void shouldExpireAllWhenListenerAddedOnMap() throws Throwable {
        CountDownLatch latch = new CountDownLatch(10);
        Set<Integer> expiredKeys = new HashSet<>();

        ExpiringMap<Integer, Integer> map = ExpiringMap.builder()
                .expiration(100, TimeUnit.MILLISECONDS)
                .build();

        map.addExpirationListener((thekey, thevalue) -> {
            expiredKeys.add(thekey);
            latch.countDown();
        });

        for (int i = 0; i < 10; i++) {
            map.put(i, i);
        }

        latch.await(5, TimeUnit.SECONDS);
        assertEquals(expiredKeys.size(), 10);
    }

    @Test(priority = 1)
    public void shouldExpireAllWhenAsyncListenerAddedOnMap() throws Throwable {
        CountDownLatch latch = new CountDownLatch(10);
        Set<Integer> expiredKeys = new HashSet<>();

        ExpiringMap<Integer, Integer> map = ExpiringMap.builder()
                .expiration(100, TimeUnit.MILLISECONDS)
                .build();

        map.addAsyncExpirationListener((thekey, thevalue) -> {
            expiredKeys.add(thekey);
            latch.countDown();
        });

        for (int i = 0; i < 10; i++) {
            map.put(i, i);
        }

        latch.await(5, TimeUnit.SECONDS);
        assertEquals(expiredKeys.size(), 10);
    }
}
