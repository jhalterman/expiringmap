package net.jodah.expiringmap.internal;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Named thread factory.
 */
public class NamedThreadFactory implements ThreadFactory {
  private final AtomicInteger threadNumber = new AtomicInteger(1);
  private final String nameFormat;

  /**
   * Creates a thread factory that names threads according to the {@code nameFormat} by supplying a
   * single argument to the format representing the thread number.
   */
  public NamedThreadFactory(String nameFormat) {
    this.nameFormat = nameFormat;
  }

  @Override
  public Thread newThread(Runnable r) {
    Thread thread = new Thread(r, String.format(nameFormat, threadNumber.getAndIncrement()));
    thread.setDaemon(true);
    return thread;
  }
}
