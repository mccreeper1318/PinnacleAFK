package com.pinnaclesmp.pinnacleafk;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.SelectorComponent;
import net.kyori.adventure.text.TranslatableComponent;
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

    @Test
    void recolorsTranslatableComponentArgumentsGray() {
        TranslatableComponent source = Component.translatable(
                "chat.type.text",
                Component.text("<FM>", NamedTextColor.RED),
                Component.text("Player", NamedTextColor.GOLD)
        );

        TranslatableComponent gray = (TranslatableComponent) TabComponentFormatting.grayRecursively(source);

        assertEquals(NamedTextColor.GRAY, ((Component) gray.arguments().get(0).value()).color());
        assertEquals(NamedTextColor.GRAY, ((Component) gray.arguments().get(1).value()).color());
    }

    @Test
    void recolorsSelectorSeparatorGray() {
        SelectorComponent source = Component.selector(
                "@a",
                Component.text(", ", NamedTextColor.RED)
        );

        SelectorComponent gray = (SelectorComponent) TabComponentFormatting.grayRecursively(source);

        assertEquals(NamedTextColor.GRAY, gray.separator().color());
    }
}
