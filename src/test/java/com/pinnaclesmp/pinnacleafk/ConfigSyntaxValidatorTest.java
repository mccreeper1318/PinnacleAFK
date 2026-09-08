package com.pinnaclesmp.pinnacleafk;

import org.bukkit.configuration.InvalidConfigurationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfigSyntaxValidatorTest {
    @TempDir
    Path tempDir;

    @Test
    void acceptsValidYamlWithoutChangingTheFile() throws IOException {
        Path config = tempDir.resolve("config.yml");
        byte[] original = "automatic-afk:\n  enabled: true\n".getBytes(StandardCharsets.UTF_8);
        Files.write(config, original);

        assertDoesNotThrow(() -> ConfigSyntaxValidator.validate(config.toFile()));
        assertArrayEquals(original, Files.readAllBytes(config));
    }

    @Test
    void rejectsMalformedYamlWithoutChangingTheFile() throws IOException {
        Path config = tempDir.resolve("config.yml");
        byte[] original = "automatic-afk:\n  enabled: [true\n".getBytes(StandardCharsets.UTF_8);
        Files.write(config, original);

        assertThrows(
                InvalidConfigurationException.class,
                () -> ConfigSyntaxValidator.validate(config.toFile())
        );
        assertArrayEquals(original, Files.readAllBytes(config));
    }
}
