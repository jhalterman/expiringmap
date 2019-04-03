package net.jodah.expiringmap;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import net.jodah.expiringmap.ExpiringMap;

import static org.testng.Assert.*;

/**
 * Tests ExpiringMap shutdown procedure.
 */
@Test
public class ShutdownTest {
  private static int expirationThreads() {
    int n = 0;
    for (Thread t : Thread.getAllStackTraces().keySet())
      if (t.getName().equals("ExpiringMap-Expirer"))
        ++n;
    return n;
  }

  /**
   * Ensures that the service thread are properly shutdown.
   */
  public void shouldCloseThreads() throws Exception {
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
  }
}
