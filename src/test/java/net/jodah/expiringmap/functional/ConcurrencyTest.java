package net.jodah.expiringmap.functional;

import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.testng.annotations.Test;

import net.jodah.concurrentunit.Waiter;
import net.jodah.expiringmap.ExpiringMap;
import net.jodah.expiringmap.Testing;

@Test(enabled = false)
public class ConcurrencyTest {
  /**
   * Should fail if a ConcurrentModificationException is thrown while performing concurrent put/remove operations.
   * 
   * See https://github.com/jhalterman/expiringmap/issues/29
   */
  public void shouldSupportConcurrentPutRemove() throws Throwable {
    ExpiringMap<Integer, Integer> map = ExpiringMap.builder().expiration(100, TimeUnit.MINUTES).build();
    Waiter waiter = new Waiter();
    AtomicInteger counter = new AtomicInteger();

    int threadCount = 50;
    Testing.threadedRun(threadCount, () -> {
      try {
        Random shouldPut = new Random();
        for (int i = 0; i < 1000 * 1000; i++) {
          if (shouldPut.nextBoolean()) {
            int value = counter.get();
            System.out.println("putting " + value);
            map.put(value, value);
          } else {
            int value = counter.get();
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

  /**
   * Should fail if when loader is asked to load a key more than once.
   * 
   * See https://github.com/jhalterman/expiringmap/issues/33
   */
  public void shouldSupportConcurrentLoad() throws Throwable {
    Waiter waiter = new Waiter();
    ConcurrentMap<Integer, Integer> loaded = new ConcurrentHashMap<>();
    ExpiringMap<Integer, Integer> map = ExpiringMap.builder()
        .expiration(100, TimeUnit.MINUTES)
        .entryLoader((Integer key) -> {
          waiter.assertFalse(loaded.containsKey(key));
          return key;
        })
        .build();

    int threadCount = 50;
    Testing.threadedRun(threadCount, () -> {
      for (int i = 0; i < 1000 * 1000; i++) {
        map.get(i);
        loaded.putIfAbsent(i, i);
      }
      waiter.resume();
    });

    waiter.await(100000, threadCount);
  }
}
