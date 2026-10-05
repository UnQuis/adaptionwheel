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
 * <p>It hangs off {@code RenderGuiEvent.Pre} rather than a mixin. By the time that fires, {@code
 * GameRenderer.render} has finished the world, the hand <i>and</i> vanilla's own post chain
 * (blaze rods, nausea), and has re-bound the main render target; no GUI layer has drawn yet. So
 * the frame is complete, the world underneath is in its final graded state, the HUD stays legible
 * on top of it, and the GUI stage sets its own GL state afterwards without having to be put back
 * the way this class found it.
 *
 * <p><b>Two passes, one program.</b> The two-tone threshold needs to read neighbouring pixels, and
 * reading the framebuffer you are drawing into is undefined, so the frame is first copied into a
 * scratch target. Pass A is this same shader with {@code strength = 0}, which early-outs to a plain
 * texture fetch — one sample per pixel instead of the thirteen the effect needs.
 */
@EventBusSubscriber(modid = AdaptionWheel.MODID, value = Dist.CLIENT)
public final class DimensionImpactFX {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation SHADER =
            ResourceLocation.fromNamespaceAndPath(AdaptionWheel.MODID, "post/dimension_impact");

    /**
     * The panel's life, in seconds: a short full-strength snap so the hit registers as a punch,
     * then a linear fade. {@code HOLD + FADE} is 450 ms end to end, which is the window the
     * original game held its impact frames for.
     *
     * <p>Wall-clock, not per frame. A per-frame decay would hold the panel for a fixed <i>number
     * of frames</i>, which is the same wall-clock duration on a 60 Hz screen and a fraction of
     * it on a 240 Hz one — so the effect would quietly change length with the player's hardware.
     */
    private static final float HOLD = 0.06f;
    private static final float FADE = 0.39f;
    /** The white pop on top of the panel is much shorter than the panel itself. */
    private static final float FLASH_FADE = 0.12f;
    /** Longest step we honour, so a stall cannot dump the whole panel in one frame. */
    private static final float MAX_STEP = 1f / 20f;

    @Nullable
    private static ShaderInstance shader;
    @Nullable
    private static TextureTarget scratch;

    /** Seconds since the last trigger, parked past the end once the panel has played out. */
    private static float elapsed = Float.MAX_VALUE;
    private static long lastFrameNanos;

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

    /**
     * Restarts the panel. Called from {@code RiftImpactPayload} when the server reports that <i>this</i>
     * player's swing actually spawned rifts.
     *
     * <p>It used to be driven by scanning the level for {@code SpatialRiftProjectile} owned by the local
     * player, which could never fire: {@code Projectile.getOwner()} resolves its cached owner only, and
     * the spawn packet carries no owner, so on the client it always returned {@code null}. The panel was
     * therefore dead code. A clientbound packet is also the honest signal — the server already knows which
     * swing produced the rifts, and it removes the ping-dependent guesswork entirely.
     */
    public static void trigger() {
        elapsed = 0f;
        lastFrameNanos = 0L;
    }

    @SubscribeEvent
    public static void onRenderGuiPre(RenderGuiEvent.Pre event) {
        // Advanced before any early return so the clock stays live even while nothing is drawn; a stale
        // timestamp would otherwise dump a whole panel into the first frame after the next trigger.
        elapsed += step();

        if (shader != null && elapsed < HOLD + FADE
                && AdaptionConfig.DIMENSION_IMPACT_FRAME_ENABLED.get()) {
            renderPanel();
        }
        // Drawn after the panel and independently of it, so the punch still lands if the post shader
        // ever failed to load.
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

        RenderTarget main = mc.getMainRenderTarget();
        TextureTarget target = scratchFor(main.width, main.height);

        target.bindWrite(true);
        pass(main.getColorTextureId(), target.width, target.height, 0f, 0f, 0f, 1f, 0f);

        main.bindWrite(true);
        pass(target.getColorTextureId(), main.width, main.height,
                current * AdaptionConfig.DIMENSION_IMPACT_STRENGTH.get().floatValue(),
                AdaptionConfig.DIMENSION_IMPACT_ABERRATION.get().floatValue(),
                flashAmount * 0.35f,
                1f, progress);
    }

