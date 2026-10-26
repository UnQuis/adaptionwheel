package ru.adaptionwheel.client;

import net.minecraft.client.Minecraft;
import ru.adaptionwheel.data.PlayerAdaption;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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

    private static final int RED = 0xFFE5484D;
    private static final int RED_LIGHT = 0xFFFF7B7B;
    private static final int CARD_RADIUS = 7;
    private static final int ROW_X = 9;
    private static final int ROW_WIDTH = 252;
    private static final int ROW_HEIGHT = 36;
    /** Row pitch: the card plus the gap that separates two of them. */
    private static final int SPACING = 46;
    /** First row is placed one small gap below the header rule, not one full pitch. */
    private static final int FIRST_ROW_Y = 36;

    /**
     * How opaque a row's plate is: 20%, i.e. 80% transparency.
     *
     * <p>A plate that is mostly transparent is the point, not an oversight — it floats over the
     * world and the world stays readable through it, while the concept-tinted border and the accent
     * stripe are what actually delimit the card. The row's text and bar are drawn at full strength
     * on top, so legibility does not depend on the plate's fill.
     */
    private static final float CARD_BODY_ALPHA = 0.20f;

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
        if (!ClientAdaption.wearingWheel) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        Minecraft mc = Minecraft.getInstance();

        // The Instabreak switch is a state readout, not an analysis row, so it is drawn directly
        // in screen space near the hotbar and survives the "nothing is being analysed" early
        // return below. It is deliberately independent of showAnalysisHud: a player who hides the
        // analysis list still needs to know whether their fist is about to break the block in front
        // of them.
        if (ClientAdaption.instabreakUnlocked && mc.screen == null) {
            drawInstabreakPanel(graphics, mc);
        }

        if (!AdaptionConfig.HUD_ENABLED.get()) {
            return;
        }
        if (ClientAdaption.TASKS.isEmpty() && !ClientAdaption.adversityActive
                && ClientAdaption.EXISTENCE_PROGRESS.isEmpty() && !hasFistRow()) {
            return;
        }
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
        int headerAlpha = Math.round(opacity * headerPulse);
        // The header is drawn in the menu's voice -- JetBrains Mono caps, a solid accent block,
        // a hairline rule -- instead of vanilla bold text in gold, which sat on grass as an
        // unreadable olive smear.
        graphics.fill(0, 0, 3, 19, withAlpha(RED, headerAlpha));
        WheelUi.text(graphics, font, WheelUi.MONO_8,
                Component.translatable("adaptionwheel.hud.analysis").getString(),
                8, 0, withAlpha(WheelUi.INK, headerAlpha), false);
        WheelUi.text(graphics, font, WheelUi.MONO_8,
                Component.translatable("adaptionwheel.hud.adapted", ClientAdaption.adaptedCount).getString(),
                8, 10, withAlpha(RED_LIGHT, headerAlpha), false);
        graphics.fill(0, 22, ROW_X + ROW_WIDTH, 23, withAlpha(RED_LIGHT, Math.round(headerAlpha * 0.18f)));

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

        // Only show a row while there is a next level to earn. Once the last material is maxed the
        // server reports total 0; the old code still added a row, which drew a full bar under
        // "100.0% MAX" and was the only thing left on an otherwise finished HUD. The permanent
        // Instabreak chip near the hotbar now carries the finished-ladder state.
        {
            int trained = ClientAdaption.combatFistTier();
            if (trained >= 0 && ClientAdaption.combatFistProgressTotal > 0) {
                int tierCount = ru.adaptionwheel.category.CombatFistTiers.TIER_COUNT;
                int tier = Math.min(trained, tierCount - 1);
                String concept = ru.adaptionwheel.category.CombatFistTiers.concept(tier);
                rows.add(buildFistTierRow(concept, combatFistProgress(),
                        ClientAdaption.combatFistProgressDone, ClientAdaption.combatFistProgressTotal,
                        null, ClientAdaption.level(concept), tier, tierCount,
                        ru.adaptionwheel.category.CombatFistTiers::concept,
                        ru.adaptionwheel.category.CombatFistTiers::color));
            }
        }
        rows.sort(Comparator.comparingInt(r -> priority(r.concept)));

        pruneAnimationState(rows);

        int maxDisplay = Math.max(5, (int) (mc.getWindow().getGuiScaledHeight() * 0.6f / (SPACING * scale)));
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            int y = FIRST_ROW_Y + i * SPACING;
            if (i >= maxDisplay || y + ROW_HEIGHT > mc.getWindow().getGuiScaledHeight() / scale) {
                WheelUi.text(graphics, font, WheelUi.MONO_8,
                        Component.translatable("adaptionwheel.hud.more", rows.size() - i).getString(),
                        15, y, withAlpha(WheelUi.MUTED, opacity), false);
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
        int accent = off ? WheelUi.MUTED : WheelUi.lighten(color, 0.15f);
        int cardTop = y - 2;

        // Each plate wears its own concept's colour, not the mod's red: with five rows on screen the
        // colour is the only thing that says which bar belongs to which adaptation at a glance, and
        // a single red for all of them threw that away. The tint is the concept colour driven most
        // of the way to black, so it stays a plate the white text can sit on rather than a colour
        // swatch; the border keeps the full hue, which is what makes the card read as a colour
        // without needing a solid fill.
        int plate = off ? 0xFF1E1E22 : WheelUi.darken(color, 0.62f);
        int borderColor = off ? 0xFF6D6961 : WheelUi.lighten(color, 0.22f);
        int border = withAlpha(borderColor, Math.round(rowOpacity * 0.55f));
        int body = withAlpha(plate, Math.round(rowOpacity * CARD_BODY_ALPHA));
        // A 1px dark foot under the plate: at 80% transparency the fill alone cannot separate the
        // card from a bright background, and the world otherwise bleeds through the bottom edge.
        WheelUi.outlineRounded(graphics, ROW_X, cardTop, ROW_WIDTH, ROW_HEIGHT, CARD_RADIUS,
                border, body);
        graphics.fill(ROW_X + CARD_RADIUS, cardTop + ROW_HEIGHT - 1,
                ROW_X + ROW_WIDTH - CARD_RADIUS, cardTop + ROW_HEIGHT,
                withAlpha(0x000000, Math.round(rowOpacity * 0.18f)));
        graphics.fill(ROW_X + 2, cardTop + 7, ROW_X + 4, cardTop + ROW_HEIGHT - 7,
                withAlpha(accent, Math.round(rowOpacity * 0.95f)));

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
        String valueText = meter + suffix;

        int valueWidth = font.width(WheelUi.mono(WheelUi.MONO_8, valueText));
        int nameMax = ROW_WIDTH - 18 - valueWidth - 8;
        // Shadowed, because the plate is 80% transparent: over grass or a bright sky, unshadowed
        // white text on a 20% tint is legible in a cave and disappears in daylight.
        WheelUi.text(graphics, font, WheelUi.MONO_8,
                WheelUi.fit(font, WheelUi.MONO_8, WheelUi.upper(name + tag), nameMax),
                ROW_X + 8, cardTop + 5, withAlpha(off ? WheelUi.MUTED : WheelUi.INK, rowOpacity), true);
        WheelUi.text(graphics, font, WheelUi.MONO_8, valueText,
                ROW_X + ROW_WIDTH - 8 - valueWidth, cardTop + 5,
                withAlpha(accent, rowOpacity), true);

        int barX = ROW_X + 8;
        int barW = ROW_WIDTH - 16;
        int barY = cardTop + 21;
        int barH = 6;
        int barW2 = Math.max(0, Math.min(barW, Math.round(barW * displayed)));
        WheelUi.fillRounded(graphics, barX, barY, barW, barH, barH / 2,
                withAlpha(0x000000, Math.round(rowOpacity * 0.45f)));

        if (row.rainbow) {
            for (int x = 0; x < barW2; x++) {
                int segColor = rainbowColor((int) (x + System.currentTimeMillis() / 10));
                graphics.fill(barX + x, barY + 1, barX + x + 1, barY + barH - 1,
                        withAlpha(segColor, rowOpacity * 90 / 100));
            }
        } else if (row.split) {
            // Two-tone, and only at a material handover: left is the stage being finished, right
            // is the one being moved into, both ways round -- see handedOverColours for why.
            int half = barW / 2;
            WheelUi.fillRounded(graphics, barX, barY, half + barH, barH, barH / 2,
                    withAlpha(row.splitLeft, rowOpacity * 90 / 100));
            WheelUi.fillRounded(graphics, barX + half - barH, barY, barW - half + barH, barH,
                    barH / 2, withAlpha(row.splitRight, rowOpacity * 90 / 100));
        } else if (barW2 > 0) {
            WheelUi.fillRounded(graphics, barX, barY, barW2, barH, barH / 2,
                    withAlpha(accent, rowOpacity * 90 / 100));
            drawBarLight(graphics, barX, barY, barW2, barH, accent, rowOpacity, now);
        }

        if (!row.rainbow) {
            // The knob is the one thing this HUD borrows outright from the panel's switches.
            // It marks the leading edge of what has been earned, which a flat bar cannot show --
            // and it is left in place at 100%, where a bar simply stops changing.
            int knobRadius = 3;
            int knobX = Math.max(barX + knobRadius,
                    Math.min(barX + barW - knobRadius, barX + barW2));
            if (row.split) {
                knobX = barX + barW - knobRadius;
            }
            int knobColor = off ? 0xFFB9B4AA : 0xFFF6F2E9;
            WheelUi.fillCircle(graphics, knobX, barY + barH / 2 + 1, knobRadius + 1,
                    withAlpha(0x000000, Math.round(rowOpacity * 0.45f)));
            WheelUi.fillCircle(graphics, knobX, barY + barH / 2, knobRadius,
                    withAlpha(knobColor, rowOpacity));
        }

        if (!row.rainbow && !off && row.progress >= 0.9f) {
            drawCompletionPulse(graphics, ROW_X, cardTop, ROW_WIDTH, ROW_HEIGHT, now, rowOpacity);
        }

        graphics.pose().popPose();
    }

    /**
     * The bar's own light: a lit top edge, a shaded bottom edge, and a slow breathing glow at the
     * leading edge.
     *
     * <p>This replaces a bright band that swept along the filled portion. That was meant to read as
     * energy but at HUD size it read as a scrolling marquee: a 10px white band moving at 28 px/s
     * across a bar that is at most 236 px long, clipped to the fill, is a streak of paint rather
     * than light, and several rows at once made it worse. A bar reads as a lit object from its
     * edges, so the light now sits where a real rounded bar catches it — one row on top, one row of
     * shade underneath — and the only thing that moves is the glow at the leading edge, which is
     * where the eye already is because that is where the progress stopped.
     */
    private static void drawBarLight(GuiGraphics graphics, int x, int y, int width, int height,
                                     int tint, int opacity, float now) {
        if (width < 4) {
            return;
        }
        int innerLeft = x + 2;
        int innerRight = x + width - 2;
        if (innerRight > innerLeft) {
            graphics.fill(innerLeft, y, innerRight, y + 1,
                    withAlpha(WheelUi.lighten(tint, 0.55f), Math.round(opacity * 0.30f)));
            graphics.fill(innerLeft, y + height - 1, innerRight, y + height,
                    withAlpha(0x000000, Math.round(opacity * 0.20f)));
        }
        // ~0.45 Hz: slower than anything that could read as a blink, see the flash-rate reasoning
        // in DimensionImpactFX for why that matters.
        float breathe = 0.5f + 0.5f * (float) Math.sin(now * Math.PI * 0.9);
        int glowAlpha = Math.round(opacity * (0.08f + 0.12f * breathe));
        if (glowAlpha > 0) {
            // Wider than the knob it sits behind, or the glow is entirely hidden by it.
            WheelUi.fillCircle(graphics, x + width, y + height / 2, 5,
                    withAlpha(WheelUi.lighten(tint, 0.35f), glowAlpha));
        }
    }

    /**
     * A soft, rounded breathing outline around a row that is nearly finished (progress is the
     * ground truth, not the smoothed display, so the glow's timing matches when the task actually
     * completes). ~0.8 Hz on purpose -- a breathing glow, not a blink, for the same reason the
     * header's pulse stays slow.
     */
    private static void drawCompletionPulse(GuiGraphics graphics, int x, int y, int width, int height,
                                            float now, int opacity) {
        float pulse = 0.5f + 0.5f * (float) Math.sin(now * Math.PI * 1.6);
        int alpha = Math.round(opacity * 0.5f * pulse);
        if (alpha <= 0) {
            return;
        }
        WheelUi.outlineRounded(graphics, x - 1, y - 1, width + 2, height + 2, CARD_RADIUS + 1,
                withAlpha(0xFFFFFF, alpha), 0x00000000);
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
        if (concept.startsWith("Offense_")) return 40;
        if (concept.startsWith("Drop_NPC_")) return 1000;
        return 50;
    }

    /**
     * The permanent Instabreak card beside the hotbar.
     *
     * <p>It used to be a flat coloured strip with the words "INSTABREAK OFF" in it, drawn at the
     * same height as the ability card and on top of it. It is now the same card as the ability
     * card, stacked one slot higher, so the two read as one column of status readouts instead of
     * two unrelated overlays fighting for the same corner.
     *
     * <p>It survives the "nothing is being analysed" early return and ignores the analysis HUD
     * toggle on purpose: a player who hid the analysis list still needs to know whether their fist
     * is about to break the block in front of them.
     */
    private static void drawInstabreakPanel(GuiGraphics graphics, Minecraft mc) {
        boolean active = ClientAdaption.instabreakActive;
        int accent = active ? 0xFF57D68D : 0xFF9A9A9A;
        int index = AbilityHud.showsCard() ? 1 : 0;
        int x = HudCards.x(mc.getWindow().getGuiScaledWidth(), HudCards.WIDTH);
        int y = HudCards.y(mc.getWindow().getGuiScaledHeight(), index, HudCards.HEIGHT);

        HudCards.frame(graphics, x, y, HudCards.WIDTH, HudCards.HEIGHT, accent,
                WheelUi.CARD, WheelUi.BORDER);
        HudCards.icon(graphics, x, y, new ItemStack(Items.NETHERITE_PICKAXE), accent);
        HudCards.title(graphics, mc.font, x, y,
                Component.translatable("adaptionwheel.instabreak").getString(),
                WheelUi.INK, HudCards.WIDTH - 38 - 20);
        HudCards.switchRow(graphics, mc.font, x, y, HudCards.WIDTH, active,
                Component.translatable("adaptionwheel.instabreak.state.on").getString(),
                Component.translatable("adaptionwheel.instabreak.state.off").getString(), accent);
        HudCards.keyChip(graphics, mc.font, x, y, HudCards.WIDTH,
                AdaptionKeybinds.TOGGLE_INSTABREAK_KEY.getTranslatedKeyMessage().getString(), accent);
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