package com.pinnaclesmp.pinnacleafk;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AfkDismountTest {
    @Test
    void succeedsWhenNormalDismountDetachesPlayer() {
        AtomicBoolean mounted = new AtomicBoolean(true);
        AtomicInteger fallbackAttempts = new AtomicInteger();

        boolean dismounted = AfkDismount.attempt(
                mounted::get,
                () -> mounted.set(false),
                fallbackAttempts::incrementAndGet
        );

        assertTrue(dismounted);
        assertFalse(mounted.get());
        assertEquals(0, fallbackAttempts.get());
    }

    @Test
    void succeedsWhenFallbackDetachesAfterNormalAttemptFails() {
        AtomicBoolean mounted = new AtomicBoolean(true);
        AtomicInteger normalAttempts = new AtomicInteger();
        AtomicInteger fallbackAttempts = new AtomicInteger();

        boolean dismounted = AfkDismount.attempt(
                mounted::get,
                normalAttempts::incrementAndGet,
                () -> {
                    fallbackAttempts.incrementAndGet();
                    mounted.set(false);
                }
        );

        assertTrue(dismounted);
        assertFalse(mounted.get());
        assertEquals(1, normalAttempts.get());
        assertEquals(1, fallbackAttempts.get());
    }

    @Test
    void failsWhenNormalAndFallbackAttemptsLeavePlayerMounted() {
        AtomicBoolean mounted = new AtomicBoolean(true);
        AtomicInteger normalAttempts = new AtomicInteger();
        AtomicInteger fallbackAttempts = new AtomicInteger();

        boolean dismounted = AfkDismount.attempt(
                mounted::get,
                normalAttempts::incrementAndGet,
                fallbackAttempts::incrementAndGet
        );

        assertFalse(dismounted);
        assertTrue(mounted.get());
        assertEquals(1, normalAttempts.get());
        assertEquals(1, fallbackAttempts.get());
    }
}
