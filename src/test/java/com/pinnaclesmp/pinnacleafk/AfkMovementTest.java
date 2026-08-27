package com.pinnaclesmp.pinnacleafk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AfkMovementTest {
    @Test
    void unchangedViewDoesNotCountAsActivity() {
        assertFalse(AfkMovement.viewChanged(45.0F, 10.0F, 45.0F, 10.0F));
    }

    @Test
    void yawRotationCountsAsActivity() {
        assertTrue(AfkMovement.viewChanged(45.0F, 10.0F, 46.0F, 10.0F));
    }

    @Test
    void pitchRotationCountsAsActivity() {
        assertTrue(AfkMovement.viewChanged(45.0F, 10.0F, 45.0F, 11.0F));
    }
}
