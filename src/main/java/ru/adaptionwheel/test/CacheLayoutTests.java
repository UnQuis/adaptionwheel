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
                        == CacheService.CACHE_TOP + CacheService.ROWS * CacheService.SLOT + 8,
                "the panel height must be derived from the cache rows, got " + CacheService.PANEL_HEIGHT);
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void theCompactLayoutHoldsAllFiftySlots(GameTestHelper helper) {
        helper.assertTrue(CacheService.SIDE_COLUMNS * CacheService.SIDE_ROWS >= CacheService.SLOTS,
                "the side layout must have room for every cache slot");
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
        helper.assertTrue(CacheMenu.CACHE_SLOT_START + CacheService.SLOTS == 96,
                "the combined menu must expose 96 slots, got "
                        + (CacheMenu.CACHE_SLOT_START + CacheService.SLOTS));
        helper.succeed();
    }
}
