package net.jodah.expiringmap.issues;

import java.util.concurrent.TimeUnit;

import org.testng.annotations.Test;

import net.jodah.expiringmap.ExpiringMap;

/**
 * https://github.com/jhalterman/expiringmap/issues/31
 */
@Test
public class Issue31 {
  public void shouldNotThrowException() {
    ExpiringMap<String, String> map = ExpiringMap.builder().variableExpiration().build();
    map.setExpiration("foo", 1, TimeUnit.SECONDS);
  }
}
