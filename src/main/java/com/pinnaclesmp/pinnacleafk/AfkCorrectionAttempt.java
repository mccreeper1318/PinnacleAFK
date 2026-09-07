package com.pinnaclesmp.pinnacleafk;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;

final class AfkCorrectionAttempt {
    private AfkCorrectionAttempt() {
    }

    static Result run(
            Map<UUID, AfkCorrectionTeleport> pendingCorrections,
            UUID playerId,
            AfkCorrectionTeleport expectedCorrection,
            BooleanSupplier teleportAction,
            BooleanSupplier destinationMatches
    ) {
        Objects.requireNonNull(pendingCorrections, "pendingCorrections");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(expectedCorrection, "expectedCorrection");
        Objects.requireNonNull(teleportAction, "teleportAction");
        Objects.requireNonNull(destinationMatches, "destinationMatches");

        AfkCorrectionTeleport existingCorrection = pendingCorrections.putIfAbsent(
                playerId,
                expectedCorrection
        );
        if (existingCorrection != null) {
            return new Result(
                    false,
                    false,
                    new IllegalStateException("An AFK correction is already pending for " + playerId)
            );
        }

        boolean teleported = false;
        boolean finalDestinationMatches = false;
        RuntimeException failure = null;
        try {
            teleported = teleportAction.getAsBoolean();
            if (teleported) {
                finalDestinationMatches = destinationMatches.getAsBoolean();
            }
        } catch (RuntimeException exception) {
            failure = exception;
        } finally {
            pendingCorrections.remove(playerId, expectedCorrection);
        }

        return new Result(teleported, finalDestinationMatches, failure);
    }

    record Result(
            boolean teleported,
            boolean destinationMatches,
            RuntimeException failure
    ) {
        boolean succeeded() {
            return failure == null && teleported && destinationMatches;
        }
    }
}
