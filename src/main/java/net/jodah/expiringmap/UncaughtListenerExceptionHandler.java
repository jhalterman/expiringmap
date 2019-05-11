package net.jodah.expiringmap;

/**
 * Fired when exception occurred during expiration listeners dispatch
 */
@FunctionalInterface
public interface UncaughtListenerExceptionHandler {

    /**
     * Called when exception in expiration listener occurred
     *
     * @param t exception fired
     */
    void exceptionCaught(Throwable t);
}
