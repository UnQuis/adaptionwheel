package ru.adaptionwheel.test;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.menu.CacheMenu;
import ru.adaptionwheel.server.CacheService;

/**
 * Pins the combined inventory/cache layout arithmetic.
 *
 * <p>These are pure numbers, so nothing about a wrong one throws: it draws slightly wrong, or puts a
 * slot over the player's hotbar. The offhand slot that once landed one row below the old panel is
 * the proof that this deserves a test.
 */
@GameTestHolder(AdaptionWheel.MODID)
@PrefixGameTestTemplate(false)
public class CacheLayoutTests {

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void everyCacheRowStaysInsideTheVerticalPanel(GameTestHelper helper) {
        for (int row = 0; row < CacheService.ROWS; row++) {
            int y = CacheService.slotY(row);
            helper.assertTrue(y >= CacheService.VANILLA_HEIGHT && y + CacheService.SLOT <= CacheService.PANEL_HEIGHT,
                    "cache row " + row + " sits at y=" + y + ", outside the extended panel of "
                            + CacheService.PANEL_HEIGHT);
        }
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void theVanillaInventoryKeepsItsOwnSpace(GameTestHelper helper) {
        helper.assertTrue(CacheService.CACHE_LABEL_Y > CacheService.VANILLA_HEIGHT,
                "the cache label must sit below the 166-pixel vanilla texture");
        helper.assertTrue(CacheService.CACHE_TOP >= CacheService.VANILLA_HEIGHT + 10,
                "the first cache slot needs room for the label under the vanilla inventory");
        helper.assertTrue(CacheService.PANEL_HEIGHT
                        == CacheService.VANILLA_HEIGHT + CacheService.CACHE_TEXTURE_HEIGHT,
                "the panel height must be the inventory plus the cache texture, got "
                        + CacheService.PANEL_HEIGHT);
        helper.assertTrue(CacheService.CACHE_TEXTURE_HEIGHT
                        == CacheService.CACHE_TOP_PAD + CacheService.ROWS * CacheService.SLOT
                        + CacheService.CACHE_BOTTOM_PAD,
                "the cache texture must be its label strip, its grid and its bottom bevel, got "
                        + CacheService.CACHE_TEXTURE_HEIGHT);
        helper.succeed();
    }

    /**
     * The grid has to be a full rectangle. At fifty slots a nine-wide grid left four empty cells in
     * the bottom-right corner, which everyone reads as slots that failed to render.
     */
    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void theCacheGridIsAFullRectangle(GameTestHelper helper) {
        helper.assertTrue(CacheService.SLOTS == CacheService.COLUMNS * CacheService.ROWS,
                CacheService.SLOTS + " slots do not fill " + CacheService.COLUMNS + "x"
                        + CacheService.ROWS + "; the leftover cells draw as a hole in the panel");
        int lastX = CacheService.slotX(CacheService.COLUMNS - 1);
        int lastY = CacheService.slotY(CacheService.ROWS - 1);
        helper.assertTrue(lastX + CacheService.SLOT <= CacheService.PANEL_WIDTH,
                "the last cache column ends at " + (lastX + CacheService.SLOT) + ", past the panel");
        helper.assertTrue(lastY + CacheService.SLOT <= CacheService.PANEL_HEIGHT,
                "the last cache row ends at " + (lastY + CacheService.SLOT) + ", past the panel");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void theCompactLayoutShowsTheSameGrid(GameTestHelper helper) {
        helper.assertTrue(CacheService.SIDE_COLUMNS * CacheService.SIDE_ROWS >= CacheService.SLOTS,
                "the side layout must have room for every cache slot");
        helper.assertTrue(CacheService.SIDE_COLUMNS == CacheService.COLUMNS,
                "the side layout must not reshape the cache; it only moves it");
        int lastX = CacheService.sideSlotX(CacheService.SIDE_COLUMNS - 1);
        int lastY = CacheService.sideSlotY(CacheService.SIDE_ROWS - 1);
        helper.assertTrue(lastX + CacheService.SLOT <= CacheService.SIDE_WIDTH,
                "the last side column ends at " + (lastX + CacheService.SLOT)
                        + ", past the " + CacheService.SIDE_WIDTH + "-pixel panel");
        helper.assertTrue(lastY + CacheService.SLOT <= CacheService.SIDE_HEIGHT,
                "the last side row ends at " + (lastY + CacheService.SLOT)
                        + ", past the " + CacheService.SIDE_HEIGHT + "-pixel panel");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void theCacheSlotsBeginAfterVanillasFortySix(GameTestHelper helper) {
        helper.assertTrue(CacheMenu.CACHE_SLOT_START == 46,
                "vanilla's inventory has 46 slots (result, 4 craft, 4 armour, 36 inventory, offhand)");
        helper.assertTrue(CacheMenu.CACHE_SLOT_START + CacheService.SLOTS == 100,
                "the combined menu must expose 100 slots, got "
                        + (CacheMenu.CACHE_SLOT_START + CacheService.SLOTS));
        helper.succeed();
    }

    /**
     * The slot sprites must stay inside the frame's bevel.
     *
     * <p>The sprite is 18x18 and is drawn one pixel up and left of the item, while the frame's
     * white bevel occupies the first two pixels - so a cell placed too high or too far left paints
     * over the bevel, and a grid sized without the sprite's extra pixel clips its last row. Both are
     * invisible to the menu (which only ever places items) and to the compiler.
     */
    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void theSlotSpritesStayInsideTheFrame(GameTestHelper helper) {
        int firstX = CacheService.slotX(0) - 1;
        int lastX = CacheService.slotX(CacheService.COLUMNS - 1) - 1 + 18;
        helper.assertTrue(firstX >= 3, "the first sprite starts at " + firstX
                + " and covers the frame's white bevel");
        helper.assertTrue(lastX <= CacheService.PANEL_WIDTH - 2, "the last sprite ends at " + lastX
                + ", past the frame's right edge");

        int firstY = CacheService.CACHE_TOP_PAD - 1;
        int lastY = CacheService.CACHE_TOP_PAD + CacheService.ROWS * CacheService.SLOT + 1;
        helper.assertTrue(firstY >= 3, "the first sprite starts at " + firstY
                + " and covers the frame's white bevel");
        helper.assertTrue(lastY <= CacheService.CACHE_TEXTURE_HEIGHT - 2,
                "the last sprite ends at " + lastY + ", past the frame's bottom edge of "
                        + CacheService.CACHE_TEXTURE_HEIGHT);
        helper.succeed();
    }
}
