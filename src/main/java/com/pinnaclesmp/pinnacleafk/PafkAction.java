package com.pinnaclesmp.pinnacleafk;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

enum PafkAction {
    RELOAD,
    LIST,
    STATUS,
    REMOVE;

    static Optional<PafkAction> parse(String value) {
        try {
            return Optional.of(valueOf(value.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    static List<String> completions(String prefix) {
        String normalizedPrefix = prefix.toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .map(action -> action.name().toLowerCase(Locale.ROOT))
                .filter(name -> name.startsWith(normalizedPrefix))
                .toList();
    }
}
