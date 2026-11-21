package ru.adaptionwheel.data;

import org.jetbrains.annotations.Nullable;

/** The ids stored in {@link ModDataComponents#WHEEL_SKIN}, and the one place that validates them. */
public final class WheelSkins {

    public static final String DHARMA = "dharma";
    public static final String ORBITAL = "orbital";

    private WheelSkins() {
    }

    public static boolean isValid(String skin) {
        return DHARMA.equals(skin) || ORBITAL.equals(skin);
    }

    /** An absent or unknown id reads as the default rather than leaking a bad value to the renderer. */
    public static String normalize(@Nullable String skin) {
        return isValid(skin) ? skin : DHARMA;
    }
}
