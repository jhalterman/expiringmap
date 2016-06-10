package net.jodah.expiringmap;

import java.util.concurrent.TimeUnit;

/**
 * Modifiable custom value ticker.
 * Returns set time value as current time after conversion from ms.
 * Starts with time equal to 0.
 */
public class CustomValueTicker extends Ticker {
  private long value = 0;

  public void setValue(long valueInMs) {
    this.value = TimeUnit.MILLISECONDS.toNanos(valueInMs);
  }

  @Override
  public long time() {
    return value;
  }
}
