package net.jodah.expiringmap.functional;

import static net.jodah.expiringmap.Testing.threadedRun;
import static org.testng.Assert.assertTrue;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.testng.annotations.Test;

import net.jodah.concurrentunit.ConcurrentTestCase;
import net.jodah.expiringmap.ExpirationListener;
import net.jodah.expiringmap.ExpiringMap;

public class ExpirationAccuracyTest extends ConcurrentTestCase {
  /**
   * Performs 10000 puts in each of 50 threads across 10 maps and ensures that expiration times are within 1/10s of
   * expected times.
   */
  @Test(enabled = false)
  public void test50ThreadsAcross10MapsWith1SecondExpiration() throws Throwable {
    putTest(50, 10, 1000);
  }

  /**
   * Performs 10000 puts in each of 20 threads across 50 maps and ensures that expiration times are within 1/10s of
   * expected times.
   */
  @Test(enabled = false)
  public void test20ThreadsAcross50MapsWith1SecondExpiration() throws Throwable {
    putTest(20, 50, 1000);
  }

  /**
   * A test that performs 10000 puts in each of {@code threadCount} threads across {@code mapCount} maps and asserts
   * that expiration times are within 1/10th of a second of the expected times.
   * 
   * <p>
   * Note: Thread puts timeout at 60 seconds.
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  private void putTest(final int threadCount, final int mapCount, final long duration) throws Throwable {
    final String finalKey = "final";

    ExpirationListener<String, Long> expirationListener = (key, startTime) -> {
      // Assert that expiration is within 1/10 second of expected time
      threadAssertTrue(System.currentTimeMillis() - (startTime + duration) < 100);
      if (key.equals(finalKey))
        resume();
    };

    ExpiringMap.Builder builder = ExpiringMap.builder()
        .expiration(duration, TimeUnit.MILLISECONDS)
        .expirationListener(expirationListener);
    final ExpiringMap[] maps = new ExpiringMap[mapCount];

    for (int i = 0; i < mapCount; i++)
      maps[i] = builder.build();

    threadedRun(threadCount, () -> {
      Random mapRandom = new Random();
      Random keyRandom = new Random();
      Random sleepRandom = new Random();

      try {
        for (int i = 0; i < 10000; i++) {
          maps[mapRandom.nextInt(mapCount)].put("key" + keyRandom.nextInt(1000), System.currentTimeMillis());
          Thread.sleep(sleepRandom.nextInt(2) + 1);
        }
      } catch (Exception e) {
        threadFail(e);
      } finally {
        resume();
      }
    });

    await(60000, threadCount);

    for (int i = 0; i < mapCount; i++)
      maps[i].put(finalKey, System.currentTimeMillis());

    await(10000, mapCount);

    for (int i = 0; i < mapCount; i++)
      assertTrue(maps[i].isEmpty());
  }
}
