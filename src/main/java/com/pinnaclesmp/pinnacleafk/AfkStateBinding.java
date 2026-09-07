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
        if (attempt.succeeded()) {
            return false;
        }

        if (isCurrent(states, playerId, expectedState)) {
            return true;
        }

        // A stale correction normally must not clear a replacement AFK session. The
        // exception is a teleport that actually completed but missed the authorized
        // destination: PlayerChangedWorld may have deferred cleanup while the initiating
        // correction was pending, so any replacement state is now based on a position
        // that the stale correction moved away from. Fail that current state closed too.
        return attempt.teleported()
                && !attempt.destinationMatches()
                && states.containsKey(playerId);
    }
}
