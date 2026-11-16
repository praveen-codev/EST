package com.cognition.est.app.job;

/** Exit codes shared by the headless jobs an external scheduler runs. */
public final class JobExit {
    /** Everything ran (busy or skipped items are counted, not failures). */
    public static final int OK = 0;

    /** Unexpected error: configuration, connectivity or a bug. Nothing is reported but the type. */
    public static final int ERROR = 1;

    /** A safety precondition refused the run (wrong track, schema mismatch, read-only check). */
    public static final int REFUSED = 2;

    /** The run finished but at least one calendar identity failed. */
    public static final int PARTIAL = 3;

    /** Another run of the same job holds the lock; nothing was changed. */
    public static final int BUSY = 4;

    private JobExit() {}

    /** A safety precondition failed; the message must not contain row data or credentials. */
    public static final class Refused extends IllegalStateException {
        public Refused(String message) {
            super(message);
        }
    }
}
