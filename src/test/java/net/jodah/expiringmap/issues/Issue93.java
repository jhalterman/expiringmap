package net.jodah.expiringmap.issues;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.util.concurrent.TimeUnit;

import org.testng.annotations.Test;

import net.jodah.expiringmap.ExpirationPolicy;
import net.jodah.expiringmap.ExpiringMap;

@Test
public class Issue93 {
  private interface Condition {
    boolean test();
  }

  private static void awaitTrue(Condition condition, long timeoutMillis) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
    while (System.nanoTime() < deadline) {
      if (condition.test())
        return;
      Thread.sleep(10);
    }
    assertTrue(condition.test(), "condition not met within " + timeoutMillis + "ms");
  }

  public void testVeryLongExpirationDoesNotBlockShortExpirations() throws Exception {
    ExpiringMap<String, String> map = ExpiringMap.builder()
        .variableExpiration()
        .build();

    map.put("A", "alpha", ExpirationPolicy.CREATED, Long.MAX_VALUE, TimeUnit.NANOSECONDS);
    map.put("B", "bravo", ExpirationPolicy.CREATED, 200, TimeUnit.MILLISECONDS);
    map.put("C", "charlie", ExpirationPolicy.CREATED, 400, TimeUnit.MILLISECONDS);

    awaitTrue(new Condition() {
      public boolean test() {
        return map.get("B") == null;
      }
    }, 2000);
    assertEquals(map.get("A"), "alpha");

    awaitTrue(new Condition() {
      public boolean test() {
        return map.get("C") == null;
      }
    }, 2000);
    assertEquals(map.get("A"), "alpha");
  }
}
