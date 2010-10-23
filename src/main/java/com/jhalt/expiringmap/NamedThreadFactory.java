package com.jhalt.expiringmap;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Decorates a thread factory with named threads.
 * 
 * @author Jonathan Halterman
 */
public class NamedThreadFactory implements ThreadFactory {
    private final String name;
    private final ThreadFactory threadFactory;

    /**
     * Creates a new NamedThreadFactory object.
     * 
     * @param threadFactory Factory to decorate
     * @param name Name
     */
    public NamedThreadFactory(ThreadFactory threadFactory, String name) {
        this.threadFactory = threadFactory;
        this.name = name + " ";
    }

    /**
     * Decorates the given thread pool executor with a named thread factory.
     * 
     * @param executor Executor to decorate.
     * @return The decorated executor
     */
    public static ThreadPoolExecutor decorate(ThreadPoolExecutor executor, String pName) {
        executor.setThreadFactory(new NamedThreadFactory(executor.getThreadFactory(), pName));
        return executor;
    }

    /**
     * {@inheritDoc}
     */
    public Thread newThread(Runnable runnable) {
        Thread thread = threadFactory.newThread(runnable);
        thread.setName(name + thread.getName());
        return thread;
    }
}
