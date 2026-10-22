package ru.adaptionwheel.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/**
 * The one card shape used by everything that rides beside the hotbar.
 *
 * <p>There used to be two: the ability card was a rounded dark card, and the Instabreak readout was
 * a flat coloured strip drawn in its own little box. They sat on top of each other, in two
 * different idioms, and the strip overlapped the card. Every screen-space readout now comes from
 * here, so "beside the hotbar" is one design instead of one design and one exception.
 *
 * <p>The cards stack upwards from the hotbar; {@link #place} answers where the n-th one goes, so a
 * second card does not have to know what the first one is.
 */
final class HudCards {

    static final int WIDTH = 104;
    static final int HEIGHT = 34;
    static final int GAP = 4;

    private HudCards() {
    }

    /** Bottom-left corner of the n-th card counted upwards from the hotbar (0 = closest). */
    static int x(int screenWidth, int cardWidth) {
        int hotbarRight = screenWidth / 2 + 91;
        int x = Math.min(screenWidth - cardWidth - 4, hotbarRight + 8);
        if (x < hotbarRight + 4 && x + cardWidth > screenWidth / 2 - 91) {
            // A narrow GUI cannot fit a card beside the hotbar. Tuck it against the screen edge
            // instead of letting it cover the item the player is holding.
            x = Math.max(4, screenWidth - cardWidth - 4);
        }
        return x;
    }

    static int y(int screenHeight, int index, int cardHeight) {
        int bottom = screenHeight - 4;
        int y = bottom - cardHeight - index * (cardHeight + GAP);
        if (y < 0) {
            y = 0;
        }
        return y;
    }

    static void frame(GuiGraphics graphics, int x, int y, int width, int height,
                      int accent, int body, int border) {
        WheelUi.card(graphics, x, y, width, height, 8, body, border);
        graphics.fill(x + 4, y + 8, x + 6, y + height - 8, accent);
    }

    static void icon(GuiGraphics graphics, int x, int y, ItemStack stack, int accent) {
        WheelUi.fillRounded(graphics, x + 9, y + 7, 20, 20, 5, WheelUi.tint(accent));
        WheelUi.itemIcon(graphics, stack, x + 11, y + 9, 16);
    }

    static void title(GuiGraphics graphics, Font font, int x, int y, String title, int color,
                      int maxWidth) {
        WheelUi.text(graphics, font, WheelUi.MONO_8,
                WheelUi.fit(font, WheelUi.MONO_8, WheelUi.upper(title), maxWidth),
                x + 34, y + 5, color, false);
    }

    static void keyChip(GuiGraphics graphics, Font font, int x, int y, int width, String keyLabel,
                        int accent) {
        if (keyLabel == null || keyLabel.isBlank()) {
            return;
        }
        int keyX = x + width - 20;
        WheelUi.fillRounded(graphics, keyX, y + 8, 14, 14, 4, WheelUi.withAlpha(accent, 0.14f));
        WheelUi.centered(graphics, font, WheelUi.MONO_8, keyLabel, keyX + 7, y + 11, accent, false);
    }

    /**
     * A refilling meter in the card's lower half. It is a mirror of the server cooldown, not the
     * authority: a dropped packet shortens the bar, never the wait.
     */
    static void meter(GuiGraphics graphics, int x, int y, int width, int height, float filled,
                      int accent) {
        int barX = x + 34;
        int barW = width - 44;
        graphics.fill(barX, y + 22, barX + barW, y + 24, 0x24FFFFFF);
        int px = Math.round(barW * Math.max(0f, Math.min(1f, filled)));
        if (px > 0) {
            graphics.fill(barX, y + 22, barX + px, y + 23, WheelUi.withAlpha(accent, 0.55f));
        }
    }

    /**
     * The switch idiom from the adaptation panel, shrunk into the card: a rounded track and a round
     * knob, plus the state word beside it. Drawing the same control at two sizes is what makes the
     * HUD read as part of the mod rather than as a label pasted over the game.
     */
    static void switchRow(GuiGraphics graphics, Font font, int x, int y, int width, boolean on,
                          String onText, String offText, int accent) {
        int trackW = 28;
        int trackH = 12;
        int trackX = x + 34;
        int trackY = y + 18;
        int track = on ? WheelUi.withAlpha(accent, 0.85f) : 0xFF28282D;
        WheelUi.fillRounded(graphics, trackX, trackY, trackW, trackH, trackH / 2, track);
        int knob = trackH - 4;
        int knobX = on ? trackX + trackW - knob - 2 : trackX + 2;
        WheelUi.fillRounded(graphics, knobX, trackY + 2, knob, knob, knob / 2, 0xFFF1EEE6);
        WheelUi.text(graphics, font, WheelUi.MONO_8, on ? onText : offText,
                trackX + trackW + 6, trackY + 2,
                on ? accent : WheelUi.MUTED, false);
    }

}
