package net.jodah.expiringmap.functional;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.testng.annotations.Test;

import net.jodah.concurrentunit.Waiter;
import net.jodah.expiringmap.ExpiringMap;
import net.jodah.expiringmap.Testing;

@Test(enabled = false)
public class ConcurrencyTest {
  volatile int counter;

  /**
   * Should fail if an exception is thrown while performing concurrent put/remove operations.
   */
  public void shouldSupportConcurrentPutRemove() throws Throwable {
    ExpiringMap<Integer, Integer> map = ExpiringMap.builder().expiration(100, TimeUnit.MINUTES).build();
    Waiter waiter = new Waiter();

    int threadCount = 50;
    Testing.threadedRun(threadCount, () -> {
      try {
        Random shouldPut = new Random();
        for (int i = 0; i < 1000 * 1000; i++) {
          if (shouldPut.nextBoolean()) {
            int value = counter;
            System.out.println("putting " + value);
            map.put(value, value);
          } else {
            int value = counter;
            System.out.println("removing " + value + " resulted in " + map.remove(value));
          }
        }
      } catch (Exception e) {
        waiter.fail(e);
      }

      waiter.resume();
    });

    waiter.await(100000, threadCount);
  }
}
