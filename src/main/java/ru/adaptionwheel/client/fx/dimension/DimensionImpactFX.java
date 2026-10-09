package ru.adaptionwheel.client.fx.dimension;

import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.cinematic.ImpactFrameTiming;
import ru.adaptionwheel.config.AdaptionConfig;

/** Client-side timing, camera shake, and uniforms for the Dimension Destroy impact frame. */
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

    /** Advances from the camera render event, independently of game TPS. */
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

    /**
     * Index of the optional animated texture mask for the current flash step, or {@code -1} when
     * there is no mask frame to draw. Textures are supplied by a resource pack or the optional
     * impact-frames namespace; the procedural panel remains the fallback.
     */
    public static int textureMaskFrame() {
        if (!AdaptionConfig.DIMENSION_IMPACT_TEXTURE_MASKS.get() || !ImpactFrameTiming.playing(age)) {
            return -1;
        }
        int flashStep = ImpactFrameTiming.flashIndex(age,
                AdaptionConfig.DIMENSION_IMPACT_FLASH_FRAMES.get(),
                AdaptionConfig.DIMENSION_IMPACT_FLASH_MS.get());
        return flashStep < 0 ? -1 : flashStep % ImpactFrameTextureResolver.FRAME_COUNT;
    }

    /** Uploads one frame's uniforms; the copy pass sets its panel strength to zero. */
    public static GpuBufferSlice writeUniforms(int screenWidth, int screenHeight, boolean copyPass,
                                               boolean maskAvailable, int maskWidth, int maskHeight) {
        float currentAge = age;
        float configStrength = AdaptionConfig.DIMENSION_IMPACT_STRENGTH.get().floatValue();
        float panelStrength = copyPass ? 0f : ImpactFrameTiming.strengthAt(currentAge) * configStrength;
        float panelProgress = ImpactFrameTiming.progressAt(currentAge);
        float mode = ImpactFrameTiming.modeAt(currentAge,
                AdaptionConfig.DIMENSION_IMPACT_MODE_A.get(),
                AdaptionConfig.DIMENSION_IMPACT_MODE_B.get());

        // The visible flash is drawn by GuiGraphicsExtractor in RenderGuiEvent.Post. The shader
        // block's third ImpactParams component stays zero to preserve its std140 layout.
        return DimensionImpactUniforms.upload(screenWidth, screenHeight,
                panelStrength,
                AdaptionConfig.DIMENSION_IMPACT_ABERRATION.get().floatValue(),
                1.0f,
                panelProgress, mode, centreX, centreY,
                RING_GAIN * configStrength,
                LINES_GAIN * configStrength,
                SCREENTONE_CRAWL,
                INK_DARKNESS,
                INK,
                PAPER,
                !copyPass && maskAvailable,
                AdaptionConfig.DIMENSION_IMPACT_TEXTURE_SMOOTH_INVERT.get(),
                AdaptionConfig.DIMENSION_IMPACT_TEXTURE_STRETCH.get(),
                maskWidth,
                maskHeight);
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        advanceFrame();
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

    /** Draws the flash in the GUI overlay path, separate from the post-process pipeline. */
    @SubscribeEvent
    public static void onGuiPost(RenderGuiEvent.Post event) {
        if (!active()) {
            return;
        }

        int flash = flashOverlayColor(age);
        if ((flash >>> 24) == 0) {
            return;
        }

        GuiGraphicsExtractor graphics = event.getGuiGraphics();
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), flash);
    }

    private static int flashOverlayColor(float currentAge) {
        if (!AdaptionConfig.DIMENSION_IMPACT_FLASH_ENABLED.get()) {
            return 0;
        }

        float strength;
        int rgb;
        if (currentAge < DETONATION_SECONDS) {
            float detonation = Mth.clamp(1f - currentAge / DETONATION_SECONDS, 0f, 1f);
            strength = detonation * detonation;
            rgb = 0x00FFFFFF;
        } else {
            int index = ImpactFrameTiming.flashIndex(currentAge,
                    AdaptionConfig.DIMENSION_IMPACT_FLASH_FRAMES.get(),
                    AdaptionConfig.DIMENSION_IMPACT_FLASH_MS.get());
            if (index < 0) {
                return 0;
            }

            strength = Mth.clamp(AdaptionConfig.DIMENSION_IMPACT_FLASH_STRENGTH.get().floatValue(), 0f, 1f);
            int mode = ImpactFrameTiming.modeAt(currentAge,
                    AdaptionConfig.DIMENSION_IMPACT_MODE_A.get(),
                    AdaptionConfig.DIMENSION_IMPACT_MODE_B.get());
            boolean inverseMode = (mode & 1) != 0;
            boolean paperFlash = ((index & 1) == 0) ^ inverseMode;
            // The shipped pair is pale paper and dark ink. Keep the GUI flash in the same phase
            // as the frame's paper/ink inversion, even when the render pipeline is unavailable.
            rgb = paperFlash ? 0x00F7F5FA : 0x000B0513;
        }

        int alpha = Math.round(Mth.clamp(strength, 0f, 1f) * 255f);
        return (alpha << 24) | rgb;
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        age = Float.MAX_VALUE;
        lastFrameNanos = 0L;
        centreX = 0.5f;
        centreY = 0.5f;
    }
}
