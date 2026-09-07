package com.pinnaclesmp.pinnacleafk;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AfkStateBindingTest {
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
    void exactStateInstanceIsRequired() {
        Map<UUID, Object> states = new HashMap<>();
        Object stateA = new String("state");
        Object equalButDifferentState = new String("state");
        states.put(PLAYER_ID, stateA);

        assertTrue(AfkStateBinding.isCurrent(states, PLAYER_ID, stateA));
        assertFalse(AfkStateBinding.isCurrent(states, PLAYER_ID, equalButDifferentState));
    }

    @Test
    void successfulCorrectionCannotContinueAfterStateReplacement() {
        Map<UUID, Object> states = new HashMap<>();
        Map<UUID, AfkCorrectionTeleport> pending = new HashMap<>();
        Object stateA = new Object();
        Object stateB = new Object();
        states.put(PLAYER_ID, stateA);

        AfkCorrectionAttempt.Result attempt = AfkCorrectionAttempt.run(
                pending,
                PLAYER_ID,
                EXPECTED,
                () -> {
                    states.put(PLAYER_ID, stateB);
                    return true;
                },
                () -> true
        );

        assertTrue(attempt.succeeded());
        assertFalse(AfkStateBinding.mayContinueAfterCorrection(
                states,
                PLAYER_ID,
                stateA,
                attempt
        ));
        assertSame(stateB, states.get(PLAYER_ID));
        assertTrue(pending.isEmpty());
    }

    @Test
    void failedStaleCorrectionCannotClearReplacementState() {
        Map<UUID, Object> states = new HashMap<>();
        Map<UUID, AfkCorrectionTeleport> pending = new HashMap<>();
        Object stateA = new Object();
        Object stateB = new Object();
        states.put(PLAYER_ID, stateA);

        AfkCorrectionAttempt.Result attempt = AfkCorrectionAttempt.run(
                pending,
                PLAYER_ID,
                EXPECTED,
                () -> {
                    states.put(PLAYER_ID, stateB);
                    return false;
                },
                () -> true
        );

        assertFalse(attempt.succeeded());
        assertFalse(AfkStateBinding.shouldClearAfterFailedCorrection(
                states,
                PLAYER_ID,
                stateA,
                attempt
        ));
        assertSame(stateB, states.get(PLAYER_ID));
        assertTrue(pending.isEmpty());
    }

    @Test
    void failedCurrentCorrectionStillFailsClosed() {
        Map<UUID, Object> states = new HashMap<>();
        Map<UUID, AfkCorrectionTeleport> pending = new HashMap<>();
        Object stateA = new Object();
        states.put(PLAYER_ID, stateA);

        AfkCorrectionAttempt.Result attempt = AfkCorrectionAttempt.run(
                pending,
                PLAYER_ID,
                EXPECTED,
                () -> false,
                () -> true
        );

        assertFalse(attempt.succeeded());
        assertTrue(AfkStateBinding.shouldClearAfterFailedCorrection(
                states,
                PLAYER_ID,
                stateA,
                attempt
        ));
        assertTrue(pending.isEmpty());
    }

    @Test
    void removedStateCannotContinueAfterSuccessfulCorrection() {
        Map<UUID, Object> states = new HashMap<>();
        Map<UUID, AfkCorrectionTeleport> pending = new HashMap<>();
        Object stateA = new Object();
        states.put(PLAYER_ID, stateA);

        AfkCorrectionAttempt.Result attempt = AfkCorrectionAttempt.run(
                pending,
                PLAYER_ID,
                EXPECTED,
                () -> {
                    states.remove(PLAYER_ID);
                    return true;
                },
                () -> true
        );

        assertTrue(attempt.succeeded());
        assertFalse(AfkStateBinding.mayContinueAfterCorrection(
                states,
                PLAYER_ID,
                stateA,
                attempt
        ));
        assertFalse(AfkStateBinding.isCurrent(states, PLAYER_ID, stateA));
        assertTrue(pending.isEmpty());
    }
}
