package com.pinnaclesmp.pinnacleafk;

import org.bukkit.event.player.PlayerTeleportEvent;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AfkCorrectionTeleportTest {
    private static final UUID LOCK_WORLD = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_WORLD = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final AfkCorrectionTeleport EXPECTED = new AfkCorrectionTeleport(
            LOCK_WORLD,
            12.5D,
            64.0D,
            -8.25D,
            135.0F,
            -22.5F
    );

    @Test
    void expectedPluginCorrectionMatchesExactSavedDestination() {
        assertTrue(EXPECTED.matches(
                PlayerTeleportEvent.TeleportCause.PLUGIN,
                LOCK_WORLD,
                12.5D,
                64.0D,
                -8.25D,
                135.0F,
                -22.5F
        ));
    }

    @Test
    void nestedUnrelatedTeleportDoesNotMatchPendingCorrection() {
        assertFalse(EXPECTED.matches(
                PlayerTeleportEvent.TeleportCause.PLUGIN,
                LOCK_WORLD,
                13.5D,
                64.0D,
                -8.25D,
                135.0F,
                -22.5F
        ));
        assertFalse(EXPECTED.matches(
                PlayerTeleportEvent.TeleportCause.COMMAND,
                LOCK_WORLD,
                12.5D,
                64.0D,
                -8.25D,
                135.0F,
                -22.5F
        ));
    }

    @Test
    void modifiedCorrectionDestinationIsNotTrusted() {
        assertFalse(EXPECTED.matches(
                PlayerTeleportEvent.TeleportCause.PLUGIN,
                OTHER_WORLD,
                12.5D,
                64.0D,
                -8.25D,
                135.0F,
                -22.5F
        ));
        assertFalse(EXPECTED.matches(
                PlayerTeleportEvent.TeleportCause.PLUGIN,
                LOCK_WORLD,
                12.5D,
                64.0D,
                -8.25D,
                136.0F,
                -22.5F
        ));
        assertFalse(EXPECTED.matches(
                PlayerTeleportEvent.TeleportCause.PLUGIN,
                LOCK_WORLD,
                12.5D,
                64.0D,
                -8.25D,
                135.0F,
                -21.5F
        ));
    }
}
