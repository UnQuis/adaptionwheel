package ru.adaptionwheel.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

/** 26.3 port of 1.21.1 HudCards (GuiGraphics -> GuiGraphicsExtractor). */
final class HudCards {

    static final int WIDTH = 104;
    static final int HEIGHT = 34;
    static final int GAP = 4;

    private HudCards() {
    }

    static int x(int screenWidth, int cardWidth) {
        int hotbarRight = screenWidth / 2 + 91;
        int x = Math.min(screenWidth - cardWidth - 4, hotbarRight + 8);
        if (x < hotbarRight + 4 && x + cardWidth > screenWidth / 2 - 91) {
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

    static void frame(GuiGraphicsExtractor g, int x, int y, int width, int height,
                      int accent, int body, int border) {
        WheelUi.card(g, x, y, width, height, 8, body, border);
        g.fill(x + 4, y + 8, x + 6, y + height - 8, accent);
    }

    static void icon(GuiGraphicsExtractor g, int x, int y, ItemStack stack, int accent) {
        WheelUi.fillRounded(g, x + 9, y + 7, 20, 20, 5, WheelUi.tint(accent));
        WheelUi.itemIcon(g, stack, x + 11, y + 9, 16);
    }

    static void title(GuiGraphicsExtractor g, Font font, int x, int y, String title, int color,
                      int maxWidth) {
        WheelUi.text(g, font, WheelUi.MONO_8,
                WheelUi.fit(font, WheelUi.MONO_8, WheelUi.upper(title), maxWidth),
                x + 34, y + 5, color, false);
    }

    static void keyChip(GuiGraphicsExtractor g, Font font, int x, int y, int width, String keyLabel,
                        int accent) {
        if (keyLabel == null || keyLabel.isBlank()) {
            return;
        }
        int keyX = x + width - 20;
        WheelUi.fillRounded(g, keyX, y + 8, 14, 14, 4, WheelUi.withAlpha(accent, 0.14f));
        WheelUi.centered(g, font, WheelUi.MONO_8, keyLabel, keyX + 7, y + 11, accent, false);
    }

    static void meter(GuiGraphicsExtractor g, int x, int y, int width, int height, float filled,
                      int accent) {
        int barX = x + 34;
        int barW = width - 44;
        g.fill(barX, y + 22, barX + barW, y + 24, 0x24FFFFFF);
        int px = Math.round(barW * Math.max(0f, Math.min(1f, filled)));
        if (px > 0) {
            g.fill(barX, y + 22, barX + px, y + 23, WheelUi.withAlpha(accent, 0.55f));
        }
    }

    static void switchRow(GuiGraphicsExtractor g, Font font, int x, int y, int width, boolean on,
                          String onText, String offText, int accent) {
        int trackW = 28;
        int trackH = 12;
        int trackX = x + 34;
        int trackY = y + 18;
        int track = on ? WheelUi.withAlpha(accent, 0.85f) : 0xFF28282D;
        WheelUi.fillRounded(g, trackX, trackY, trackW, trackH, trackH / 2, track);
        int knob = trackH - 4;
        int knobX = on ? trackX + trackW - knob - 2 : trackX + 2;
        WheelUi.fillRounded(g, knobX, trackY + 2, knob, knob, knob / 2, 0xFFF1EEE6);
        WheelUi.text(g, font, WheelUi.MONO_8, on ? onText : offText,
                trackX + trackW + 6, trackY + 2,
                on ? accent : WheelUi.MUTED, false);
    }
}
