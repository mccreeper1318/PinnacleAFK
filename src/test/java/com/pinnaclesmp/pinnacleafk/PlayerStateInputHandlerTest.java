package com.pinnaclesmp.pinnacleafk;

import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerStateInputHandlerTest {
    @Test
    void stateChangingInputsUseTheCompleteActionLockContract() throws NoSuchMethodException {
        assertActionLockHandler("onPlayerItemHeld", PlayerItemHeldEvent.class);
        assertActionLockHandler("onPlayerToggleFlight", PlayerToggleFlightEvent.class);
        assertActionLockHandler("onPlayerToggleSneak", PlayerToggleSneakEvent.class);
        assertActionLockHandler("onPlayerToggleSprint", PlayerToggleSprintEvent.class);
    }

    private static void assertActionLockHandler(
            String methodName,
            Class<?> eventType
    ) throws NoSuchMethodException {
        assertTrue(Cancellable.class.isAssignableFrom(eventType));

        Method method = PinnacleAfkPlugin.class.getMethod(methodName, eventType);
        EventHandler handler = method.getAnnotation(EventHandler.class);

        assertNotNull(handler, methodName + " must remain an event handler");
        assertEquals(EventPriority.HIGHEST, handler.priority());
        assertFalse(handler.ignoreCancelled());
    }
}
