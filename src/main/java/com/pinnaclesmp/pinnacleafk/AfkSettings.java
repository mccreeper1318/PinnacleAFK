package com.pinnaclesmp.pinnacleafk;

import org.bukkit.configuration.Configuration;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;

record AfkSettings(
        int invincibleAfterSeconds,
        int toggleCooldownSeconds,
        boolean broadcastsEnabled,
        Set<String> allowedWorlds,
        boolean protectionEnabled,
        boolean protectionRequiresPermission,
        Set<String> protectionAllowedWorlds,
        boolean tabIndicatorEnabled,
        boolean nametagIndicatorEnabled,
        boolean automaticAfkEnabled,
        int automaticAfkAfterSeconds
) {
    static final int DEFAULT_INVINCIBLE_AFTER_SECONDS = 10;
    static final int DEFAULT_TOGGLE_COOLDOWN_SECONDS = 3;
    static final int DEFAULT_AUTOMATIC_AFK_AFTER_SECONDS = 300;

    AfkSettings {
        allowedWorlds = Set.copyOf(allowedWorlds);
        protectionAllowedWorlds = Set.copyOf(protectionAllowedWorlds);
    }

    static AfkSettings load(Configuration config, Logger logger) {
        return new AfkSettings(
                readNonNegativeSeconds(
                        config,
                        logger,
                        "invincible-after-seconds",
                        DEFAULT_INVINCIBLE_AFTER_SECONDS
                ),
                readNonNegativeSeconds(
                        config,
                        logger,
                        "toggle-cooldown-seconds",
                        DEFAULT_TOGGLE_COOLDOWN_SECONDS
                ),
                readBoolean(config, logger, "broadcasts.enabled", true),
                readWorldList(config, logger, "allowed-worlds"),
                readBoolean(config, logger, "protection.enabled", true),
                readBoolean(config, logger, "protection.require-permission", false),
                readWorldList(config, logger, "protection.allowed-worlds"),
                readBoolean(config, logger, "display.tab-enabled", true),
                readBoolean(config, logger, "display.nametag-enabled", true),
                readBoolean(config, logger, "automatic-afk.enabled", false),
                readNonNegativeSeconds(
                        config,
                        logger,
                        "automatic-afk.after-seconds",
                        DEFAULT_AUTOMATIC_AFK_AFTER_SECONDS
                )
        );
    }

    boolean allowsAfkWorld(String worldName) {
        return allowsWorld(allowedWorlds, worldName);
    }

    boolean allowsProtectionWorld(String worldName) {
        return protectionEnabled && allowsWorld(protectionAllowedWorlds, worldName);
    }

    boolean protectionPolicyEquals(AfkSettings other) {
        return other != null
                && invincibleAfterSeconds == other.invincibleAfterSeconds
                && protectionEnabled == other.protectionEnabled
                && protectionRequiresPermission == other.protectionRequiresPermission
                && protectionAllowedWorlds.equals(other.protectionAllowedWorlds);
    }

    private static int readNonNegativeSeconds(
            Configuration config,
            Logger logger,
            String path,
            int safeDefault
    ) {
        Object configured = config.get(path);
        if (configured instanceof Number number) {
            double decimalValue = number.doubleValue();
            long wholeValue = number.longValue();
            if (Double.isFinite(decimalValue)
                    && decimalValue == wholeValue
                    && wholeValue >= 0L
                    && wholeValue <= Integer.MAX_VALUE) {
                return (int) wholeValue;
            }
        }

        logger.warning(
                "Invalid " + path + " value '" + configured
                        + "'; using the safe default of " + safeDefault + " seconds."
        );
        config.set(path, safeDefault);
        return safeDefault;
    }

    private static boolean readBoolean(
            Configuration config,
            Logger logger,
            String path,
            boolean safeDefault
    ) {
        Object configured = config.get(path);
        if (configured instanceof Boolean booleanValue) {
            return booleanValue;
        }

        logger.warning(
                "Invalid " + path + " value '" + configured
                        + "'; using the safe default of " + safeDefault + "."
        );
        config.set(path, safeDefault);
        return safeDefault;
    }

    private static Set<String> readWorldList(
            Configuration config,
            Logger logger,
            String path
    ) {
        Object configured = config.get(path);
        if (!(configured instanceof List<?> configuredWorlds)
                || configuredWorlds.stream().anyMatch(value -> !(value instanceof String))) {
            logger.warning(
                    "Invalid " + path + " value '" + configured
                            + "'; using an empty list to allow every world."
            );
            config.set(path, List.of());
            return Set.of();
        }

        Set<String> worlds = new LinkedHashSet<>();
        for (Object configuredWorld : configuredWorlds) {
            String worldName = ((String) configuredWorld).trim();
            if (!worldName.isEmpty()) {
                worlds.add(worldName.toLowerCase(Locale.ROOT));
            }
        }
        return worlds;
    }

    private static boolean allowsWorld(Set<String> configuredWorlds, String worldName) {
        return configuredWorlds.isEmpty()
                || configuredWorlds.contains("*")
                || configuredWorlds.contains(worldName.toLowerCase(Locale.ROOT));
    }
}
