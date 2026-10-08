package ru.adaptionwheel.client;

import net.minecraft.client.renderer.PostPass;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.cinematic.ImpactFrameTiming;
import ru.adaptionwheel.client.fx.DimensionImpactUniforms;
import ru.adaptionwheel.config.AdaptionConfig;

/** Client-side timing and uniforms for the Dimension Destroy manga impact frame. */
@EventBusSubscriber(modid = AdaptionWheel.MODID, value = Dist.CLIENT)
public final class DimensionImpactFX {

    private static final float DETONATION_SECONDS = 0.05f;
    private static final float MAX_FRAME_STEP = 1f / 20f;
    private static final float RING_GAIN = 1.15f;
    private static final float LINES_GAIN = 1.05f;
    private static final float SCREENTONE_CRAWL = 2.5f;
    private static final float INK_DARKNESS = 1.0f;

    private static final float[] INK = {0.045f, 0.02f, 0.075f, 1.0f};
    private static final float[] PAPER = {0.97f, 0.96f, 0.98f, 1.0f};

    private static volatile float age = Float.MAX_VALUE;
    private static volatile float centreX = 0.5f;
    private static volatile float centreY = 0.5f;
    private static volatile long lastFrameNanos;

    private DimensionImpactFX() {
    }

    /** Starts or restarts the client-side frame after the server confirms a rift volley. */
    public static void trigger(float cx, float cy) {
        centreX = Mth.clamp(cx, 0f, 1f);
        centreY = Mth.clamp(cy, 0f, 1f);
        age = 0f;
        lastFrameNanos = 0L;
    }

    /** Advances from the render mixin, so this wall-clock effect does not depend on game TPS. */
    public static void advanceFrame() {
        if (!ImpactFrameTiming.playing(age)) {
            lastFrameNanos = 0L;
            return;
        }

        long now = System.nanoTime();
        long previous = lastFrameNanos;
        lastFrameNanos = now;
        if (previous == 0L) {
            return;
        }

        float dt = (now - previous) / 1_000_000_000f;
        age += Math.min(Math.max(dt, 0f), MAX_FRAME_STEP);
        if (age >= ImpactFrameTiming.TOTAL) {
            age = Float.MAX_VALUE;
            lastFrameNanos = 0L;
        }
    }

    public static boolean active() {
        return AdaptionConfig.DIMENSION_IMPACT_ENABLED.get() && ImpactFrameTiming.playing(age);
    }

    /** Writes the per-frame values into the post pass's custom std140 uniform buffer. */
    public static void writeUniforms(PostPass pass, int screenWidth, int screenHeight) {
        float currentAge = age;
        float configStrength = AdaptionConfig.DIMENSION_IMPACT_STRENGTH.get().floatValue();
        float panelStrength = ImpactFrameTiming.strengthAt(currentAge) * configStrength;
        float panelProgress = ImpactFrameTiming.progressAt(currentAge);
        float mode = ImpactFrameTiming.modeAt(currentAge,
                AdaptionConfig.DIMENSION_IMPACT_MODE_A.get(),
                AdaptionConfig.DIMENSION_IMPACT_MODE_B.get());
        float flash = flashAt(currentAge);

        DimensionImpactUniforms.apply(pass, screenWidth, screenHeight,
                panelStrength,
                AdaptionConfig.DIMENSION_IMPACT_ABERRATION.get().floatValue(),
                flash,
                1.0f,
                panelProgress, mode, centreX, centreY,
                RING_GAIN * configStrength,
                LINES_GAIN * configStrength,
                SCREENTONE_CRAWL,
                INK_DARKNESS,
                INK,
                PAPER);
    }

    private static float flashAt(float currentAge) {
        if (!AdaptionConfig.DIMENSION_IMPACT_FLASH_ENABLED.get()) {
            return 0f;
        }
        if (currentAge < DETONATION_SECONDS) {
            float detonation = 1f - currentAge / DETONATION_SECONDS;
            // Values above one mark the one-shot white detonation for the shader; regular strobe
            // values stay within [-1, 1] and alternate paper/ink.
            return 1f + detonation * detonation;
        }

        int index = ImpactFrameTiming.flashIndex(currentAge,
                AdaptionConfig.DIMENSION_IMPACT_FLASH_FRAMES.get(),
                AdaptionConfig.DIMENSION_IMPACT_FLASH_MS.get());
        if (index < 0) {
            return 0f;
        }
        float strength = Mth.clamp(AdaptionConfig.DIMENSION_IMPACT_FLASH_STRENGTH.get().floatValue(), 0f, 1f);
        return (index & 1) == 0 ? strength : -strength;
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        float currentAge = age;
        if (!active()) {
            return;
        }

        float power = AdaptionConfig.DIMENSION_IMPACT_SHAKE_ENABLED.get()
                ? ImpactFrameTiming.strengthAt(currentAge)
                * AdaptionConfig.DIMENSION_IMPACT_SHAKE_STRENGTH.get().floatValue()
                : 0f;
        if (power <= 0.01f) {
            return;
        }

        float t = currentAge * 47f;
        event.setYaw(event.getYaw() + (float) (Math.sin(t) + Math.sin(t * 2.17f) * 0.5f) * power);
        event.setPitch(event.getPitch() + (float) (Math.cos(t * 1.31f) + Math.sin(t * 2.83f) * 0.5f) * power);
        event.setRoll(event.getRoll() + (float) Math.sin(t * 0.87f) * power * 0.8f);

        if (currentAge < DETONATION_SECONDS) {
            float snap = 1f - currentAge / DETONATION_SECONDS;
            event.setPitch(event.getPitch() + snap * power * 6f);
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        age = Float.MAX_VALUE;
        lastFrameNanos = 0L;
        centreX = 0.5f;
        centreY = 0.5f;
    }
}
