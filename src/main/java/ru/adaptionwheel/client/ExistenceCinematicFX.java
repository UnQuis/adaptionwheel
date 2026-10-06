package ru.adaptionwheel.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.minecraft.client.player.Input;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.cinematic.ExistenceCinematicTiming;
import ru.adaptionwheel.config.AdaptionConfig;
import ru.adaptionwheel.network.ExistenceCinematicPayload;
import ru.adaptionwheel.sound.ModSounds;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The Existence cinematic: the reward sequence that plays once the analysis bar has filled.
 *
 * <p>Three hooks, because the original's sequence is three different kinds of thing at once:
 * <ul>
 *   <li><b>Gakon sprites</b> — world space, on the player, via {@code RenderLivingEvent.Post}. That
 *       event hands over a {@code PoseStack} already carrying the entity transform and a
 *       {@code MultiBufferSource} already inside the right batch, so the sprites depth-test against
 *       the world for free and no screen projection is needed at any point.</li>
 *   <li><b>Cinematic bars and the dialogue</b> — screen space, via {@code RenderGuiEvent.Post}.
 *       Post, not Pre: the bars are meant to sit <i>over</i> the HUD, the way they covered the
 *       hotbar in the original. This is the opposite of the impact frame, which deliberately draws
 *       under the HUD.</li>
 *   <li><b>Camera punch</b> — {@code ViewportEvent.ComputeCameraAngles}, the same event the impact
 *       frame's shake uses.</li>
 * </ul>
 *
 * <p><b>The adaptation is not granted here.</b> The original grants it at tick 540 of the sequence,
 * which means losing the reward if the player disconnects or the boss despawns mid-cinematic. The
 * grant stays server-side and immediate; this is presentation.
 */
@EventBusSubscriber(modid = AdaptionWheel.MODID, value = Dist.CLIENT)
public final class ExistenceCinematicFX {

    private static final ResourceLocation GAKON =
            ResourceLocation.fromNamespaceAndPath(AdaptionWheel.MODID, "textures/entity/gakon.png");
    /** Half a texel-free 64px sprite at this scale is roughly the original's footprint. */
    private static final float GAKON_SCALE = 0.5f;
    /** How far above the player's feet the sprites sit, in blocks. */
    private static final float GAKON_HEIGHT = 0.45f;
    /** Random scatter around the player, in blocks, matching the original's 70px fan. */
    private static final float GAKON_SPREAD = 0.9f;

    private static final List<GakonSprite> sprites = new ArrayList<>();
    private static final Random RANDOM = new Random();

    private static boolean active;
    private static long startNanos;
    private static String bossName = "";
    private static int levelsFired;
    private static boolean finaleFired;
    private static boolean linesFired;
    private static float punch;

    private ExistenceCinematicFX() {
    }

    private record GakonSprite(int bornMs, int lifeMs, float offsetX, float offsetY, float rotation, float scale) {
    }

