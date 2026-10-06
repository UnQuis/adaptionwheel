package ru.adaptionwheel.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.config.AdaptionConfig;

import java.io.IOException;

/**
 * The Dimension Destroy impact frame: a manga two-tone pass laid over the composited frame when
 * the wearer's own spatial rifts go out.
 *
 * <p>See the class-level notes from the original version for why this hangs off
 * {@code RenderGuiEvent.Pre} and why it is two passes through one program. This revision adds a
 * sharper opening punch (detonation flash, a one-shot camera shove, an FOV pull-in) and fixes two
 * things that were quietly working against the effect's own point:
 *
 * <ul>
 *   <li>{@code ImpactProgress.zw} is the shockwave/speed-line origin in the shader this is paired
 *       with. It was being sent as {@code (0, 0)} - the top-left corner - instead of the screen
 *       centre, so the burst almost certainly fired out of a corner instead of from the player.</li>
 *   <li>The burst's own gain lived entirely in the shader's JSON defaults and never saw
 *       {@code DIMENSION_IMPACT_STRENGTH} at all, so turning the effect down left the shockwave
 *       and speed lines at full strength regardless. Java now owns those gains and scales them by
 *       the same dial as everything else.</li>
 * </ul>
 */
@EventBusSubscriber(modid = AdaptionWheel.MODID, value = Dist.CLIENT)
public final class DimensionImpactFX {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation SHADER =
            ResourceLocation.fromNamespaceAndPath(AdaptionWheel.MODID, "post/dimension_impact");

    private static final float HOLD = 0.06f;
    private static final float FADE = 0.39f;
    private static final float FLASH_FADE = 0.12f;
    private static final float MAX_STEP = 1f / 20f;

    /** Length of the opening white pop, well under a frame at 60 fps counted in wall time rather
     *  than frames, so it does not quietly change length with the player's refresh rate. */
    private static final float DETONATION_SECONDS = 0.05f;

    /**
     * The strobe and the panel's own ink/paper, as literal RGB rather than generic black/white, so
     * the flash reads as the same object as the panel instead of a filter bolted on top of it.
     * Lifted straight from this shader's own {@code ImpactInk}/{@code ImpactPaper} JSON defaults
     * (0.045/0.02/0.075 and 0.97/0.96/0.98) rather than re-guessed, so the two stay in sync if that
     * JSON is ever retuned.
     */
    private static final int VOID_INK_RGB = 0x0B0513;
    private static final int VOID_PAPER_RGB = 0xF7F5FA;

    /**
     * Base shockwave and speed-line gains, scaled by {@code DIMENSION_IMPACT_STRENGTH} every pass
     * (see {@link #pass}) rather than left as a fixed JSON look. Pushed above a "tasteful" default
     * on purpose - a dimension tearing itself apart is the one place this mod gets to overdo it.
     */
    private static final float BURST_RING_GAIN = 1.15f;
    private static final float BURST_LINES_GAIN = 1.05f;
    private static final float BURST_SCREENTONE_CRAWL = 2.5f;
    private static final float BURST_INK_DARKNESS = 1.0f;

    @Nullable
    private static ShaderInstance shader;
    @Nullable
    private static TextureTarget scratch;

    /**
     * Both written by {@link #trigger}, which fires from network packet handling rather than from
     * the render thread, and read every frame from the render thread. {@code volatile} buys
     * visibility and rules out a torn read of {@code lastFrameNanos} (a non-volatile long's
     * read/write is not guaranteed atomic by the JLS); it does not need to be more than that since
     * nothing here does read-modify-write across threads.
     */
    private static volatile float elapsed = Float.MAX_VALUE;
    private static volatile long lastFrameNanos;

    private DimensionImpactFX() {
    }

