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
     * The drawn panel and the layout constants have to agree, and nothing else can notice when they
     * do not.
     *
     * <p>The committed texture once had four slot rows inside a panel sized for six, with a stray
     * black band where the last two should have been: every slot after the fourth was drawn onto
     * empty body, and the panel was the wrong height for the menu behind it. That is invisible to
     * compilation and to every other test here, because the constants were self-consistent — only the
     * image disagreed. So read the image's own header and compare.
     */
    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void theCacheTextureMatchesTheLayout(GameTestHelper helper) {
        String path = "/assets/adaptionwheel/textures/gui/container/cache.png";
        try (java.io.InputStream in = CacheLayoutTests.class.getResourceAsStream(path)) {
            helper.assertTrue(in != null, "cache.png is missing from the mod, expected at " + path);
            byte[] header = in.readNBytes(24);
            int width = ((header[16] & 0xFF) << 24) | ((header[17] & 0xFF) << 16)
                    | ((header[18] & 0xFF) << 8) | (header[19] & 0xFF);
            int height = ((header[20] & 0xFF) << 24) | ((header[21] & 0xFF) << 16)
                    | ((header[22] & 0xFF) << 8) | (header[23] & 0xFF);
            helper.assertTrue(width == CacheService.PANEL_WIDTH
                            && height == CacheService.CACHE_TEXTURE_HEIGHT,
                    "cache.png is " + width + "x" + height + " but the layout draws "
                            + CacheService.PANEL_WIDTH + "x" + CacheService.CACHE_TEXTURE_HEIGHT
                            + "; the grid would be cut off or padded with empty body");
        } catch (java.io.IOException e) {
            helper.assertTrue(false, "could not read cache.png: " + e);
        }
        helper.succeed();
    }
}
