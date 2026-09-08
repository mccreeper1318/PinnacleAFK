package com.pinnaclesmp.pinnacleafk;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AfkCorrectionDisplayRecoveryTest {
    private static final Path PLUGIN_SOURCE = Path.of(
            "src/main/java/com/pinnaclesmp/pinnacleafk/PinnacleAfkPlugin.java"
    );

    @Test
    void successfulCorrectionRefreshesMarkerBeforeReturningSuccess() throws IOException {
        String source = Files.readString(PLUGIN_SOURCE);

        int bindingCheck = source.indexOf("if (!AfkStateBinding.mayContinueAfterCorrection(");
        int markerRefresh = source.indexOf("refreshAfkDisplay(player, state);", bindingCheck);
        int successReturn = source.indexOf("return true;", markerRefresh);

        assertTrue(bindingCheck >= 0, "correction must retain its exact-state success guard");
        assertTrue(markerRefresh > bindingCheck, "a successful correction must restore the AFK marker");
        assertTrue(successReturn > markerRefresh, "marker recovery must happen before correction reports success");
    }

    @Test
    void markerRefreshReusesOnlyDisplaysFromThePlayersCurrentWorld() throws IOException {
        String source = Files.readString(PLUGIN_SOURCE);

        int refreshStart = source.indexOf("private void refreshAfkDisplay(Player player, AfkState state)");
        int refreshEnd = source.indexOf("private void removeAfkDisplay(AfkState state)", refreshStart);
        String refreshMethod = source.substring(refreshStart, refreshEnd);

        assertTrue(
                refreshMethod.contains("textDisplay.getWorld().equals(player.getWorld())"),
                "a correction across worlds must not try to reattach a stale display from the old world"
        );
        assertTrue(
                refreshMethod.contains("player.getPassengers().contains(textDisplay) || player.addPassenger(textDisplay)"),
                "a detached same-world marker must be reattached when possible"
        );
        assertTrue(
                refreshMethod.indexOf("existing.remove();")
                        < refreshMethod.indexOf("state.afkDisplayEntityId = createAfkDisplay(player).getUniqueId();"),
                "a stale or unattachable marker must be removed before a replacement is created"
        );
    }
}
