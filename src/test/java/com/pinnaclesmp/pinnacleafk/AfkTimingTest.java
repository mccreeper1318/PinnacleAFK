package com.pinnaclesmp.pinnacleafk;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AfkTimingTest {
    @Test
    void protectionDeadlineUsesElapsedNanoseconds() {
        long start = 5_000_000L;
        long deadline = AfkTiming.deadlineFrom(start, 10);

        assertFalse(AfkTiming.isDue(
                start + TimeUnit.SECONDS.toNanos(9),
                deadline
        ));
        assertTrue(AfkTiming.isDue(
                start + TimeUnit.SECONDS.toNanos(10),
                deadline
        ));
    }

    @Test
    void remainingCooldownRoundsUpAndExpires() {
        long start = 1_000L;

        assertEquals(3, AfkTiming.remainingWholeSeconds(start, start, 3));
        assertEquals(
                1,
                AfkTiming.remainingWholeSeconds(
                        start + TimeUnit.MILLISECONDS.toNanos(2_500L),
                        start,
                        3
                )
        );
        assertEquals(
                0,
                AfkTiming.remainingWholeSeconds(
                        start + TimeUnit.SECONDS.toNanos(3),
                        start,
                        3
                )
        );
    }

    @Test
    void missingDeadlineNeverBecomesDue() {
        assertFalse(AfkTiming.isDue(Long.MAX_VALUE, AfkTiming.NO_DEADLINE));
    }
}
