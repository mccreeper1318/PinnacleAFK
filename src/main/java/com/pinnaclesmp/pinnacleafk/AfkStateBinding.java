package com.pinnaclesmp.pinnacleafk;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

final class AfkStateBinding {
    private AfkStateBinding() {
    }

    static <T> boolean isCurrent(
            Map<UUID, T> states,
            UUID playerId,
            T expectedState
    ) {
        Objects.requireNonNull(states, "states");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(expectedState, "expectedState");
        return states.get(playerId) == expectedState;
    }

    static <T> boolean mayContinueAfterCorrection(
            Map<UUID, T> states,
            UUID playerId,
            T expectedState,
            AfkCorrectionAttempt.Result attempt
    ) {
        Objects.requireNonNull(attempt, "attempt");
        return attempt.succeeded() && isCurrent(states, playerId, expectedState);
    }

    static <T> boolean shouldClearAfterFailedCorrection(
            Map<UUID, T> states,
            UUID playerId,
            T expectedState,
            AfkCorrectionAttempt.Result attempt
    ) {
        Objects.requireNonNull(attempt, "attempt");
        return !attempt.succeeded() && isCurrent(states, playerId, expectedState);
    }

    static boolean shouldClearReplacementAfterRedirectedCorrection(
            AfkCorrectionAttempt.Result attempt,
            boolean replacementMatchesFinalLocation
    ) {
        Objects.requireNonNull(attempt, "attempt");
        return !attempt.succeeded()
                && attempt.teleported()
                && !attempt.destinationMatches()
                && !replacementMatchesFinalLocation;
    }
}
