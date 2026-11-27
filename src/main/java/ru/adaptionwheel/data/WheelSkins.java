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

    /**
     * The CPU-lit fallback's decode of the orbital mesh's material id (the red vertex-colour channel):
     * dark metal, cyan emissive shell, plain fabric. The returned arrays are shared and read-only —
     * the fallback draws tens of thousands of corners a frame and cannot allocate per corner.
     */
    private static final float[] DARK_METAL = {0.35f, 0.35f, 0.35f};
    private static final float[] EMISSIVE_SHELL = {0.05f, 0.80f, 1.00f};
    private static final float[] WHITE = {1.00f, 1.00f, 1.00f};

    public static float[] tintForMaterial(float id) {
        if (id < 0.25f) {
            return DARK_METAL;
        }
        return id < 0.75f ? EMISSIVE_SHELL : WHITE;
    }

    /**
     * The CPU fallback's per-channel tint: an orbital shell keeps its authored colour because the
     * shader's orbital branch ignores WheelTint, so the fallback must not apply it either.
     */
    public static float fallbackTint(boolean orbital, float material, float adaptationTint) {
        return orbital ? material : material * adaptationTint;
    }
}
