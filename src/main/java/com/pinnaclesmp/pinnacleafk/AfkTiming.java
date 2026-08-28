package com.pinnaclesmp.pinnacleafk;

import java.util.concurrent.TimeUnit;

final class AfkTiming {
    static final long NO_DEADLINE = -1L;

    private AfkTiming() {
    }

    static long deadlineFrom(long startNanos, int seconds) {
        return startNanos + TimeUnit.SECONDS.toNanos(seconds);
    }

    static boolean isDue(long nowNanos, long deadlineNanos) {
        return deadlineNanos != NO_DEADLINE && nowNanos - deadlineNanos >= 0L;
    }

    static int remainingWholeSeconds(long nowNanos, long startedNanos, int durationSeconds) {
        if (durationSeconds <= 0) {
            return 0;
        }

        long durationNanos = TimeUnit.SECONDS.toNanos(durationSeconds);
        long remainingNanos = durationNanos - (nowNanos - startedNanos);
        if (remainingNanos <= 0L) {
            return 0;
        }

        long secondNanos = TimeUnit.SECONDS.toNanos(1L);
        return (int) Math.max(
                1L,
                Math.min(
                        Integer.MAX_VALUE,
                        (remainingNanos + secondNanos - 1L) / secondNanos
                )
        );
    }
}
