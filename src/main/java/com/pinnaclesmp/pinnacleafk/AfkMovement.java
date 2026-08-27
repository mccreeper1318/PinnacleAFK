package com.pinnaclesmp.pinnacleafk;

final class AfkMovement {
    private AfkMovement() {
    }

    static boolean viewChanged(
            float fromYaw,
            float fromPitch,
            float toYaw,
            float toPitch
    ) {
        return Float.compare(fromYaw, toYaw) != 0
                || Float.compare(fromPitch, toPitch) != 0;
    }
}
