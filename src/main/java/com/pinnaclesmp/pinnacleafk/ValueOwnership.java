package com.pinnaclesmp.pinnacleafk;

import java.util.Objects;

final class ValueOwnership {
    private ValueOwnership() {
    }

    static boolean stillOwns(Object currentValue, Object appliedValue) {
        return Objects.equals(currentValue, appliedValue);
    }
}
