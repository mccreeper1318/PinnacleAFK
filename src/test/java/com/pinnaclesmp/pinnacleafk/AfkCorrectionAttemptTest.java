package com.pinnaclesmp.pinnacleafk;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AfkCorrectionAttemptTest {
    private static final UUID PLAYER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID WORLD_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final AfkCorrectionTeleport EXPECTED = new AfkCorrectionTeleport(
            WORLD_ID,
            12.5D,
            64.0D,
            -8.25D,
            135.0F,
            -22.5F
    );

    @Test
    void successfulCorrectionChecksFinalDestinationAndCleansPendingState() {
        Map<UUID, AfkCorrectionTeleport> pending = new HashMap<>();

        AfkCorrectionAttempt.Result result = AfkCorrectionAttempt.run(
                pending,
                PLAYER_ID,
                EXPECTED,
                () -> {
                    assertSame(EXPECTED, pending.get(PLAYER_ID));
                    return true;
                },
                () -> true
        );

        assertTrue(result.succeeded());
        assertTrue(result.teleported());
        assertTrue(result.destinationMatches());
        assertTrue(pending.isEmpty());
    }

    @Test
    void cancelledOrFailedCorrectionDoesNotCheckDestinationAndCleansPendingState() {
        Map<UUID, AfkCorrectionTeleport> pending = new HashMap<>();
        AtomicBoolean destinationChecked = new AtomicBoolean(false);

        AfkCorrectionAttempt.Result result = AfkCorrectionAttempt.run(
                pending,
                PLAYER_ID,
                EXPECTED,
                () -> false,
                () -> {
                    destinationChecked.set(true);
                    return true;
                }
        );

        assertFalse(result.succeeded());
        assertFalse(result.teleported());
        assertFalse(destinationChecked.get());
        assertTrue(pending.isEmpty());
    }

    @Test
    void thrownCorrectionFailureIsReportedAndCleansPendingState() {
        Map<UUID, AfkCorrectionTeleport> pending = new HashMap<>();
        RuntimeException failure = new IllegalStateException("teleport failed");

        AfkCorrectionAttempt.Result result = AfkCorrectionAttempt.run(
                pending,
                PLAYER_ID,
                EXPECTED,
                () -> {
                    throw failure;
                },
                () -> true
        );

        assertFalse(result.succeeded());
        assertSame(failure, result.failure());
        assertTrue(pending.isEmpty());
    }

    @Test
    void mismatchedFinalDestinationFailsAndCleansPendingState() {
        Map<UUID, AfkCorrectionTeleport> pending = new HashMap<>();

        AfkCorrectionAttempt.Result result = AfkCorrectionAttempt.run(
                pending,
                PLAYER_ID,
                EXPECTED,
                () -> true,
                () -> false
        );

        assertFalse(result.succeeded());
        assertTrue(result.teleported());
        assertFalse(result.destinationMatches());
        assertTrue(pending.isEmpty());
    }

    @Test
    void nestedCorrectionDoesNotOverwriteExistingPendingCorrection() {
        Map<UUID, AfkCorrectionTeleport> pending = new HashMap<>();
        AfkCorrectionTeleport existing = new AfkCorrectionTeleport(
                WORLD_ID,
                1.0D,
                2.0D,
                3.0D,
                4.0F,
                5.0F
        );
        pending.put(PLAYER_ID, existing);

        AfkCorrectionAttempt.Result result = AfkCorrectionAttempt.run(
                pending,
                PLAYER_ID,
                EXPECTED,
                () -> true,
                () -> true
        );

        assertFalse(result.succeeded());
        assertTrue(result.failure() instanceof IllegalStateException);
        assertSame(existing, pending.get(PLAYER_ID));
    }
}
