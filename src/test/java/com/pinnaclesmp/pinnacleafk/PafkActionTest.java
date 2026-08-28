package com.pinnaclesmp.pinnacleafk;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PafkActionTest {
    @Test
    void parsesCommandsCaseInsensitively() {
        assertEquals(PafkAction.RELOAD, PafkAction.parse("ReLoAd").orElseThrow());
        assertEquals(PafkAction.REMOVE, PafkAction.parse("remove").orElseThrow());
        assertTrue(PafkAction.parse("unknown").isEmpty());
    }

    @Test
    void completesOnlyMatchingSubcommands() {
        assertEquals(List.of("reload", "remove"), PafkAction.completions("re"));
        assertEquals(List.of("status"), PafkAction.completions("sta"));
    }
}
