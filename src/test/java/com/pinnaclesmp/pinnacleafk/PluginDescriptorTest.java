package com.pinnaclesmp.pinnacleafk;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginDescriptorTest {
    @Test
    void pafkPermissionIsCheckedByTheExecutor() throws Exception {
        InputStream resource = Objects.requireNonNull(
                PluginDescriptorTest.class.getClassLoader().getResourceAsStream("plugin.yml")
        );

        try (InputStreamReader reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
            YamlConfiguration pluginYml = new YamlConfiguration();
            pluginYml.load(reader);

            assertFalse(pluginYml.contains("commands.pafk.permission"));
            assertTrue(pluginYml.contains("permissions.pinnacleafk.admin"));
        }
    }
}
