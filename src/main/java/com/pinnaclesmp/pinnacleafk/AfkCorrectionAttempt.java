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
            // Another correction already owns the player's pending-correction slot.
            // This attempt never started, so callers must not treat it as a failure of
            // the current AFK state or clear that state as part of fail-closed cleanup.
            return new Result(false, false, false, null);
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

        return new Result(true, teleported, finalDestinationMatches, failure);
    }

    record Result(
            boolean started,
            boolean teleported,
            boolean destinationMatches,
            RuntimeException failure
    ) {
        Result(
                boolean teleported,
                boolean destinationMatches,
                RuntimeException failure
        ) {
            this(true, teleported, destinationMatches, failure);
        }

        boolean succeeded() {
            return started && failure == null && teleported && destinationMatches;
        }
    }
}
