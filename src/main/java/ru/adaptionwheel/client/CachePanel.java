package ru.adaptionwheel.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import ru.adaptionwheel.server.CacheService;

/**
 * The cache extension's artwork, drawn rather than blitted.
 *
 * <p>It used to be a generated PNG, and that file could disagree with the layout in a way nothing
 * could see: a version with four slot rows in a six-row panel shipped, and every slot past the
 * fourth drew onto empty body. Drawing it from the same constants the menu uses removes the second
 * copy of the truth.
 *
 * <p>The colours are vanilla's own panel pixels - {@code #C6C6C6} body, a two-pixel white bevel on
 * the top and left, {@code #555555} on the right and bottom, and one transparent pixel around it -
 * so the extension reads as part of the inventory it hangs off. Slots come from the game's own
 * {@code container/slot} sprite, which is exactly what the vanilla panels use.
 */
public final class CachePanel {

    private static final ResourceLocation SLOT_SPRITE =
            ResourceLocation.withDefaultNamespace("container/slot");

    private static final int BODY = 0xFFC6C6C6;
    private static final int BEVEL = 0xFFFFFFFF;
    private static final int SHADOW = 0xFF555555;

    private CachePanel() {
    }

    /**
     * Draws the panel and its 54 slot sprites at {@code (x, y)}.
     *
     * <p>The origin is the frame's top-left corner, and a cell's {@code (column, row)} item position
     * is {@code (x + 8 + 18 * column, y + CACHE_TOP_PAD + 18 * row)} - the same arithmetic
     * {@link CacheService#slotX(int)} and the menu's real slots use.
     */
    public static void draw(GuiGraphics graphics, int x, int y) {
        draw(graphics, x, y, true);
    }

    /**
     * The same panel, but joined to the panel above it instead of looking like a second window.
     *
     * <p>Stacked under the inventory the extension must not draw its own white top bevel: vanilla
     * ends a panel section with a {@code #555555} line, and a bright ridge immediately under it
     * reads as two separate windows stacked on top of each other. The seam is already there, so the
     * body simply continues from it.
     */
    public static void drawJoined(GuiGraphics graphics, int x, int y) {
        draw(graphics, x, y, false);
    }

    private static void draw(GuiGraphics graphics, int x, int y, boolean topBevel) {
        // One transparent pixel all round, then vanilla's own bevel order: white on the top and
        // left, 555555 on the right and bottom, body between.
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
            // The sprite is one pixel taller and wider than the cell and carries the slot's own
            // dark top/left edge, so the item sits inside it rather than on its corner.
            graphics.blitSprite(SLOT_SPRITE,
                    x + CacheService.slotX(index % CacheService.COLUMNS) - 1,
                    y + CacheService.CACHE_TOP_PAD
                            + (index / CacheService.COLUMNS) * CacheService.SLOT - 1,
                    18, 18);
        }
    }
}
