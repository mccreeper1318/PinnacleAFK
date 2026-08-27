package com.pinnaclesmp.pinnacleafk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValueOwnershipTest {
    @Test
    void restoresOnlyTheValueStillOwnedByPinnacleAfk() {
        String original = "rank-format";
        String applied = "[AFK] rank-format";

        assertTrue(ValueOwnership.stillOwns(applied, applied));
        assertFalse(ValueOwnership.stillOwns("external-update", applied));
        assertFalse(ValueOwnership.stillOwns(original, applied));
    }

    @Test
    void nullableValuesAreComparedSafely() {
        assertTrue(ValueOwnership.stillOwns(null, null));
        assertFalse(ValueOwnership.stillOwns(null, "applied"));
    }
}
