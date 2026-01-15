package net.jodah.expiringmap.issues;

import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.concurrent.TimeUnit;

import org.testng.annotations.Test;

import net.jodah.expiringmap.ExpiringMap;

@Test
public class Issue91 {
  public void testProbabilisticEarlyExpiration() throws Exception {
    ExpiringMap<String, String> map = ExpiringMap.builder()
        .expiration(200, TimeUnit.MILLISECONDS)
        .probabilisticExpiration(1.0, 1.0)
        .build();

    map.put("k", "v");
    Thread.sleep(10);

    assertNull(map.get("k"));
  }

  public void testExpirationJitterWithinBounds() {
    long baseMillis = 1000;
    ExpiringMap<String, String> map = ExpiringMap.builder()
        .expiration(baseMillis, TimeUnit.MILLISECONDS)
        .expirationJitter(0.2)
        .build();

    map.put("k", "v");
    long expected = map.getExpectedExpiration("k");

    assertTrue(expected <= 1200, "expected expiration should not exceed base + jitter");
    assertTrue(expected >= 700, "expected expiration should not be negative or too small");
  }
}
