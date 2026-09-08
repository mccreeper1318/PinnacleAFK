package com.pinnaclesmp.pinnacleafk;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

final class ConfigSyntaxValidator {
    private ConfigSyntaxValidator() {
    }

    static void validate(File configFile) throws IOException, InvalidConfigurationException {
        Objects.requireNonNull(configFile, "configFile");
        YamlConfiguration syntaxCheck = new YamlConfiguration();
        syntaxCheck.load(configFile);
    }
}