    /**
     * The anime flash: a full-screen white/black alternation over a measured duration.
     *
     * <p>The frame index is derived from {@code elapsed} and never from a per-rendered-frame
     * counter. That is the entire safety question in this feature. Incrementing an index once per
     * drawn frame gives 12 colour changes/second at 60 fps, inside the 3-25 Hz band where flicker
     * reads as motion and provokes photosensitive migraine — that is a strobe, and it is why the
     * original mod's own defaults are modest. Dividing a fixed duration into a fixed number of steps
     * gives 20 fps for its 2-over-100 ms, which is just animation and is indistinguishable from the
     * same frames played by hand at 60 fps.
     *
     * <p>Hard cut at the end rather than a fade: the sequence is punctuation on the front of the
     * panel, and cutting is what makes it read as a hit instead of a dissolve.
     */
    private static void renderFlash(GuiGraphics graphics) {
        if (!AdaptionConfig.DIMENSION_IMPACT_FRAME_ENABLED.get()
                || !AdaptionConfig.DIMENSION_IMPACT_FLASH_ENABLED.get()) {
            return;
        }
        float duration = AdaptionConfig.DIMENSION_IMPACT_FLASH_MS.get().floatValue() / 1000f;
        if (elapsed >= duration) {
            return;
        }
        int frames = Math.max(1, AdaptionConfig.DIMENSION_IMPACT_FLASH_FRAMES.get());
        int index = Mth.clamp((int) (elapsed / duration * frames), 0, frames - 1);

        float alpha = Mth.clamp(AdaptionConfig.DIMENSION_IMPACT_FLASH_STRENGTH.get().floatValue(), 0f, 1f);
        int argb = ((int) (alpha * 255f) << 24) | (index % 2 == 0 ? 0xFFFFFF : 0x000000);
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), argb);
    }

    /**
     * Camera shake, running on the panel's own clock.
     *
     * <p>The obvious way to write this is {@code System.nanoTime()} for the phase and a per-frame
     * multiplier for the decay. That is wrong twice over: the amplitude then depends on your
     * framerate while the oscillation rate does not, so the shake's shape quietly changes with your
     * hardware. Taking the phase from {@code elapsed} instead makes it deterministic — the same
     * envelope on any machine, and it cannot outlive the panel because it is literally the same
     * curve.
     *
     * <p>{@code setRoll} is real rather than a no-op: NeoForge patches {@code Camera} to carry a
     * {@code roll} field and a {@code setRotation(yaw, pitch, roll)} overload, and fires this event
     * from {@code Camera.setup} with roll seeded to 0.
     */
    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (shader == null || elapsed >= HOLD + FADE
                || !AdaptionConfig.DIMENSION_IMPACT_FRAME_ENABLED.get()) {
            return;
        }

        // No hitstop here on purpose. See the note on the shake above.
        float power = AdaptionConfig.DIMENSION_IMPACT_SHAKE_ENABLED.get()
                ? strengthAt(elapsed) * AdaptionConfig.DIMENSION_IMPACT_SHAKE_STRENGTH.get().floatValue()
                : 0f;
        float yaw = event.getYaw();
        float pitch = event.getPitch();
        if (power > 0.01f) {
            // Two incommensurate frequencies per axis, so the motion never visibly repeats.
            float t = elapsed * 47f;
            yaw += (float) (Math.sin(t) + Math.sin(t * 2.17f) * 0.5f) * power;
            pitch += (float) (Math.cos(t * 1.31f) + Math.sin(t * 2.83f) * 0.5f) * power;
            event.setRoll(event.getRoll() + (float) Math.sin(t * 0.87f) * power * 0.8f);
        }
        event.setYaw(yaw);
        event.setPitch(pitch);
    }

    /**
     * Full-strength snap, then a curve that stays near full and then falls off a cliff.
     *
     * <p>A straight fade is what made the panel read as soft: it spends its whole life half
     * faded, which looks like a dissolve. {@code 1 - fade^3} keeps the frame at ~88% halfway
     * through the fade and still has slope at the end, so the panel is still there and then
     * simply cuts. This mirrors what the original mod's own post effects did — they took a
     * {@code uProgress} and drew against it, rather than only fading a uniform.
     */
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

    /** Seconds since the previous rendered frame, clamped so a stall cannot skip the panel. */
    private static float step() {
        long now = System.nanoTime();
        if (lastFrameNanos == 0L) {
            lastFrameNanos = now;
            return 0f;
        }
        float delta = (now - lastFrameNanos) / 1_000_000_000f;
        lastFrameNanos = now;
        return Math.min(delta, MAX_STEP);
    }

    private static TextureTarget scratchFor(int width, int height) {
        TextureTarget target = scratch;
        if (target == null) {
            target = new TextureTarget(width, height, false, false);
            target.setFilterMode(9729); // GL_LINEAR
            scratch = target;
        } else if (target.width != width || target.height != height) {
            target.resize(width, height, false);
            target.setFilterMode(9729);
        }
        return target;
    }

    /** One fullscreen quad through the impact program. {@code strength <= 0} makes it a plain copy. */
    private static void pass(int sourceTexture, int width, int height,
                             float passStrength, float aberration, float flashAmount, float edgeGain,
                             float progress) {
        ShaderInstance active = shader;
        if (active == null) {
            return;
        }
        // The world and post passes leave state behind that would silently drop an opaque
        // fullscreen quad. Culling is the one that bites: a RenderType turned it on, and this
        // quad's winding is clockwise in NDC once the vertex shader flips Y, so without this
        // line the whole pass is back-face culled and draws nothing at all. The GUI stage
        // re-establishes whatever it needs afterwards, so nothing has to be restored by hand.
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.disableScissor();
        RenderSystem.depthMask(false);

        active.setSampler("Sampler0", sourceTexture);
        active.safeGetUniform("ImpactSize").set((float) width, (float) height);
        active.safeGetUniform("ImpactParams").set(passStrength, aberration, flashAmount, edgeGain);
        active.safeGetUniform("ImpactProgress").set(progress, 0f, 0f, 0f);
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