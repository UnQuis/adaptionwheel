package ru.adaptionwheel.client;

import net.minecraft.client.Minecraft;
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

/**
 * Analysis HUD: anchored at the LEFT side of the screen, gold header,
 * per-concept colored task rows with tags, percentage text and progress bars.
 * Existence adaptation rows have a rainbow-shifting progress bar.
 */
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

        // Header
        // Header. Carries the wheel tier, because the tier is the spine of the whole progression
        // and there is no other place it appears outside the 3D model.
        int wheelTier = ru.adaptionwheel.category.WheelTier.forCount(ClientAdaption.adaptedCount);
        int nextTier = ru.adaptionwheel.category.WheelTier.nextThreshold(wheelTier);
        String header = ">>> " + Component.translatable(
                ru.adaptionwheel.category.WheelTier.nameKey(wheelTier)).getString()
                .toUpperCase(java.util.Locale.ROOT) + "  " + wheelTier + "/"
                + ru.adaptionwheel.category.WheelTier.maxTier();
        if (nextTier > 0) {
            header += "  (" + ClientAdaption.adaptedCount + "/" + nextTier + ")";
        }
        graphics.text(font, Component.literal(header), 0, 0,
                withAlpha(ru.adaptionwheel.category.WheelTier.color(wheelTier),
                        Math.min(100, opacity * 150 / 100)), true);
        graphics.text(font, Component.literal("ADAPTATION_ANALYSIS"), 0, font.lineHeight,
                withAlpha(HEADER_COLOR, Math.min(100, opacity * 150 / 100)), true);

        // Build row list: tasks + existence progress + adversity
        List<Row> rows = new ArrayList<>();
        if (ClientAdaption.adversityActive) {
            rows.add(Row.of(Concepts.ADVERSITY, ClientAdaption.adversityProgress()));
        }
        for (AdaptionTask task : ClientAdaption.TASKS) {
            rows.add(Row.of(task.concept, ClientAdaption.taskProgress(task)));
        }
        // Existence progress bars (separate from tasks)
        for (Map.Entry<String, Integer> entry : ClientAdaption.EXISTENCE_PROGRESS.entrySet()) {
            String bossPath = entry.getKey();
            float progress = ClientAdaption.existenceProgress(bossPath);
            if (progress < 1f) {
                rows.add(Row.of(Concepts.existence(bossPath), progress, true));
            }
        }
        // Fist Mastery: counted in blocks rather than seconds, so it gets its own row shape.
        if (hasFistRow()) {
            int tier = ClientAdaption.fistTier();
            int luck = ru.adaptionwheel.category.FistTiers.luckMultiplier(tier);
            // Stated on the row because an invisible multiplier is indistinguishable from a bug.
            String suffix = luck > 1 ? " - LUCK x" + luck : null;
            rows.add(new Row(ru.adaptionwheel.category.FistTiers.concept(tier),
                    fistProgress(), false, ClientAdaption.fistProgressDone,
                    ClientAdaption.fistProgressTotal, suffix));
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
        int color = row.rainbow ? rainbowColor() : Concepts.color(row.concept);
        int barW = Math.min(BAR_WIDTH, Math.max(0, (int) (BAR_WIDTH * row.progress)));

        // Tinted row background
        graphics.fill(9, y - 2, 9 + ROW_WIDTH, y - 2 + ROW_HEIGHT, withAlpha(color, opacity * 15 / 100));

        // "NAME [tag] : NN.N%"
        String name = Concepts.chatName(row.concept).getString();
        String tag;
        if (row.rainbow) {
            tag = " [EXISTENCE]";
        } else if (Concepts.isOneTime(row.concept)) {
            tag = " [NEW]";
        } else {
            int level = ClientAdaption.LEVELS.getOrDefault(row.concept, 0);
            tag = " [Lv." + level + " > " + (level + 1) + "]";
        }
        String meter = row.blocksTotal > 0
                ? row.blocksDone + "/" + row.blocksTotal + " blocks"
                : String.format("%.1f%%", row.progress * 100f);
        String suffix = row.suffix == null ? "" : " " + row.suffix;
        String text = name + tag + " : " + meter + suffix;
        graphics.text(font, text, 15, y, withAlpha(color, opacity), true);

        // Progress bar: black background + colored fill
        graphics.fill(15, y + 24, 15 + BAR_WIDTH, y + 24 + BAR_HEIGHT, withAlpha(0x000000, opacity * 60 / 100));

        if (row.rainbow) {
            // Rainbow bar: draw segment by segment with shifting hue
            int segCount = Math.max(1, barW);
            for (int x = 0; x < segCount; x++) {
                int segColor = rainbowColor((int) (x + System.currentTimeMillis() / 10));
                graphics.fill(15 + x, y + 24, 15 + x + 1, y + 24 + BAR_HEIGHT,
                        withAlpha(segColor, opacity * 90 / 100));
            }
        } else {
            graphics.fill(15, y + 24, 15 + barW, y + 24 + BAR_HEIGHT, withAlpha(color, opacity * 90 / 100));
        }
    }

    /** Precomputed hue palette: avoids per-pixel HSB conversions every frame. */
    private static final int[] HUE_PALETTE = new int[256];
    static {
        for (int i = 0; i < HUE_PALETTE.length; i++) {
            int rgb = java.awt.Color.HSBtoRGB(i / (float) HUE_PALETTE.length, 1.0f, 1.0f);
            HUE_PALETTE[i] = 0xFF000000 | (rgb & 0x00FFFFFF);
        }
    }

    /** Rainbow hue cycling based on a time/position offset. Returns ARGB. */
    private static int rainbowColor() {
        return rainbowColor((int) (System.currentTimeMillis() / 5) & 0xFFFF);
    }

    private static int rainbowColor(int offset) {
        return HUE_PALETTE[offset & (HUE_PALETTE.length - 1)];
    }

    private static int priority(String concept) {
        if (concept.equals(Concepts.ADVERSITY)) return -1;
        if (concept.startsWith("Existence_")) return 0; // Existence at the top
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

    /**
     * A HUD line. {@code blocksTotal} > 0 marks a row counted in blocks rather than seconds,
     * which changes the meter text and needs the exact done/total the fist pushes.
     */
    private record Row(String concept, float progress, boolean rainbow, int blocksDone, int blocksTotal,
                       String suffix) {
        private static Row of(String concept, float progress) {
            return new Row(concept, progress, false, 0, 0, null);
        }

        private static Row of(String concept, float progress, boolean rainbow) {
            return new Row(concept, progress, rainbow, 0, 0, null);
        }
    }

    /** True while the wearer's fist is working toward its next level. */
    private static boolean hasFistRow() {
        return ClientAdaption.fistProgressTotal > 0 && ClientAdaption.fistTier() >= 0;
    }

    private static float fistProgress() {
        int total = ClientAdaption.fistProgressTotal;
        return total <= 0 ? 0f : Math.min(1f, (float) ClientAdaption.fistProgressDone / total);
    }
}
