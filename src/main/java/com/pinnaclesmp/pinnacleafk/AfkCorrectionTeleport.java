package com.pinnaclesmp.pinnacleafk;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Objects;
import java.util.UUID;

record AfkCorrectionTeleport(
        UUID worldId,
        double x,
        double y,
        double z,
        float yaw,
        float pitch
) {
    AfkCorrectionTeleport {
        Objects.requireNonNull(worldId, "worldId");
    }

    static AfkCorrectionTeleport from(Location destination) {
        World world = Objects.requireNonNull(destination.getWorld(), "Correction destination world");
        return new AfkCorrectionTeleport(
                world.getUID(),
                destination.getX(),
                destination.getY(),
                destination.getZ(),
                destination.getYaw(),
                destination.getPitch()
        );
    }

    boolean matches(PlayerTeleportEvent.TeleportCause cause, Location destination) {
        if (cause != PlayerTeleportEvent.TeleportCause.PLUGIN || destination == null) {
            return false;
        }

        World destinationWorld = destination.getWorld();
        if (destinationWorld == null) {
            return false;
        }

        return matches(
                cause,
                destinationWorld.getUID(),
                destination.getX(),
                destination.getY(),
                destination.getZ(),
                destination.getYaw(),
                destination.getPitch()
        );
    }

    boolean matches(
            PlayerTeleportEvent.TeleportCause cause,
            UUID destinationWorldId,
            double destinationX,
            double destinationY,
            double destinationZ,
            float destinationYaw,
            float destinationPitch
    ) {
        return cause == PlayerTeleportEvent.TeleportCause.PLUGIN
                && worldId.equals(destinationWorldId)
                && Double.compare(x, destinationX) == 0
                && Double.compare(y, destinationY) == 0
                && Double.compare(z, destinationZ) == 0
                && Float.compare(yaw, destinationYaw) == 0
                && Float.compare(pitch, destinationPitch) == 0;
    }
}
