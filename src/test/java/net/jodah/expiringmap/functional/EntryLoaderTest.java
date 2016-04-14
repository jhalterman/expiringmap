package net.jodah.expiringmap.functional;

import static org.testng.Assert.assertEquals;

import java.util.concurrent.TimeUnit;

import org.testng.annotations.Test;

import net.jodah.expiringmap.EntryLoader;
import net.jodah.expiringmap.ExpiringMap;

@Test
public class EntryLoaderTest {
  /**
   * Verifies that entries are loaded from an EntryLoader.
   */
  public void shouldLoadEntries() throws Exception {
    ExpiringMap<String, String> map = ExpiringMap.builder()
        .expiration(100, TimeUnit.MILLISECONDS)
        .entryLoader(new EntryLoader<String, String>() {
          int count;

          @Override
          public String load(String key) {
            return key + ++count;
          }
        })
        .build();

    assertEquals(map.get("foo"), "foo1");
    assertEquals(map.get("foo"), "foo1");
    assertEquals(map.get("bar"), "bar2");
    assertEquals(map.get("bar"), "bar2");
    map.remove("foo");
    assertEquals(map.get("foo"), "foo3");
    assertEquals(map.get("foo"), "foo3");
  }
}
