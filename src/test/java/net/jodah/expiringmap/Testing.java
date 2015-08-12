package net.jodah.expiringmap;

public final class Testing {
  /**
   * Runs the {@code runnable} across {@code threadCount} threads.
   * 
   * @param threadCount
   * @param runnable
   */
  public static void threadedRun(int threadCount, Runnable runnable) {
    Thread[] threads = new Thread[threadCount];

    for (int i = 0; i < threadCount; i++)
      threads[i] = new Thread(runnable);

    for (int i = 0; i < threadCount; i++)
      threads[i].start();
  }
}
