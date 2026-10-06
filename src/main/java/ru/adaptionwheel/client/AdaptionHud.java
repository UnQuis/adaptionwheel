package ru.adaptionwheel.client;

import net.minecraft.client.Minecraft;
import ru.adaptionwheel.data.PlayerAdaption;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.category.Concepts;
import ru.adaptionwheel.config.AdaptionConfig;
import ru.adaptionwheel.data.AdaptionTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@EventBusSubscriber(modid = AdaptionWheel.MODID, value = net.neoforged.api.distmarker.Dist.CLIENT)
public class AdaptionHud {

    public static final ResourceLocation WHEEL_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(AdaptionWheel.MODID, "textures/entity/wheel.png");

    private static final int HEADER_COLOR = 0xFFD700;
    private static final int ROW_WIDTH = 252;
    private static final int ROW_HEIGHT = 36;
    private static final int BAR_WIDTH = 240;
    private static final int BAR_HEIGHT = 8;
    private static final int SPACING = 50;

    /** How fast the bar's drawn width chases its real target. Higher = snappier, lower = silkier. */
    private static final float PROGRESS_SMOOTHING_RATE = 10f;
    /** How long a freshly-appeared row takes to fade and slide into place. */
    private static final float ENTRANCE_SECONDS = 0.25f;

    /**
     * Smoothed bar width per concept and when each concept's row was first seen, both keyed by
     * concept so a row re-triggers its entrance animation if it ever disappears and comes back
     * (a task finishing and a new one of the same kind starting later, for instance) rather than
     * silently skipping it because the key was never forgotten.
     *
     * <p>Pruned every frame in {@link #pruneAnimationState} against whatever is actually showing,
     * so neither map grows for the lifetime of the game session.
     */
    private static final Map<String, Float> DISPLAYED_PROGRESS = new HashMap<>();
    private static final Map<String, Long> FIRST_SEEN_NANOS = new HashMap<>();

    /** Nanosecond origin for {@link #nowSeconds()}, so the float stays small for the life of the
     *  session instead of losing sub-second precision to a huge raw {@code System.nanoTime()}. */
    private static final long START_NANOS = System.nanoTime();
    private static long lastFrameNanos;

