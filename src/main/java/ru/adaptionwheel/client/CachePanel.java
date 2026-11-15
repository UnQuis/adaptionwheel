package ru.adaptionwheel.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import ru.adaptionwheel.server.CacheService;

/**
 * The cache extension's artwork, drawn rather than blitted.
 */
public final class CachePanel {

    private static final Identifier SLOT_SPRITE =
            Identifier.withDefaultNamespace("container/slot");

    private static final int BODY = 0xFFC6C6C6;
    private static final int BEVEL = 0xFFFFFFFF;
    private static final int SHADOW = 0xFF555555;

    private CachePanel() {
    }

    /** Draws the panel and its slot sprites at {@code (x, y)}. */
    public static void draw(GuiGraphicsExtractor graphics, int x, int y) {
        draw(graphics, x, y, true);
    }

    /** Same panel, joined to the panel above it (no top bevel). */
    public static void drawJoined(GuiGraphicsExtractor graphics, int x, int y) {
        draw(graphics, x, y, false);
    }

    private static void draw(GuiGraphicsExtractor graphics, int x, int y, boolean topBevel) {
        int right = x + CacheService.PANEL_WIDTH - 2;
        int bottom = y + CacheService.CACHE_TEXTURE_HEIGHT - 2;
        int top = topBevel ? y + 3 : y + 1;

        graphics.fill(x + 1, top, right, bottom, BODY);
        if (topBevel) {
            graphics.fill(x + 1, y + 1, right, y + 3, BEVEL);
        }
        graphics.fill(x + 1, y + 1, x + 3, bottom, BEVEL);
        graphics.fill(right, y + 1, right + 1, bottom, SHADOW);
        graphics.fill(x + 1, bottom, right + 1, bottom + 1, SHADOW);

        for (int index = 0; index < CacheService.SLOTS; index++) {
            graphics.blitSprite(SLOT_SPRITE,
                    x + CacheService.slotX(index % CacheService.COLUMNS) - 1,
                    y + CacheService.CACHE_TOP_PAD
                            + (index / CacheService.COLUMNS) * CacheService.SLOT - 1,
                    18, 18);
        }
    }
}
