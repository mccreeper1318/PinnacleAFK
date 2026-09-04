package com.pinnaclesmp.pinnacleafk;

import java.util.function.BooleanSupplier;

final class AfkDismount {
    private AfkDismount() {
    }

    static boolean attempt(
            BooleanSupplier stillMounted,
            Runnable normalDismount,
            Runnable fallbackDismount
    ) {
        if (!stillMounted.getAsBoolean()) {
            return true;
        }

        normalDismount.run();
        if (!stillMounted.getAsBoolean()) {
            return true;
        }

        fallbackDismount.run();
        return !stillMounted.getAsBoolean();
    }
}
