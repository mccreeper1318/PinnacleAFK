package com.pinnaclesmp.pinnacleafk;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.NBTComponent;
import net.kyori.adventure.text.SelectorComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.TranslationArgument;
import net.kyori.adventure.text.format.NamedTextColor;

final class TabComponentFormatting {
    private TabComponentFormatting() {
    }

    static Component grayRecursively(Component component) {
        Component gray = component
                .color(NamedTextColor.GRAY)
                .children(component.children().stream()
                        .map(TabComponentFormatting::grayRecursively)
                        .toList());

        if (gray instanceof TranslatableComponent translatable) {
            gray = translatable.arguments(translatable.arguments().stream()
                    .map(TabComponentFormatting::grayTranslationArgument)
                    .toList());
        }

        if (gray instanceof SelectorComponent selector && selector.separator() != null) {
            gray = selector.separator(grayRecursively(selector.separator()));
        }

        if (gray instanceof NBTComponent<?, ?> nbt && nbt.separator() != null) {
            gray = nbt.separator(grayRecursively(nbt.separator()));
        }

        return gray;
    }

    private static TranslationArgument grayTranslationArgument(TranslationArgument argument) {
        Object value = argument.value();
        if (value instanceof Component embedded) {
            return TranslationArgument.component(grayRecursively(embedded));
        }
        return argument;
    }
}
