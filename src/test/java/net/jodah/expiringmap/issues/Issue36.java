package net.jodah.expiringmap.issues;

import static org.testng.Assert.assertEquals;

import java.util.concurrent.TimeUnit;

import org.testng.annotations.Test;

import net.jodah.expiringmap.ExpirationPolicy;
import net.jodah.expiringmap.ExpiringMap;

@Test
public class Issue36 {
  public void testMap() {
    ExpiringMap<Long, String> map = ExpiringMap.builder()
        .expiration(10, TimeUnit.MINUTES)
        .expirationPolicy(ExpirationPolicy.ACCESSED)
        .variableExpiration()
        .build();

    map.put(1L, "1");
    map.put(2L, "2");
    map.put(3L, "3");
    assertEquals(map.entrySet().size(), 3);

    map.get(1L);
    assertEquals(map.entrySet().size(), 3);

    map.remove(1L);
    assertEquals(map.entrySet().size(), 2);
  }
}
