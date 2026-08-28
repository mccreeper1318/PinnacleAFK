package com.pinnaclesmp.pinnacleafk;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AfkSettingsTest {
    private static final Logger LOGGER = Logger.getLogger(AfkSettingsTest.class.getName());

    @Test
    void existingConfigurationReceivesNewDefaults() {
        YamlConfiguration existing = new YamlConfiguration();
        existing.set("invincible-after-seconds", 5);
        existing.setDefaults(defaults());
        existing.options().copyDefaults(true);

        AfkSettings settings = AfkSettings.load(existing, LOGGER);
        String saved = existing.saveToString();

        assertEquals(5, settings.invincibleAfterSeconds());
        assertFalse(settings.automaticAfkEnabled());
        assertEquals(
                "&aPinnacleAFK configuration reloaded.",
                existing.getString("messages.reload-success")
        );
        assertTrue(saved.contains("automatic-afk:"));
        assertTrue(saved.contains("broadcasts:"));
    }

    @Test
    void invalidSettingsAreReplacedWithSafeDefaults() {
        YamlConfiguration config = defaults();
        config.set("invincible-after-seconds", -1);
        config.set("toggle-cooldown-seconds", 1.5D);
        config.set("automatic-afk.after-seconds", "later");
        config.set("broadcasts.enabled", "yes");
        config.set("allowed-worlds", "world");

        AfkSettings settings = AfkSettings.load(config, LOGGER);

        assertEquals(AfkSettings.DEFAULT_INVINCIBLE_AFTER_SECONDS, settings.invincibleAfterSeconds());
        assertEquals(AfkSettings.DEFAULT_TOGGLE_COOLDOWN_SECONDS, settings.toggleCooldownSeconds());
        assertEquals(
                AfkSettings.DEFAULT_AUTOMATIC_AFK_AFTER_SECONDS,
                settings.automaticAfkAfterSeconds()
        );
        assertTrue(settings.broadcastsEnabled());
        assertTrue(settings.allowedWorlds().isEmpty());
        assertEquals(
                AfkSettings.DEFAULT_INVINCIBLE_AFTER_SECONDS,
                config.getInt("invincible-after-seconds")
        );
    }

    @Test
    void worldPoliciesAreCaseInsensitiveAndIndependent() {
        YamlConfiguration config = defaults();
        config.set("allowed-worlds", List.of("World", "world_nether"));
        config.set("protection.allowed-worlds", List.of("world"));

        AfkSettings settings = AfkSettings.load(config, LOGGER);

        assertEquals(Set.of("world", "world_nether"), settings.allowedWorlds());
        assertTrue(settings.allowsAfkWorld("WORLD_NETHER"));
        assertFalse(settings.allowsAfkWorld("world_the_end"));
        assertTrue(settings.allowsProtectionWorld("WORLD"));
        assertFalse(settings.allowsProtectionWorld("world_nether"));
    }

    private static YamlConfiguration defaults() {
        YamlConfiguration defaults = new YamlConfiguration();
        defaults.set("invincible-after-seconds", 10);
        defaults.set("toggle-cooldown-seconds", 3);
        defaults.set("broadcasts.enabled", true);
        defaults.set("allowed-worlds", List.of());
        defaults.set("protection.enabled", true);
        defaults.set("protection.require-permission", false);
        defaults.set("protection.allowed-worlds", List.of());
        defaults.set("display.tab-enabled", true);
        defaults.set("display.nametag-enabled", true);
        defaults.set("automatic-afk.enabled", false);
        defaults.set("automatic-afk.after-seconds", 300);
        defaults.set("messages.reload-success", "&aPinnacleAFK configuration reloaded.");
        return defaults;
    }
}
