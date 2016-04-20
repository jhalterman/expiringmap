package net.jodah.expiringmap.functional;

import static net.jodah.expiringmap.Testing.threadedRun;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import net.jodah.concurrentunit.Waiter;
import net.jodah.expiringmap.ExpiringMap;

@Test
public class DeadlockTest {
  private Waiter waiter;

  @BeforeMethod
  protected void beforeMethod() {
    waiter = new Waiter();
  }

  /**
   * Tests that a deadlock does not occur when concurrently reading/writing the same entries.
   */
  public void shouldNotDeadlock() throws Throwable {
    final long duration = 1000;
    final String finalKey = "final";
    int threadCount = 5;

    final ExpiringMap<String, Long> map = ExpiringMap.builder()
        .expiration(duration, TimeUnit.MILLISECONDS)
        .expirationListener((key, value) -> {
          if (key.equals(finalKey))
            waiter.resume();
        })
        .build();

    threadedRun(threadCount, () -> {
      try {
        Random random = new Random();
        for (int i = 0; i < 1000; i++) {
          map.put("key" + random.nextInt(5), System.currentTimeMillis());
          Thread.sleep(1);
        }
      } catch (Exception e) {
        waiter.fail(e);
      } finally {
        waiter.resume();
      }
    });

    waiter.await(10000, threadCount);
    map.put(finalKey, System.currentTimeMillis());
    waiter.await(2000);
  }
}
