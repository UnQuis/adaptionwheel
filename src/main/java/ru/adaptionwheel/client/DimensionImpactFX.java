package ru.adaptionwheel.client;

import net.minecraft.client.renderer.PostPass;
import net.minecraft.util.Mth;
import ru.adaptionwheel.client.fx.DimensionImpactUniforms;

public final class DimensionImpactFX {

    private static final float BURST_WINDOW = 0.28f;
    private static final float PANEL_SECONDS = 0.9f;

    private static float age = Float.MAX_VALUE;
    private static float centreX = 0.5f;
    private static float centreY = 0.5f;

    private DimensionImpactFX() {
    }

    public static void trigger(float cx, float cy) {
        age = 0f;
        centreX = cx;
        centreY = cy;
    }

    public static boolean active() {
        return age < PANEL_SECONDS;
    }

    public static void tick(float dt) {
        if (age < PANEL_SECONDS) {
            age += Math.min(dt, 0.1f);
        }
    }

    public static void writeUniforms(PostPass pass, int screenWidth, int screenHeight) {
        float panelProgress = Mth.clamp(age / PANEL_SECONDS, 0f, 1f);
        float strength = age < BURST_WINDOW ? 1f
                : Mth.clamp(1f - (age - BURST_WINDOW) / (PANEL_SECONDS - BURST_WINDOW), 0f, 1f);
        float flash = age < 0.05f ? (1f - age / 0.05f) * 0.6f : 0f;

        DimensionImpactUniforms.apply(pass, screenWidth, screenHeight,
                strength, 1.0f, flash, 1.0f,
                Math.min(age, BURST_WINDOW), centreX, centreY,
                0.85f, 0.7f, 2.5f, 1.0f,
                new float[] {0.045f, 0.02f, 0.075f, 1.0f},
                new float[] {0.97f, 0.96f, 0.98f, 1.0f});
    }
}