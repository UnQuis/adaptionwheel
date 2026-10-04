package ru.adaptionwheel.test;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.server.CacheService;

/**
 * Pins the cache GUI's layout arithmetic.
 *
 * <p>These are pure numbers, so nothing about a wrong one throws: it draws slightly wrong, or puts a
 * slot over the player's hotbar. The offhand slot that used to sit in this GUI is the proof — it
 * pointed at {@code Inventory.getSelectionSize()} (45, a hotbar slot) at a Y coordinate one row
 * below the panel, and nothing anywhere complained. Pure arithmetic in a drawing routine is exactly
 * where a test earns its keep.
 */
@GameTestHolder(AdaptionWheel.MODID)
@PrefixGameTestTemplate(false)
public class CacheLayoutTests {

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void everySlotStaysInsideThePanel(GameTestHelper helper) {

        int bottom = CacheService.PANEL_HEIGHT;

        for (int row = 0; row < CacheService.ROWS; row++) {
            int y = CacheService.slotY(row);
            helper.assertTrue(y >= 0 && y + CacheService.SLOT <= bottom,
                    "cache row " + row + " sits at y=" + y + ", which leaves the "
                            + CacheService.SLOT + "-pixel panel of " + bottom);
        }
        for (int row = 0; row < 4; row++) {
            int y = CacheService.playerSlotY(row);
            helper.assertTrue(y >= 0 && y + CacheService.SLOT <= bottom,
                    "player inventory row " + row + " sits at y=" + y + ", below the panel of "
                            + bottom + " — this is where the removed offhand slot used to land");
        }
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void theHotbarIsSeparatedFromTheMainInventory(GameTestHelper helper) {

        int lastMainRow = CacheService.playerSlotY(2) + CacheService.SLOT;
        int hotbar = CacheService.playerSlotY(3);

        helper.assertTrue(hotbar > lastMainRow,
                "the hotbar at " + hotbar + " must not touch the main inventory ending at " + lastMainRow);
        helper.assertTrue(hotbar - lastMainRow == CacheService.HOTBAR_GAP,
                "vanilla separates them by " + CacheService.HOTBAR_GAP + " pixels, got "
                        + (hotbar - lastMainRow));
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void thePanelIsTallEnoughForEverythingItClaimsToShow(GameTestHelper helper) {

        // The label is positioned off the player-inventory row, so if the panel were sized from a
        // stale constant the label would draw over the slots with nothing to complain about.
        int labelY = CacheService.PLAYER_INV_Y - 11;
        helper.assertTrue(labelY > 0,
                "the inventory label at " + labelY + " is off the top of the panel");
        helper.assertTrue(CacheService.PANEL_HEIGHT
                        == CacheService.HOTBAR_Y + CacheService.SLOT + 7,
                "PANEL_HEIGHT must be derived from the hotbar, or every number above it drifts: got "
                        + CacheService.PANEL_HEIGHT);
        helper.assertTrue(CacheService.SLOTS == CacheService.COLUMNS * CacheService.ROWS
                        || CacheService.SLOTS < CacheService.COLUMNS * CacheService.ROWS,
                "50 slots do not divide into 9 columns, so the last row is partial by design");
        helper.succeed();
    }
}