    public static void register(RegisterShadersEvent event) {
        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(), SHADER, DefaultVertexFormat.POSITION_TEX),
                    instance -> shader = instance);
        } catch (IOException e) {
            LOGGER.error("[{}] Could not load the Dimension Destroy impact shader; the frame will not play",
                    AdaptionWheel.MODID, e);
        }
    }

    public static void trigger() {
        elapsed = 0f;
        lastFrameNanos = 0L;
    }

    @SubscribeEvent
    public static void onRenderGuiPre(RenderGuiEvent.Pre event) {
        elapsed += step();

        if (shader != null && elapsed < HOLD + FADE
                && AdaptionConfig.DIMENSION_IMPACT_FRAME_ENABLED.get()) {
            renderPanel();
        }
        renderFlash(event.getGuiGraphics());
    }

    private static void renderPanel() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.player.isDeadOrDying()) {
            return;
        }

        float current = strengthAt(elapsed);
        float progress = Math.min(elapsed / (HOLD + FADE), 1f);
        float flashAmount = Math.max(0f, 1f - elapsed / FLASH_FADE);
        float configStrength = AdaptionConfig.DIMENSION_IMPACT_STRENGTH.get().floatValue();

        RenderTarget main = mc.getMainRenderTarget();
        TextureTarget target = scratchFor(main.width, main.height);

        target.bindWrite(true);
        pass(main.getColorTextureId(), target.width, target.height, 0f, 0f, 0f, 1f, 0f, 0f);

        main.bindWrite(true);
        pass(target.getColorTextureId(), main.width, main.height,
                current * configStrength,
                AdaptionConfig.DIMENSION_IMPACT_ABERRATION.get().floatValue(),
                flashAmount * 0.35f,
                1f, progress, configStrength);
    }

    /**
     * The anime flash: a hard white detonation pop, then a black/white (here: ink/paper) strobe.
     * Both are driven off {@code elapsed} rather than a per-rendered-frame counter - see the
     * original class notes on why a frame-counted strobe is a strobe-light hazard and a wall-clock
     * one is not.
     */
    private static void renderFlash(GuiGraphics graphics) {
        if (!AdaptionConfig.DIMENSION_IMPACT_FRAME_ENABLED.get()
                || !AdaptionConfig.DIMENSION_IMPACT_FLASH_ENABLED.get()) {
            return;
        }

        if (elapsed < DETONATION_SECONDS) {
            // The one true "it just happened" frame: brighter and flatter than the strobe below,
            // and gone in three frames at 60 fps - a flashbulb, not part of the animation.
            float deto = 1f - elapsed / DETONATION_SECONDS;
            int alpha = Mth.clamp(Math.round(deto * deto * 255f), 0, 255);
            graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), (alpha << 24) | 0xFFFFFF);
        }

        float duration = AdaptionConfig.DIMENSION_IMPACT_FLASH_MS.get().floatValue() / 1000f;
        if (elapsed >= duration) {
            return;
        }
        int frames = Math.max(1, AdaptionConfig.DIMENSION_IMPACT_FLASH_FRAMES.get());
        int index = Mth.clamp((int) (elapsed / duration * frames), 0, frames - 1);

        float alpha = Mth.clamp(AdaptionConfig.DIMENSION_IMPACT_FLASH_STRENGTH.get().floatValue(), 0f, 1f);
        int argb = ((int) (alpha * 255f) << 24) | (index % 2 == 0 ? VOID_PAPER_RGB : VOID_INK_RGB);
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), argb);
    }

    /**
     * Camera shake, running on the panel's own clock, plus a one-shot directional shove in the
     * opening {@link #DETONATION_SECONDS} - the oscillation alone reads as a tremor; a single
     * push riding on top of it in the first instant is what reads as the actual hit landing.
     */
    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (shader == null || elapsed >= HOLD + FADE
                || !AdaptionConfig.DIMENSION_IMPACT_FRAME_ENABLED.get()) {
            return;
        }

        float power = AdaptionConfig.DIMENSION_IMPACT_SHAKE_ENABLED.get()
                ? strengthAt(elapsed) * AdaptionConfig.DIMENSION_IMPACT_SHAKE_STRENGTH.get().floatValue()
                : 0f;
        float yaw = event.getYaw();
        float pitch = event.getPitch();
        if (power > 0.01f) {
            float t = elapsed * 47f;
            yaw += (float) (Math.sin(t) + Math.sin(t * 2.17f) * 0.5f) * power;
            pitch += (float) (Math.cos(t * 1.31f) + Math.sin(t * 2.83f) * 0.5f) * power;
            event.setRoll(event.getRoll() + (float) Math.sin(t * 0.87f) * power * 0.8f);

            if (elapsed < DETONATION_SECONDS) {
                float snap = 1f - elapsed / DETONATION_SECONDS;
                pitch += snap * power * 6f;
            }
        }
        event.setYaw(yaw);
        event.setPitch(pitch);
    }

    /**
     * A quick inward pull of the FOV at the moment of detonation - the camera gets yanked toward
     * the rupture rather than knocked back from it, which is the "something is being destroyed"
     * register rather than the "I got hit" one.
     *
     * <p>Flagged: I have not been able to confirm the exact numeric type
     * {@code ViewportEvent.ComputeFov#getFOV()}/{@code #setFOV} use on this NeoForge version
     * (float vs double) without running the game. If this does not compile as written, change
     * only the local type below to match - the logic does not depend on which it is.
     */
    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (shader == null || elapsed >= HOLD + FADE
                || !AdaptionConfig.DIMENSION_IMPACT_FRAME_ENABLED.get()
                || !AdaptionConfig.DIMENSION_IMPACT_FOV_PUNCH_ENABLED.get()) {
            return;
        }
        float power = strengthAt(elapsed) * AdaptionConfig.DIMENSION_IMPACT_FOV_PUNCH_STRENGTH.get().floatValue();
        if (power <= 0.001f) {
            return;
        }
        float fov = (float) event.getFOV();
        event.setFOV(fov * (1f - power));
    }

    private static float strengthAt(float seconds) {
        if (seconds <= HOLD) {
            return 1f;
        }
        float fade = (seconds - HOLD) / FADE;
        if (fade >= 1f) {
            return 0f;
        }
        float remaining = 1f - fade;
        return remaining * remaining * remaining;
    }

    private static float step() {
        long now = System.nanoTime();
        long previous = lastFrameNanos;
        if (previous == 0L) {
            lastFrameNanos = now;
            return 0f;
        }
        float delta = (now - previous) / 1_000_000_000f;
        lastFrameNanos = now;
        return Math.min(delta, MAX_STEP);
    }

    private static TextureTarget scratchFor(int width, int height) {
        TextureTarget target = scratch;
        if (target == null) {
            target = new TextureTarget(width, height, false, false);
            target.setFilterMode(9729);
            scratch = target;
        } else if (target.width != width || target.height != height) {
            target.resize(width, height, false);
            target.setFilterMode(9729);
        }
        return target;
    }

    /**
     * One fullscreen quad through the impact program. {@code passStrength <= 0} makes it a plain
     * copy. {@code burstGain} is the user's overall strength dial, applied here to the shockwave
     * and speed-line gains so turning the effect down turns the whole thing down - see the class
     * notes on why that was not previously true.
     */
    private static void pass(int sourceTexture, int width, int height,
                             float passStrength, float aberration, float flashAmount, float edgeGain,
                             float progress, float burstGain) {
        ShaderInstance active = shader;
        if (active == null) {
            return;
        }
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.disableScissor();
        RenderSystem.depthMask(false);

        active.setSampler("Sampler0", sourceTexture);
        active.safeGetUniform("ImpactSize").set((float) width, (float) height);
        active.safeGetUniform("ImpactParams").set(passStrength, aberration, flashAmount, edgeGain);
        // zw is the shockwave/speed-line origin in screen uv. This was (0, 0) - the top-left
        // corner - and is now the screen centre, where the player actually is.
        active.safeGetUniform("ImpactProgress").set(progress, 0f, 0.5f, 0.5f);
        active.safeGetUniform("ImpactBurst").set(
                BURST_RING_GAIN * burstGain,
                BURST_LINES_GAIN * burstGain,
                BURST_SCREENTONE_CRAWL,
                BURST_INK_DARKNESS);
        active.apply();

        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.addVertex(0f, 0f, 0f).setUv(0f, 0f);
        buffer.addVertex((float) width, 0f, 0f).setUv(1f, 0f);
        buffer.addVertex((float) width, (float) height, 0f).setUv(1f, 1f);
        buffer.addVertex(0f, (float) height, 0f).setUv(0f, 1f);
        BufferUploader.draw(buffer.buildOrThrow());

        active.clear();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }
}