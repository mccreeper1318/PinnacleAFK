package com.pinnaclesmp.pinnacleafk;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

final class TabComponentFormatting {
    private TabComponentFormatting() {
    }

    static Component grayRecursively(Component component) {
        return component
                .color(NamedTextColor.GRAY)
                .children(component.children().stream()
                        .map(TabComponentFormatting::grayRecursively)
                        .toList());
    }
}
