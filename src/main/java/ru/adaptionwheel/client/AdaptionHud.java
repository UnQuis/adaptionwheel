package ru.adaptionwheel.client;

import net.minecraft.client.Minecraft;
import ru.adaptionwheel.data.PlayerAdaption;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.category.Concepts;
import ru.adaptionwheel.config.AdaptionConfig;
import ru.adaptionwheel.data.AdaptionTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@EventBusSubscriber(modid = AdaptionWheel.MODID, value = net.neoforged.api.distmarker.Dist.CLIENT)
public class AdaptionHud {

    public static final Identifier WHEEL_TEXTURE =
            Identifier.fromNamespaceAndPath(AdaptionWheel.MODID, "textures/entity/wheel.png");

    private static final int HEADER_COLOR = 0xFFD700;
    private static final int ROW_WIDTH = 252;
    private static final int ROW_HEIGHT = 36;
    private static final int BAR_WIDTH = 240;
    private static final int BAR_HEIGHT = 8;
    private static final int SPACING = 50;

    @SubscribeEvent
    public static void render(RenderGuiEvent.Post event) {
        if (!AdaptionConfig.HUD_ENABLED.get() || !ClientAdaption.wearingWheel) {
            return;
        }
        if (ClientAdaption.TASKS.isEmpty() && !ClientAdaption.adversityActive
                && ClientAdaption.EXISTENCE_PROGRESS.isEmpty() && !hasFistRow()) {
            return;
        }
        GuiGraphicsExtractor graphics = event.getGuiGraphics();
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int opacity = AdaptionConfig.HUD_OPACITY.get();
        float scale = (float) (double) AdaptionConfig.HUD_SCALE.get();

        graphics.pose().pushMatrix();
        graphics.pose().translate(
                25f * scale + AdaptionConfig.HUD_OFFSET_X.get(),
                graphics.guiHeight() * 0.35f + AdaptionConfig.HUD_OFFSET_Y.get());
        graphics.pose().scale(scale, scale);

        String header = ">>> ADAPTED  " + ClientAdaption.adaptedCount;
        graphics.text(font, Component.literal(header), 0, 0,
                withAlpha(HEADER_COLOR, Math.min(100, opacity * 150 / 100)), true);
        graphics.text(font, Component.literal("ADAPTATION_ANALYSIS"), 0, font.lineHeight,
                withAlpha(HEADER_COLOR, Math.min(100, opacity * 150 / 100)), true);

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

            String suffix = luck > 1 ? " - LUCK x" + luck : null;
            rows.add(new Row(ru.adaptionwheel.category.FistTiers.concept(tier),
                    fistProgress(), false, ClientAdaption.fistProgressDone,
                    ClientAdaption.fistProgressTotal, suffix,
                    handedOverColour(ClientAdaption.level(
                            ru.adaptionwheel.category.FistTiers.concept(tier)), tier,
                            ru.adaptionwheel.category.FistTiers::concept,
                            ru.adaptionwheel.category.FistTiers::color)));
        }

        {
            // The row appears only once the punch is trainable -- the first bare-handed hostile
            // kill -- and then stays for good. The gate is combatFistTier(), derived from the synced
            // LEVELS map, not from combatFistProgressTier: that arrives on CombatFistProgressPayload
            // and is cleared on logout, so gating on it would hide the row again on every relog
            // after the player had already unlocked it.
            int trained = ClientAdaption.combatFistTier();
            if (trained >= 0) {
                int tier = Math.min(trained, ru.adaptionwheel.category.CombatFistTiers.TIER_COUNT - 1);
                boolean maxed = ClientAdaption.combatFistProgressTotal <= 0;
                rows.add(new Row(ru.adaptionwheel.category.CombatFistTiers.concept(tier),
                        maxed ? 1f : combatFistProgress(), false,
                        ClientAdaption.combatFistProgressDone,
                        ClientAdaption.combatFistProgressTotal, maxed ? "MAX" : tierKillsSuffix(tier),
                        handedOverColour(ClientAdaption.level(
                                ru.adaptionwheel.category.CombatFistTiers.concept(tier)), tier,
                                ru.adaptionwheel.category.CombatFistTiers::concept,
                                ru.adaptionwheel.category.CombatFistTiers::color)));
            }
        }
        rows.sort(Comparator.comparingInt(r -> priority(r.concept)));

