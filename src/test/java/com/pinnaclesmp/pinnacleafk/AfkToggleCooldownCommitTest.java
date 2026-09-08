package com.pinnaclesmp.pinnacleafk;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AfkToggleCooldownCommitTest {
    private static final Path PLUGIN_SOURCE = Path.of(
            "src/main/java/com/pinnaclesmp/pinnacleafk/PinnacleAfkPlugin.java"
    );

    @Test
    void cooldownIsCommittedOnlyAfterRequestedAfkStateIsReached() throws IOException {
        String source = Files.readString(PLUGIN_SOURCE);

        int cooldownMethodStart = source.indexOf("private boolean isToggleOnCooldown(Player player)");
        int cooldownMethodEnd = source.indexOf("private void ensureAfkReconcileTask()", cooldownMethodStart);
        String cooldownMethod = source.substring(cooldownMethodStart, cooldownMethodEnd);

        assertFalse(
                cooldownMethod.contains("lastToggleNanos.put("),
                "checking the cooldown must not consume it before AFK entry succeeds"
        );

        int toggleAttempt = source.indexOf("setAfk(player, enteringAfk, true);");
        int successCheck = source.indexOf("if (isAfk(player) == enteringAfk", toggleAttempt);
        int cooldownCommit = source.indexOf("lastToggleNanos.put(player.getUniqueId()", successCheck);

        assertTrue(toggleAttempt >= 0, "manual /afk must attempt the requested state change");
        assertTrue(successCheck > toggleAttempt, "the resulting AFK state must be checked after the attempt");
        assertTrue(cooldownCommit > successCheck, "the cooldown must be committed only after success is confirmed");
    }
}
