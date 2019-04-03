package net.jodah.expiringmap;

import net.jodah.expiringmap.internal.NamedThreadFactory;
import org.testng.annotations.Test;

import java.util.concurrent.TimeUnit;

import static org.testng.Assert.assertEquals;

/**
 * Tests ExpiringMap shutdown procedure.
 */
@Test
public class ShutdownTest {
  private static final String BASE_NAME = "TestThread-";

  private static int expirationThreads() {
    int n = 0;
    for (Thread t : Thread.getAllStackTraces().keySet())
      if (t.getName().startsWith(BASE_NAME))
        ++n;
    return n;
  }

  /**
   * Ensures that the service thread are properly shutdown.
   */
  public void shouldCloseThreads() throws Exception {
    // Initial cleanup
    ExpiringMap.setThreadFactory(new NamedThreadFactory(BASE_NAME + "%s"));
    ExpiringMap.shutdown();

    // Given
    ExpiringMap<String, String> map = ExpiringMap.builder().expiration(100, TimeUnit.MILLISECONDS).build();

    // When
    for (int i = 0; i < 100; i++)
      map.put("John" + i, "Joe");

    // Then
    assertEquals(expirationThreads(), 1);
    ExpiringMap.shutdown();
    Thread.sleep(100);
    assertEquals(expirationThreads(), 0);

    // Final cleanup
    ExpiringMap.THREAD_FACTORY = null;
  }
}