    /** Entry point, wired to the clientbound payload. */
    public static void start(ExistenceCinematicPayload payload) {
        if (!AdaptionConfig.EXISTENCE_CINEMATIC_ENABLED.get() || !Minecraft.getInstance().isWindowActive()) {
            return;
        }
        active = true;
        startNanos = System.nanoTime();
        bossName = payload.bossName();
        levelsFired = 0;
        finaleFired = false;
        linesFired = false;
        punch = 0f;
        sprites.clear();

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player != null) {
            // The original dismounted and cut grappling hooks so the player could not fight their
            // way out of the sequence; here that means dismounting and closing whatever is open.
            player.stopRiding();
            player.releaseUsingItem();
        }
        if (mc.screen != null) {
            mc.setScreen(null);
        }
        if (mc.player != null) {
            mc.player.playSound(SoundEvents.BEACON_POWER_SELECT, 0.7f, 1.4f);
        }
    }

    /** Milliseconds since {@link #start}, or -1 when nothing is playing. */
    private static int elapsedMs() {
        return active ? (int) ((System.nanoTime() - startNanos) / 1_000_000L) : -1;
    }

    /**
     * Advances the timeline and fires everything that is due.
     *
     * <p>Called from the GUI pass, which runs every rendered frame. Everything is derived from
     * elapsed wall clock rather than counted down per frame, so a slow machine sees the same
     * sequence at the same speed — and a level boundary is fired by comparing against the shared
     * schedule, so it can never be skipped or double-fired by a frame-rate change.
     */
    private static void advance(int elapsed) {
        int passed = ExistenceCinematicTiming.levelsPassed(elapsed);
        while (levelsFired < passed) {
            levelsFired++;
            int levelIndex = levelsFired - 1;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.playSound(ModSounds.ADAPT_VOICE.get(), 1f, 1f);
            }
            int at = ExistenceCinematicTiming.LEVEL_MS[levelIndex];
            spawnGakon(elapsed, ExistenceCinematicTiming.GAKON_LIFE_MS,
                    (RANDOM.nextFloat() - 0.5f) * GAKON_SPREAD * 2f, GAKON_HEIGHT,
                    (RANDOM.nextFloat() - 0.5f) * 0.3f, 0.9f + RANDOM.nextFloat() * 0.2f);
            spiralDust();
        }

        if (!finaleFired && ExistenceCinematicTiming.finaleDue(elapsed)) {
            finaleFired = true;
            onFinale();
        }

        if (!linesFired && elapsed >= ExistenceCinematicTiming.LINE_MIDDLE_MS) {
            linesFired = true;
        }

        punch = Math.max(0f, punch - 0.06f);

        sprites.removeIf(s -> elapsed - s.bornMs >= s.lifeMs);
        if (elapsed >= ExistenceCinematicTiming.TOTAL_MS) {
            active = false;
            sprites.clear();
        }
    }

    private static final int EXISTENCE_GAUGE_WIDTH = 220;
    private static final int EXISTENCE_GAUGE_HEIGHT = 10;

    /**
     * Climbs 0..8 over the cinematic, snapping to a full bar at the exact instant each of the
     * eight ADAPT_VOICE cues fires, then immediately restarting the next segment at zero. Driven
     * by the same boundaries as the Gakon sprites and dust bursts, so it can't visually drift from
     * what the player actually hears.
     */
    private static void drawExistenceGauge(GuiGraphics graphics, Minecraft mc, int width, int height, int elapsed) {
        float visibility = ExistenceCinematicTiming.barFraction(elapsed);
        if (visibility <= 0f) {
            return;
        }
        int level = ExistenceCinematicTiming.existenceLevel(elapsed);
        float fraction = ExistenceCinematicTiming.existenceLevelFraction(elapsed);
        int alpha = Math.round(255 * visibility);

        int x = width / 2 - EXISTENCE_GAUGE_WIDTH / 2;
        int y = height / 2 + 46;

        String label = "EXISTENCE LEVEL " + level + "/8";
        graphics.drawString(mc.font, label, width / 2 - mc.font.width(label) / 2, y - 12,
                (alpha << 24) | 0xFFFFFF, true);

        graphics.fill(x - 1, y - 1, x + EXISTENCE_GAUGE_WIDTH + 1, y + EXISTENCE_GAUGE_HEIGHT + 1, alpha << 24);
        int filled = Math.round(EXISTENCE_GAUGE_WIDTH * fraction);
        if (filled > 0) {
            int rainbow = AdaptionHud.rainbowColor();
            graphics.fill(x, y, x + filled, y + EXISTENCE_GAUGE_HEIGHT, (alpha << 24) | (rainbow & 0xFFFFFF));
        }
    }

    /** The finale: the original's punch, its four dust patterns and the largest sprite. */
    private static void onFinale() {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        int elapsed = elapsedMs();
        if (player == null) {
            return;
        }
        player.playSound(ModSounds.ADAPT_VOICE.get(), 3f, 0.7f);
        player.playSound(ModSounds.REF.get(), 2f, 0.6f);
        player.playSound(ModSounds.SOE_HIT_1.get(), 1.5f, 0.8f);
        punch = 1f;
        spawnGakon(elapsed, ExistenceCinematicTiming.GAKON_FINALE_LIFE_MS,
                0f, GAKON_HEIGHT, 0f, 1.6f);

        var level = player.level();
        double cx = player.getX();
        double cy = player.getY() + GAKON_HEIGHT * 2f;
        double cz = player.getZ();
        for (int i = 0; i < 150; i++) {
            double angle = Math.PI * 2 * i / 150.0;
            level.addParticle(ParticleTypes.SCULK_SOUL, cx, cy, cz,
                    Math.cos(angle) * 0.8, Math.sin(angle) * 0.8, 0);
        }
        for (int i = 0; i < 200; i++) {
            double angle = Math.PI * 2 * i / 200.0;
            level.addParticle(ParticleTypes.END_ROD, cx, cy, cz,
                    Math.cos(angle) * 1.5, Math.sin(angle) * 1.5, 0);
        }
        for (int i = 0; i < 120; i++) {
            double angle = RANDOM.nextFloat() * Math.PI * 2;
            double dist = 0.1 + RANDOM.nextFloat() * 0.5;
            level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                    cx + Math.cos(angle) * dist, cy, cz + Math.sin(angle) * dist,
                    Math.cos(angle) * 0.4, RANDOM.nextFloat() * 0.4, Math.sin(angle) * 0.4);
        }
        for (int i = 0; i < 60; i++) {
            level.addParticle(ParticleTypes.SNOWFLAKE,
                    cx + (RANDOM.nextFloat() - 0.5f) * 4.0, cy + 2f + RANDOM.nextFloat() * 3.0,
                    cz + (RANDOM.nextFloat() - 0.5f) * 4.0,
                    0, -0.2 - RANDOM.nextFloat() * 0.15, 0);
        }
    }

    /** Twenty motes drifting inward, as at each rising level. */
    private static void spiralDust() {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) {
            return;
        }
        var level = player.level();
        double cx = player.getX();
        double cy = player.getY() + GAKON_HEIGHT * 2f;
        double cz = player.getZ();
        for (int i = 0; i < 20; i++) {
            double angle = RANDOM.nextFloat() * Math.PI * 2;
            double dist = 0.2 + RANDOM.nextFloat() * 0.6;
            double dx = Math.cos(angle) * dist;
            double dz = Math.sin(angle) * dist;
            level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, cx + dx, cy, cz + dz,
                    -dx * 0.08, 0, -dz * 0.08);
        }
    }

    private static void spawnGakon(int born, int life, float offsetX, float offsetY,
                                   float rotation, float scale) {
        sprites.add(new GakonSprite(born, life, offsetX, offsetY, rotation, scale));
    }

    @SubscribeEvent
    public static void onRenderLiving(RenderLivingEvent.Post<?, ?> event) {
        int elapsed = elapsedMs();
        if (elapsed < 0 || sprites.isEmpty() || !(event.getEntity() instanceof Player player)) {
            return;
        }
        if (player != Minecraft.getInstance().player) {
            return;
        }
        EntityRenderer<?> renderer = event.getRenderer();
        if (renderer == null) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource buffers = event.getMultiBufferSource();
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityTranslucent(GAKON));
        float time = player.level().getGameTime() + event.getPartialTick();

        for (GakonSprite sprite : sprites) {
            int age = elapsed - sprite.bornMs;
            float progress = 1f - age / (float) sprite.lifeMs;
            float alpha = fadeEnds(progress);
            if (alpha <= 0.01f) {
                continue;
            }
            // The original bobbed each sprite by its own offset, so a fan of them breathes
            // out of phase instead of pulsing as one block.
            float bob = Mth.sin(time * 4f + sprite.offsetX) * 0.02f;
            poseStack.pushPose();
            poseStack.translate(sprite.offsetX, sprite.offsetY + bob, 0f);
            poseStack.mulPose(com.mojang.math.Axis.ZP.rotation(sprite.rotation));
            poseStack.scale(GAKON_SCALE * sprite.scale, GAKON_SCALE * sprite.scale, 1f);
            int argb = (int) (Mth.clamp(alpha, 0f, 1f) * 255f) << 24 | 0xFFFFFF;
            quad(consumer, poseStack, argb);
            poseStack.popPose();
        }
    }

    /** The original's 20%-in / 20%-out window, unchanged. */
    private static float fadeEnds(float progress) {
        if (progress < 0.2f) {
            return progress / 0.2f;
        }
        if (progress > 0.8f) {
            return (1f - progress) / 0.2f;
        }
        return 1f;
    }

    private static void quad(VertexConsumer consumer, PoseStack poseStack, int argb) {
        // The 64x66 texture, drawn about its centre.
        float halfWidth = 32f / 64f;
        float halfHeight = 33f / 66f;
        vertex(consumer, poseStack, -halfWidth, halfHeight, 0f, 0f, argb);
        vertex(consumer, poseStack, -halfWidth, -halfHeight, 0f, 1f, argb);
        vertex(consumer, poseStack, halfWidth, -halfHeight, 1f, 1f, argb);
        vertex(consumer, poseStack, halfWidth, halfHeight, 1f, 0f, argb);
    }

    private static void vertex(VertexConsumer consumer, PoseStack poseStack,
                               float x, float y, float u, float v, int argb) {
        consumer.addVertex(poseStack.last(), x, y, 0f)
                .setColor(argb)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(0f, 0f, 1f);
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        int elapsed = elapsedMs();
        if (elapsed < 0) {
            return;
        }
        advance(elapsed);

        GuiGraphics graphics = event.getGuiGraphics();
        Minecraft mc = Minecraft.getInstance();
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();

        float bars = ExistenceCinematicTiming.barFraction(elapsed);
        if (bars > 0f) {
            int barHeight = Math.round(height * ExistenceCinematicTiming.BAR_MAX_FRACTION * bars);
            if (barHeight > 0) {
                graphics.fill(0, 0, width, barHeight, 0xFF000000);
                graphics.fill(0, height - barHeight, width, height, 0xFF000000);
            }
        }

        drawLine(graphics, mc, ExistenceCinematicTiming.LINE_OPENING_MS, elapsed,
                "Kind of adaptation just started...", 0xFFFFFFFF);
        drawLine(graphics, mc, ExistenceCinematicTiming.LINE_MIDDLE_MS, elapsed,
                "No way...", 0xFFFFFFFF);
        drawLine(graphics, mc, ExistenceCinematicTiming.FINALE_MS, elapsed,
                "To my very existence!?", 0xFFFF5555);

        drawExistenceGauge(graphics, mc, width, height, elapsed);
    }

    /**
     * Shows {@code text} for the half-second after {@code at}, then lets it go. The original used
     * floating combat text over the boss; the action bar is the closest thing that does not require
     * a screen projection.
     */
    private static void drawLine(GuiGraphics graphics, Minecraft mc, int at, int elapsed,
                                 String text, int colour) {
        int age = elapsed - at;
        if (age < 0 || age > 1000) {
            return;
        }
        int alpha = age > 800 ? Math.round((1f - (age - 800) / 200f) * 255f) : 255;
        graphics.drawString(mc.font, text, graphics.guiWidth() / 2 - mc.font.width(text) / 2,
                graphics.guiHeight() / 3, (alpha << 24) | (colour & 0x00FFFFFF), true);
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (punch <= 0f) {
            return;
        }
        // Shared shape with the impact frame's shake: two incommensurate rates so it never
        // visibly repeats, driven by the punch envelope rather than by wall clock.
        float t = punch * 40f;
        event.setYaw(event.getYaw() + (float) Math.sin(t * 1.7f) * punch * 6f);
        event.setPitch(event.getPitch() + (float) Math.cos(t * 2.3f) * punch * 4f);
        event.setRoll(event.getRoll() + (float) Math.sin(t * 1.1f) * punch * 5f);
    }

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!active) {
            return;
        }
        Input input = event.getInput();
        input.up = false;
        input.down = false;
        input.left = false;
        input.right = false;
        input.jumping = false;
        input.shiftKeyDown = false;
        input.forwardImpulse = 0f;
        input.leftImpulse = 0f;
    }
}