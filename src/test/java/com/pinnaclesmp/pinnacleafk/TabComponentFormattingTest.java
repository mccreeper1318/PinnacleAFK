package com.pinnaclesmp.pinnacleafk;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TabComponentFormattingTest {
    @Test
    void recolorsNestedPlayerListComponentsGray() {
        Component source = Component.text("Player", NamedTextColor.RED)
                .append(Component.text(" <FM>", NamedTextColor.GOLD)
                        .append(Component.text(" Rank", NamedTextColor.GREEN)));

        Component gray = TabComponentFormatting.grayRecursively(source);

        assertEquals(NamedTextColor.GRAY, gray.color());
        assertEquals(NamedTextColor.GRAY, gray.children().getFirst().color());
        assertEquals(NamedTextColor.GRAY, gray.children().getFirst().children().getFirst().color());
    }
}
