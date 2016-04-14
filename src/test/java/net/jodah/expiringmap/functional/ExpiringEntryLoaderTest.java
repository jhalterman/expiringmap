package net.jodah.expiringmap.functional;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNull;

import java.util.concurrent.TimeUnit;

import org.testng.annotations.Test;

import net.jodah.expiringmap.ExpirationPolicy;
import net.jodah.expiringmap.ExpiringEntryLoader;
import net.jodah.expiringmap.ExpiringMap;
import net.jodah.expiringmap.ExpiringValue;

@Test
public class ExpiringEntryLoaderTest {
  /**
   * Verifies that entries are loaded from an ExpiringEntryLoader.
   */
  public void shouldLoadExpiringEntries() throws Exception {
    ExpiringMap<String, String> map = ExpiringMap.builder()
        .expiringEntryLoader(new ExpiringEntryLoader<String, String>() {
          int count;

          @Override
          public ExpiringValue<String> load(String key) {
            return new ExpiringValue<String>(key + ++count, 100 * count, TimeUnit.MILLISECONDS);
          }
        })
        .build();

    assertEquals(map.get("foo"), "foo1");
    assertEquals(map.get("foo"), "foo1");
    assertEquals(map.getExpiration("foo"), 100);
    assertEquals(map.get("bar"), "bar2");
    assertEquals(map.get("bar"), "bar2");
    map.remove("foo");
    assertEquals(map.get("foo"), "foo3");
    assertEquals(map.get("foo"), "foo3");

    map.get("baz");
    assertEquals(map.getExpiration("baz"), 400);
  }

  public void expiringValueShouldUseMapDefaultsWhenUnspecified() throws Exception {
    long mapDefaultDuration = 100;
    ExpirationPolicy mapDefaultPolicy = ExpirationPolicy.CREATED;

    final ExpiringValue<String> useAllDefaults = new ExpiringValue<String>("useAllDefaults");
    final ExpiringValue<String> useDefaultPolicy = new ExpiringValue<String>("useDefaultPolicy", mapDefaultDuration * 2,
        TimeUnit.MILLISECONDS);
    final ExpiringValue<String> useDefaultDuration = new ExpiringValue<String>("useDefaultDuration",
        ExpirationPolicy.ACCESSED);
    final ExpiringValue<String> useNoDefaults = new ExpiringValue<String>("useNoDefaults", ExpirationPolicy.ACCESSED,
        mapDefaultDuration * 3, TimeUnit.MILLISECONDS);

    ExpiringMap<String, String> map = ExpiringMap.builder()
        .expirationPolicy(mapDefaultPolicy)
        .expiration(mapDefaultDuration, TimeUnit.MILLISECONDS)
        .expiringEntryLoader(key -> {
          if (key.equals("useAllDefaults")) {
            return useAllDefaults;
          } else if (key.equals("useDefaultPolicy")) {
            return useDefaultPolicy;
          } else if (key.equals("useDefaultDuration")) {
            return useDefaultDuration;
          } else if (key.equals("useNoDefaults")) {
            return useNoDefaults;
          } else {
            throw new IllegalStateException("Unexpected get");
          }
        })
        .build();

    map.get("useAllDefaults");
    assertEquals(map.getExpiration("useAllDefaults"), mapDefaultDuration);
    assertEquals(map.getExpirationPolicy("useAllDefaults"), mapDefaultPolicy);
    map.get("useDefaultPolicy");
    assertEquals(map.getExpiration("useDefaultPolicy"), useDefaultPolicy.getDuration());
    assertEquals(map.getExpirationPolicy("useAllDefaults"), mapDefaultPolicy);
    map.get("useDefaultDuration");
    assertEquals(map.getExpiration("useDefaultDuration"), mapDefaultDuration);
    assertNotEquals(useDefaultDuration.getExpirationPolicy(), mapDefaultDuration, "test validity check");
    assertEquals(map.getExpirationPolicy("useDefaultDuration"), useDefaultDuration.getExpirationPolicy());
    map.get("useNoDefaults");
    assertEquals(map.getExpiration("useNoDefaults"), useNoDefaults.getDuration());
    assertNotEquals(useNoDefaults.getExpirationPolicy(), mapDefaultDuration, "test validity check");
    assertEquals(map.getExpirationPolicy("useNoDefaults"), useNoDefaults.getExpirationPolicy());
  }

  @Test(expectedExceptions = IllegalStateException.class)
  public void behaviorWhenBothTypesOfLoadersProvided() {
    ExpiringMap.builder().entryLoader(key -> null).expiringEntryLoader(key -> null);
  }

  public void loadNullExpiringValue() throws Exception {
    ExpiringMap<String, String> map = ExpiringMap.builder().expiringEntryLoader(key -> null).build();

    assertNull(map.get("foo"));
  }
}
