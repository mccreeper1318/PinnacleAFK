package com.pinnaclesmp.pinnacleafk;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AfkReconciliationDismountFailureTest {
    private static final Path PLUGIN_SOURCE = Path.of(
            "src/main/java/com/pinnaclesmp/pinnacleafk/PinnacleAfkPlugin.java"
    );

    @Test
    void failedReconciliationDismountFailsClosedBeforeProtectionCanContinue() throws IOException {
        String source = Files.readString(PLUGIN_SOURCE);

        int enforceStart = source.indexOf("private boolean enforceAfkLock(Player player, AfkState state)");
        int enforceEnd = source.indexOf("private boolean dismountForAfk(Player player)", enforceStart);
        String enforceMethod = source.substring(enforceStart, enforceEnd);

        int failedDismount = enforceMethod.indexOf("if (!dismountForAfk(player))");
        int failClosed = enforceMethod.indexOf("failAfkDismount(player, state);", failedDismount);
        int stopReconciliation = enforceMethod.indexOf("return false;", failClosed);

        assertTrue(failedDismount >= 0, "reconciliation must check the dismount result");
        assertTrue(failClosed > failedDismount, "failed dismount must clear the bound AFK session");
        assertTrue(stopReconciliation > failClosed, "reconciliation must stop after failed dismount");
    }

    @Test
    void movingVehiclePathAlsoFailsClosedOnDismountFailure() throws IOException {
        String source = Files.readString(PLUGIN_SOURCE);

        int handlerStart = source.indexOf("private void handleMovingVehiclePassenger(Entity passenger)");
        int handlerEnd = source.indexOf("private boolean isProtectionEligible(Player player)", handlerStart);
        String handlerMethod = source.substring(handlerStart, handlerEnd);

        int failedDismount = handlerMethod.indexOf("else if (!dismountForAfk(player))");
        int failClosed = handlerMethod.indexOf("failAfkDismount(player, state);", failedDismount);
        int correction = handlerMethod.indexOf("correctAfkPosition(player, state);", failClosed);

        assertTrue(failedDismount >= 0, "moving vehicles must check the dismount result");
        assertTrue(failClosed > failedDismount, "failed moving-vehicle dismount must clear AFK state");
        assertTrue(correction > failClosed, "position correction must only occur in the successful-dismount branch");
    }
}
