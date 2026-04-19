package dev.httptape.testcontainers;

/**
 * Controls SSE (Server-Sent Events) replay timing in the httptape container.
 *
 * <p>Three modes:
 * <ul>
 *   <li>{@link #REALTIME} -- replay at original recorded timing</li>
 *   <li>{@link #INSTANT} -- replay all events immediately</li>
 *   <li>{@link #accelerated(double)} -- replay at a multiplied speed</li>
 * </ul>
 *
 * <p>This is a sealed interface with three record implementations rather than
 * an enum because {@link Accelerated} requires per-instance state (the factor).
 */
public sealed interface SseTimingMode permits
        SseTimingMode.Realtime,
        SseTimingMode.Instant,
        SseTimingMode.Accelerated {

    /** Replay at original recorded timing. */
    SseTimingMode REALTIME = new Realtime();

    /** Replay all events immediately with no delay. */
    SseTimingMode INSTANT = new Instant();

    /**
     * Creates an accelerated timing mode that replays at the given speed multiplier.
     *
     * @param factor acceleration factor (e.g., 2.0 = twice as fast)
     * @return an accelerated timing mode
     * @throws IllegalArgumentException if factor is not positive
     */
    static SseTimingMode accelerated(double factor) {
        if (factor <= 0) {
            throw new IllegalArgumentException("Acceleration factor must be positive, got: " + factor);
        }
        return new Accelerated(factor);
    }

    /**
     * Returns the CLI flag value for {@code --sse-timing}.
     *
     * @return the flag string (e.g., "realtime", "instant", "accelerated=2.5")
     */
    String toCliFlag();

    /** Replay at original recorded timing. */
    record Realtime() implements SseTimingMode {
        @Override
        public String toCliFlag() {
            return "realtime";
        }
    }

    /** Replay all events immediately with no delay. */
    record Instant() implements SseTimingMode {
        @Override
        public String toCliFlag() {
            return "instant";
        }
    }

    /**
     * Replay at an accelerated speed.
     *
     * @param factor the acceleration multiplier (must be positive)
     */
    record Accelerated(double factor) implements SseTimingMode {
        @Override
        public String toCliFlag() {
            return "accelerated=" + factor;
        }
    }
}
