package net.jodah.expiringmap;

/**
 * Time source. Provides a time value representing nanoseconds elapsed since some arbitrary point in time.
 */
public abstract class Ticker {
    private static final Ticker SYSTEM_TICKER = new Ticker() {
        @Override
        public long time() {
            return System.nanoTime();
        }
    };

    /**
     * A ticker that reads the current time using {@link System#nanoTime()}
     */
    public static Ticker systemTicker() { return SYSTEM_TICKER; }

    /**
     * Constructor to be used by subclasses
     */
    protected Ticker() {}

    /**
     * Returns number of nanoseconds since this ticker's point of reference
     */
    public abstract long time();
}