    @SubscribeEvent
    public static void render(RenderGuiEvent.Post event) {
        if (!AdaptionConfig.HUD_ENABLED.get() || !ClientAdaption.wearingWheel) {
            return;
        }
        if (ClientAdaption.TASKS.isEmpty() && !ClientAdaption.adversityActive
                && ClientAdaption.EXISTENCE_PROGRESS.isEmpty() && !hasFistRow()) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int opacity = AdaptionConfig.HUD_OPACITY.get();
        float scale = (float) (double) AdaptionConfig.HUD_SCALE.get();

        float dt = step();
        float now = nowSeconds();

        graphics.pose().pushPose();
        graphics.pose().translate(
                25f * scale + AdaptionConfig.HUD_OFFSET_X.get(),
                mc.getWindow().getGuiScaledHeight() * 0.35f + AdaptionConfig.HUD_OFFSET_Y.get(),
                0f);
        graphics.pose().scale(scale, scale, 1f);

        // A slow (~0.6 Hz) brightness breathe on the header. Nowhere near the 3-25 Hz band that
        // reads as a strobe and provokes photosensitive migraine -- see the flash-rate reasoning
        // in DimensionImpactFX, which this deliberately stays well clear of in the same way.
        float headerPulse = 0.85f + 0.15f * (float) Math.sin(now * Math.PI * 1.2);
        int headerOpacity = Math.min(100, Math.round(opacity * 150 / 100f * headerPulse));
        String header = ">>> ADAPTED  " + ClientAdaption.adaptedCount;
        graphics.drawString(font, Component.literal(header), 0, 0,
                withAlpha(HEADER_COLOR, headerOpacity), true);
        graphics.drawString(font, Component.literal("ADAPTATION_ANALYSIS"), 0, font.lineHeight,
                withAlpha(HEADER_COLOR, headerOpacity), true);

        List<Row> rows = new ArrayList<>();
        if (ClientAdaption.adversityActive) {
            rows.add(Row.of(Concepts.ADVERSITY, ClientAdaption.adversityProgress()));
        }
        for (AdaptionTask task : ClientAdaption.TASKS) {
            rows.add(Row.of(task.concept, ClientAdaption.taskProgress(task)));
        }

        for (Map.Entry<String, Integer> entry : ClientAdaption.EXISTENCE_PROGRESS.entrySet()) {
            String bossPath = entry.getKey();
            float progress = ClientAdaption.existenceProgress(bossPath);
            if (progress < 1f) {
                rows.add(Row.of(Concepts.existence(bossPath), progress, true));
            }
        }

        if (hasFistRow()) {
            int tier = ClientAdaption.fistTier();
            int luck = ru.adaptionwheel.category.FistTiers.luckMultiplier(tier);
            String concept = ru.adaptionwheel.category.FistTiers.concept(tier);
            // No leading space here: drawRow's own separator supplies the one space before this,
            // so a bare "- LUCK x2" lines up as "12/34 blocks - LUCK x2" instead of the old
            // "12/34 blocks  - LUCK x2" with its doubled space.
            String suffix = luck > 1 ? "- LUCK x" + luck : null;
            rows.add(buildFistTierRow(concept, fistProgress(),
                    ClientAdaption.fistProgressDone, ClientAdaption.fistProgressTotal, suffix,
                    ClientAdaption.level(concept), tier,
                    ru.adaptionwheel.category.FistTiers.TIER_COUNT,
                    ru.adaptionwheel.category.FistTiers::concept,
                    ru.adaptionwheel.category.FistTiers::color));
        }

        // The row appears only once the punch is trainable -- the first bare-handed hostile kill --
        // and then stays for good. The gate is `combatFistTier()`, derived from the synced LEVELS
        // map, not from the per-kill progress fields: those travel on `CombatFistProgressPayload`,
        // which is only sent when a kill lands and is cleared on logout, so gating on them would
        // make the row vanish again on every relogin after the player had already unlocked it.
        {
            int trained = ClientAdaption.combatFistTier();
            if (trained >= 0) {
                int tierCount = ru.adaptionwheel.category.CombatFistTiers.TIER_COUNT;
                int tier = Math.min(trained, tierCount - 1);
                boolean maxed = ClientAdaption.combatFistProgressTotal <= 0;
                String concept = ru.adaptionwheel.category.CombatFistTiers.concept(tier);
                // No suffix beyond MAX: the meter already prints "0/6 kills" from blocksDone/Total,
                // so a repeated counter suffix would just say the same number twice.
                rows.add(buildFistTierRow(concept, maxed ? 1f : combatFistProgress(),
                        ClientAdaption.combatFistProgressDone, ClientAdaption.combatFistProgressTotal,
                        maxed ? "MAX" : null, ClientAdaption.level(concept), tier, tierCount,
                        ru.adaptionwheel.category.CombatFistTiers::concept,
                        ru.adaptionwheel.category.CombatFistTiers::color));
            }
        }
        rows.sort(Comparator.comparingInt(r -> priority(r.concept)));

        pruneAnimationState(rows);

        int maxDisplay = Math.max(5, (int) (mc.getWindow().getGuiScaledHeight() * 0.6f / (SPACING * scale)));
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            int y = (i + 1) * SPACING;
            if (i >= maxDisplay || y + ROW_HEIGHT > mc.getWindow().getGuiScaledHeight() / scale) {
                graphics.drawString(font, Component.literal("...and " + (rows.size() - i) + " more"),
                        15, y, 0xFFAAAAAA, true);
                break;
            }
            drawRow(graphics, font, row, y, opacity, dt, now);
        }