        int maxDisplay = Math.max(5, (int) (graphics.guiHeight() * 0.6f / (SPACING * scale)));
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            int y = (i + 1) * SPACING;
            if (i >= maxDisplay || y + ROW_HEIGHT > graphics.guiHeight() / scale) {
                graphics.text(font, Component.literal("...and " + (rows.size() - i) + " more"),
                        15, y, 0xFFAAAAAA, true);
                break;
            }
            drawRow(graphics, font, row, y, opacity);
        }

        graphics.pose().popMatrix();
    }

    private static void drawRow(GuiGraphicsExtractor graphics, Font font, Row row, int y, int opacity) {
        boolean off = !row.rainbow && !ClientAdaption.isEnabled(row.concept);
        int color = row.rainbow ? rainbowColor()
                : (off ? 0xFF9A9A9A : Concepts.color(row.concept));
        int barW = Math.min(BAR_WIDTH, Math.max(0, (int) (BAR_WIDTH * row.progress)));

        graphics.fill(9, y - 2, 9 + ROW_WIDTH, y - 2 + ROW_HEIGHT, withAlpha(color, opacity * 15 / 100));

        String name = Concepts.chatName(row.concept).getString();
        String tag;
        if (row.rainbow) {
            tag = " [EXISTENCE]";
        } else if (Concepts.isOneTime(row.concept)) {
            tag = " [NEW]";
        } else {
            int level = ClientAdaption.LEVELS.getOrDefault(row.concept, 0);
            // A stage tops out at 8 and the next starts at 1, so "Lv.8 > 9" was never a real state:
            // maxing a stage hands over to the next material at level 1. So at the cap the tag names
            // that next stage instead of inventing a ninth level.
            if (level >= PlayerAdaption.MAX_LEVEL) {
                String next = nextStageName(row.concept);
                // Resolved, not printed: the key used to reach the HUD verbatim as Combat_FistStone.
                tag = next == null ? " [Lv.MAX]"
                        : " [Lv.MAX > " + Concepts.chatName(next).getString() + " Lv.1]";
            } else {
                tag = " [Lv." + level + " > " + (level + 1) + "]";
            }
        }
        String meter = row.blocksTotal > 0
                ? row.blocksDone + "/" + row.blocksTotal + " " + row.unit()
                : String.format("%.1f%%", row.progress * 100f);
        String suffix = row.suffix == null ? "" : " " + row.suffix;
        if (off) {
            tag = " [OFF]";
        }
        String text = name + tag + " : " + meter + suffix;
        graphics.text(font, text, 15, y, withAlpha(color, opacity), true);

        graphics.fill(15, y + 24, 15 + BAR_WIDTH, y + 24 + BAR_HEIGHT, withAlpha(0x000000, opacity * 60 / 100));

        if (row.rainbow) {

            int segCount = Math.max(1, barW);
            for (int x = 0; x < segCount; x++) {
                int segColor = rainbowColor((int) (x + System.currentTimeMillis() / 10));
                graphics.fill(15 + x, y + 24, 15 + x + 1, y + 24 + BAR_HEIGHT,
                        withAlpha(segColor, opacity * 90 / 100));
            }
        } else if (row.splitColour != 0) {
            // Two-tone, and only here: the player has just crossed a material boundary, so half the
            // bar is the stage they finished and half is the one they are training.
            int half = BAR_WIDTH / 2;
            graphics.fill(15, y + 24, 15 + half, y + 24 + BAR_HEIGHT,
                    withAlpha(row.splitColour, opacity * 55 / 100));
            graphics.fill(15 + half, y + 24, 15 + BAR_WIDTH, y + 24 + BAR_HEIGHT,
                    withAlpha(color, opacity * 90 / 100));
        } else {
            graphics.fill(15, y + 24, 15 + barW, y + 24 + BAR_HEIGHT, withAlpha(color, opacity * 90 / 100));
        }
    }

    private static final int[] HUE_PALETTE = new int[256];
    static {
        for (int i = 0; i < HUE_PALETTE.length; i++) {
            int rgb = java.awt.Color.HSBtoRGB(i / (float) HUE_PALETTE.length, 1.0f, 1.0f);
            HUE_PALETTE[i] = 0xFF000000 | (rgb & 0x00FFFFFF);
        }
    }

    private static int rainbowColor() {
        return rainbowColor((int) (System.currentTimeMillis() / 5) & 0xFFFF);
    }

    private static int rainbowColor(int offset) {
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
                       String suffix, int splitColour) {
        private static Row of(String concept, float progress) {
            return new Row(concept, progress, false, 0, 0, null, 0);
        }

        private static Row of(String concept, float progress, boolean rainbow) {
            return new Row(concept, progress, rainbow, 0, 0, null, 0);
        }

        /** What the counter measures: the breaking fist counts blocks, the punching fist kills. */
        String unit() {
            return concept != null && concept.startsWith("Combat_Fist") ? "kills" : "blocks";
        }
    }

    private static boolean hasFistRow() {
        return ClientAdaption.fistProgressTotal > 0 && ClientAdaption.fistTier() >= 0;
    }

    /** "3/7" for the current stage, so the row says what is left rather than only a bar. */
    /** "3/7 kills", so the row says what is left rather than showing only a bar. */
    /**
     * The next stage's concept key, or null when this concept is the last stage.
     *
     * <p>Reached at the cap the tag has to name somewhere to go: a stage tops out at 8, so
     * "Lv.8 &gt; 9" was a level the game can never reach. Naming the stage that takes over at
     * level 1 is what makes the ladder read as a ladder.
     */
    private static String nextStageName(String concept) {
        // Returns the concept key, NOT a name. The tag resolves it through the same display-name
        // path as the row itself; printing the key put a literal "Combat_FistStone" on the HUD.
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
     * The colour of the other half of the bar, but only where a stage actually hands over.
     *
     * <p>A stage tops out at 8 and the next begins at 1, so both ends of that boundary are the same
     * event seen from opposite sides: level 1 of a stage whose predecessor is maxed (you have just
     * arrived) and level 8 of a stage whose successor is untouched (you are about to leave). Gating
     * on the arrival side alone left the row reading "Lv.MAX &gt; Stone Lv.1" in one flat colour on
     * the very tick it announces a new material, which is what a player actually sees.
     *
     * <p>Which half is the "other" one flips with the side: on arrival the row is the new stage and
     * the other half is what was just finished; on departure the row is the finished stage and the
     * other half is what comes next. The bar always paints the row's own colour on the right, so only
     * the left half has to be worked out.
     *
     * <p>Levels are read raw, not through {@code levelOrZero}: switching a stage off must not make it
     * look like a handover happened.
     *
     * @return the colour of the other half, or 0 when no split applies
     */
    private static int handedOverColour(int level, int tier,
                                        java.util.function.IntFunction<String> concept,
                                        java.util.function.IntFunction<Integer> colour) {
        int count = ru.adaptionwheel.category.FistTiers.TIER_COUNT;
        boolean previousMaxed = tier > 0
                && ClientAdaption.level(concept.apply(tier - 1)) >= PlayerAdaption.MAX_LEVEL;
        boolean nextUntrained = tier + 1 < count && ClientAdaption.level(concept.apply(tier + 1)) == 0;
        // The rule is in FistTiers, not here, because a gametest cannot call a method that needs a
        // client Font -- and the "only at the handover" half of it is the whole requirement.
        if (!ru.adaptionwheel.category.FistTiers.showsHandover(level, previousMaxed, nextUntrained)) {
            return 0;
        }
        return colour.apply(previousMaxed && level == 1 ? tier - 1 : tier + 1);
    }

    private static String tierKillsSuffix(int tier) {
        return ClientAdaption.combatFistProgressDone + "/" + ClientAdaption.combatFistProgressTotal;
    }

    private static float combatFistProgress() {
        int total = ClientAdaption.combatFistProgressTotal;
        if (total <= 0) {
            return 0f;
        }
        return Math.min(1f, (float) ClientAdaption.combatFistProgressDone / total);
    }

    private static float fistProgress() {
        int total = ClientAdaption.fistProgressTotal;
        return total <= 0 ? 0f : Math.min(1f, (float) ClientAdaption.fistProgressDone / total);
    }
}
