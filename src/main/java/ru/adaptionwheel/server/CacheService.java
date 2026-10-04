package ru.adaptionwheel.server;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import ru.adaptionwheel.data.PlayerAdaption;

public final class CacheService {

    private CacheService() {
    }

    public static final int SLOTS = 50;
    public static final int COLUMNS = 9;
    public static final int ROWS = 6; // ceil(50 / 9) → last row has 5 slots
    public static final int GAP = 14;
    public static final int PANEL_WIDTH = 176;
    public static final int SLOT = 18;

    /** Y of the first player-inventory row (main inv). */
    public static final int PLAYER_INV_Y = slotY(ROWS) + GAP; // 140

    /** Extra pixels between main inventory and hotbar (vanilla-like). */
    public static final int HOTBAR_GAP = 4;

    /** Y of the hotbar. */
    public static final int HOTBAR_Y = PLAYER_INV_Y + 3 * SLOT + HOTBAR_GAP; // 198

    /** Total GUI height: hotbar bottom + bottom padding. */
    public static final int PANEL_HEIGHT = HOTBAR_Y + SLOT + 7; // 223

    public static PlayerAdaption data(Player player) {
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            return AdaptionEvents.dataOf(serverPlayer);
        }
        return new PlayerAdaption();
    }

    public static void pad(PlayerAdaption data) {
        while (data.cache.size() < SLOTS) {
            data.cache.add(ItemStack.EMPTY);
        }
        while (data.cache.size() > SLOTS) {
            data.cache.remove(data.cache.size() - 1);
        }
    }

    public static boolean inventoryIsFull(Player player) {
        net.minecraft.world.entity.player.Inventory inventory = player.getInventory();
        for (int slot = 0; slot < net.minecraft.world.entity.player.Inventory.getSelectionSize() * 4; slot++) {
            if (inventory.getItem(slot).isEmpty()) {
                return false;
            }
        }
        return !inventory.offhand.get(0).isEmpty();
    }

    public static int slotX(int column) {
        return 8 + column * SLOT;
    }

    public static int slotY(int row) {
        return 18 + row * SLOT;
    }

    /**
     * Y coordinate for player inventory slots.
     * rows 0-2 → main inventory, row 3 → hotbar (with vanilla-style gap).
     */
    public static int playerSlotY(int row) {
        if (row < 3) {
            return PLAYER_INV_Y + row * SLOT;
        }
        return HOTBAR_Y;
    }
}