        graphics.pose().popPose();
    }

    private static void drawRow(GuiGraphics graphics, Font font, Row row, int y, int opacity,
                                float dt, float now) {
        boolean off = !row.rainbow && !ClientAdaption.isEnabled(row.concept);

        long firstSeen = FIRST_SEEN_NANOS.computeIfAbsent(row.concept, k -> System.nanoTime());
        float age = (System.nanoTime() - firstSeen) / 1_000_000_000f;
        float entrance = smoothstep01(age / ENTRANCE_SECONDS);

        // The number shown (meter text, %) is always the ground truth; only the drawn bar width
        // lags slightly behind it. That split is deliberate: the text must never lie about actual
        // progress, but a bar that visibly "catches up" each tick reads as something flowing
        // rather than snapping between discrete steps.
        float displayed = DISPLAYED_PROGRESS.compute(row.concept,
                (k, previous) -> previous == null ? row.progress
                        : smoothTowards(previous, row.progress, dt, PROGRESS_SMOOTHING_RATE));

        int rowOpacity = Math.round(opacity * entrance);
        float slideX = (1f - entrance) * 16f;

        graphics.pose().pushPose();
        graphics.pose().translate(slideX, 0f, 0f);

        int color = row.rainbow ? rainbowColor()
                : (off ? 0xFF9A9A9A : (row.overrideColor != null ? row.overrideColor : Concepts.color(row.concept)));
        int barW = Math.min(BAR_WIDTH, Math.max(0, Math.round(BAR_WIDTH * displayed)));

        graphics.fill(9, y - 2, 9 + ROW_WIDTH, y - 2 + ROW_HEIGHT, withAlpha(color, rowOpacity * 15 / 100));

        String name = Concepts.chatName(row.concept).getString();
        String tag;
        if (row.rainbow) {
            tag = " [EXISTENCE]";
        } else if (Concepts.isOneTime(row.concept)) {
            tag = " [NEW]";
        } else {
            int level = ClientAdaption.LEVELS.getOrDefault(row.concept, 0);
            if (level >= PlayerAdaption.MAX_LEVEL) {
                String next = nextStageName(row.concept);
                tag = next == null ? " [Lv.MAX]"
                        : " [Lv.MAX > " + Concepts.chatName(next).getString() + " Lv.1]";
            } else {
                tag = " [Lv." + level + " > " + (level + 1) + "]";
            }
        }
        if (off) {
            tag = " [OFF]";
        }
        String meter = row.blocksTotal > 0
                ? row.blocksDone + "/" + row.blocksTotal + " " + row.unit()
                : String.format("%.1f%%", row.progress * 100f);
        String suffix = row.suffix == null ? "" : " " + row.suffix;
        graphics.drawString(font, name + tag + " : " + meter + suffix, 15, y, withAlpha(color, rowOpacity), true);

        graphics.fill(15, y + 24, 15 + BAR_WIDTH, y + 24 + BAR_HEIGHT, withAlpha(0x000000, rowOpacity * 60 / 100));

        if (row.rainbow) {
            int segCount = Math.max(1, barW);
            for (int x = 0; x < segCount; x++) {
                int segColor = rainbowColor((int) (x + System.currentTimeMillis() / 10));
                graphics.fill(15 + x, y + 24, 15 + x + 1, y + 24 + BAR_HEIGHT,
                        withAlpha(segColor, rowOpacity * 90 / 100));
            }
        } else if (row.split) {
            // Two-tone, and only at a material handover: left is the stage being finished, right
            // is the one being moved into, both ways round -- see handedOverColours for why.
            int half = BAR_WIDTH / 2;
            graphics.fill(15, y + 24, 15 + half, y + 24 + BAR_HEIGHT,
                    withAlpha(row.splitLeft, rowOpacity * 90 / 100));
            graphics.fill(15 + half, y + 24, 15 + BAR_WIDTH, y + 24 + BAR_HEIGHT,
                    withAlpha(row.splitRight, rowOpacity * 90 / 100));
        } else {
            graphics.fill(15, y + 24, 15 + barW, y + 24 + BAR_HEIGHT, withAlpha(color, rowOpacity * 90 / 100));
            drawShimmer(graphics, 15, y + 24, barW, BAR_HEIGHT, now, rowOpacity, off);
        }

        if (!row.rainbow && !off && row.progress >= 0.9f) {
            drawCompletionPulse(graphics, 15, y + 24, BAR_WIDTH, BAR_HEIGHT, now, rowOpacity);
        }

        graphics.pose().popPose();
    }

    /**
     * A bright band sweeping left-to-right through the filled portion of a bar, clipped to it so
     * it never spills onto the dark unfilled track. Reads as energy flowing into the bar rather
     * than a block that merely fills up.
     */
    private static void drawShimmer(GuiGraphics graphics, int x, int y, int width, int height,
                                    float now, int opacity, boolean off) {
        if (off || width <= 1) {
            return;
        }
        int bandWidth = 10;
        int travel = width + bandWidth * 2;
        // Was 90px/s — fast enough that several simultaneous bars read as flicker rather than a
        // single light sweeping through. At this speed a full sweep takes several seconds, closer
        // to a slow glint than a strobe.
        float speedPxPerSecond = 28f;
        int pos = Math.floorMod((int) (now * speedPxPerSecond), travel) - bandWidth;
        for (int i = 0; i < bandWidth; i++) {
            int px = x + pos + i;
            if (px < x || px >= x + width) {
                continue;
            }
            float distFromCentre = Math.abs(i - bandWidth / 2f) / (bandWidth / 2f);
            int alpha = Math.round(opacity * (1f - distFromCentre) * 0.5f);
            if (alpha <= 0) {
                continue;
            }
            graphics.fill(px, y, px + 1, y + height, withAlpha(0xFFFFFF, alpha));
        }
    }

    /**
     * A slow, smooth glow around a bar that is nearly finished (progress is the ground truth, not
     * the smoothed display, so the glow's timing matches when the task actually completes).
     * ~0.8 Hz on purpose -- a breathing glow, not a blink, for the same reason the header's pulse
     * above stays slow.
     */
    private static void drawCompletionPulse(GuiGraphics graphics, int x, int y, int width, int height,
                                            float now, int opacity) {
        float pulse = 0.5f + 0.5f * (float) Math.sin(now * Math.PI * 1.6);
        int alpha = Math.round(opacity * 0.5f * pulse);
        if (alpha <= 0) {
            return;
        }
        int glow = withAlpha(0xFFFFFF, alpha);
        graphics.fill(x - 1, y - 1, x + width + 1, y, glow);
        graphics.fill(x - 1, y + height, x + width + 1, y + height + 1, glow);
        graphics.fill(x - 1, y - 1, x, y + height + 1, glow);
        graphics.fill(x + width, y - 1, x + width + 1, y + height + 1, glow);
    }

    /** Drops animation state for any concept that is no longer showing, so a task finishing and a
     *  later, unrelated one reusing the same concept key replays the entrance instead of silently
     *  inheriting old state, and so neither map grows for the life of the session. */
    private static void pruneAnimationState(List<Row> rows) {
        Set<String> present = new HashSet<>();
        for (Row row : rows) {
            present.add(row.concept);
        }
        DISPLAYED_PROGRESS.keySet().retainAll(present);
        FIRST_SEEN_NANOS.keySet().retainAll(present);
    }

    private static float smoothTowards(float current, float target, float dt, float rate) {
        if (dt <= 0f) {
            return current;
        }
        float t = 1f - (float) Math.exp(-rate * dt);
        return current + (target - current) * t;
    }

    private static float smoothstep01(float t) {
        float c = Mth.clamp(t, 0f, 1f);
        return c * c * (3f - 2f * c);
    }

    /** Seconds since the previous rendered frame, clamped so a stall cannot dump a whole
     *  animation into one frame. Mirrors DimensionImpactFX's own step() for the same reason. */
    private static float step() {
        long now = System.nanoTime();
        if (lastFrameNanos == 0L) {
            lastFrameNanos = now;
            return 0f;
        }
        float delta = (now - lastFrameNanos) / 1_000_000_000f;
        lastFrameNanos = now;
        return Math.min(delta, 0.1f);
    }

    private static float nowSeconds() {
        return (System.nanoTime() - START_NANOS) / 1_000_000_000f;
    }

    private static final int[] HUE_PALETTE = new int[256];
    static {
        for (int i = 0; i < HUE_PALETTE.length; i++) {
            int rgb = java.awt.Color.HSBtoRGB(i / (float) HUE_PALETTE.length, 1.0f, 1.0f);
            HUE_PALETTE[i] = 0xFF000000 | (rgb & 0x00FFFFFF);
        }
    }

    static int rainbowColor() {
        return rainbowColor((int) (System.currentTimeMillis() / 5) & 0xFFFF);
    }

    static int rainbowColor(int offset) {
        return HUE_PALETTE[offset & (HUE_PALETTE.length - 1)];
    }

    private static int priority(String concept) {
        if (concept.equals(Concepts.ADVERSITY)) return -1;
        if (concept.startsWith("Existence_")) return 0;
        if (concept.startsWith("Mutation_")) return 2;
        if (concept.contains("Regen") || concept.contains("IMMORTALITY") || concept.equals(Concepts.SELF_DAMAGE)) return 1;
        if (concept.startsWith("Env_")) return 10;
        if (concept.startsWith("Move_")) return 12;
        if (concept.startsWith("Mine_")) return 13;
        if (concept.startsWith("Fist_")) return 11;
        if (concept.startsWith("Combat_")) return 14;
        if (concept.startsWith("Percep_")) return 15;
        if (concept.contains("Debuff")) return 20;
        if (concept.startsWith("Contact_")) return 30;
        if (concept.startsWith("Proj_")) return 35;
        if (concept.startsWith("Offense_")) return 40;
        if (concept.startsWith("Drop_NPC_")) return 1000;
        return 50;
    }

    private static int withAlpha(int rgb, int alphaPercent) {
        int alpha = Math.max(0, Math.min(255, alphaPercent * 255 / 100));
        return (rgb & 0xFFFFFF) | (alpha << 24);
    }

    private record Row(String concept, float progress, boolean rainbow, int blocksDone, int blocksTotal,
                       String suffix, boolean split, int splitLeft, int splitRight, Integer overrideColor) {

        private static Row of(String concept, float progress) {
            return new Row(concept, progress, false, 0, 0, null, false, 0, 0, null);
        }

        private static Row of(String concept, float progress, boolean rainbow) {
            return new Row(concept, progress, rainbow, 0, 0, null, false, 0, 0, null);
        }

        /** What the counter measures: the breaking fist counts blocks, the punching fist kills. */
        String unit() {
            return concept != null && concept.startsWith("Combat_Fist") ? "kills" : "blocks";
        }
    }

    private static boolean hasFistRow() {
        return ClientAdaption.fistProgressTotal > 0 && ClientAdaption.fistTier() >= 0;
    }

    private static float fistProgress() {
        int total = ClientAdaption.fistProgressTotal;
        if (total <= 0) {
            return 0f;
        }
        return Math.min(1f, (float) ClientAdaption.fistProgressDone / total);
    }

    /**
     * The next stage's concept key, or null when this concept is the last stage.
     */
    private static String nextStageName(String concept) {
        for (int t = 0; t < ru.adaptionwheel.category.FistTiers.TIER_COUNT; t++) {
            if (concept.equals(ru.adaptionwheel.category.FistTiers.concept(t)) && t + 1 < ru.adaptionwheel.category.FistTiers.TIER_COUNT) {
                return ru.adaptionwheel.category.FistTiers.concept(t + 1);
            }
            if (concept.equals(ru.adaptionwheel.category.CombatFistTiers.concept(t)) && t + 1 < ru.adaptionwheel.category.CombatFistTiers.TIER_COUNT) {
                return ru.adaptionwheel.category.CombatFistTiers.concept(t + 1);
            }
        }
        return null;
    }

    /**
     * Builds a material-tier row (the breaking fist or the punching fist): works out whether this
     * level is a handover boundary, and always carries the tier's own colour as
     * {@link Row#overrideColor} so the bar, name and background read in that material's colour
     * all the time -- not only in the rare frame where the handover split is drawn, which used to
     * be the only place a tier's actual colour ever appeared on screen.
     *
     * <p>{@code tierCount} is taken explicitly from the caller rather than hardcoded, because this
     * is shared between two independent tier ladders (the breaking fist and the punching fist):
     * hardcoding one's {@code TIER_COUNT} here previously made the punching fist's own boundary
     * check silently use the wrong ladder's length.
     */
    private static Row buildFistTierRow(String concept, float progress, int done, int total, String suffix,
                                        int level, int tier, int tierCount,
                                        java.util.function.IntFunction<String> conceptFn,
                                        java.util.function.IntFunction<Integer> colourFn) {
        int[] split = handedOverColours(level, tier, tierCount, conceptFn, colourFn);
        boolean hasSplit = split != null;
        return new Row(concept, progress, false, done, total, suffix,
                hasSplit, hasSplit ? split[0] : 0, hasSplit ? split[1] : 0, colourFn.apply(tier));
    }

    /**
     * The colours of a material handover, or {@code null} when this level is not one.
     *
     * <p>Returns {@code null} rather than a sentinel pair of zeroes: a tier's real colour could
     * itself be black, which made "no split" and "split into black" indistinguishable before.
     *
     * @return {left, right} colours, or {@code null} when no split applies
     */
    private static int[] handedOverColours(int level, int tier, int tierCount,
                                           java.util.function.IntFunction<String> concept,
                                           java.util.function.IntFunction<Integer> colour) {
        boolean previousMaxed = tier > 0
                && ClientAdaption.level(concept.apply(tier - 1)) >= PlayerAdaption.MAX_LEVEL;
        boolean nextUntrained = tier + 1 < tierCount && ClientAdaption.level(concept.apply(tier + 1)) == 0;
        if (!ru.adaptionwheel.category.FistTiers.showsHandover(level, previousMaxed, nextUntrained)) {
            return null;
        }
        boolean arriving = level == 1 && previousMaxed;
        return new int[] {colour.apply(arriving ? tier - 1 : tier), colour.apply(arriving ? tier : tier + 1)};
    }

    private static float combatFistProgress() {
        int total = ClientAdaption.combatFistProgressTotal;
        if (total <= 0) {
            return 0f;
        }
        return Math.min(1f, (float) ClientAdaption.combatFistProgressDone / total);
    }
}