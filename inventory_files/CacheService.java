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
    public static final int PANEL_WIDTH = 176;
    public static final int SLOT = 18;

    /** The vanilla inventory texture is exactly 176x166. */
    public static final int VANILLA_HEIGHT = 166;

    /** First cache slot when the cache is stacked under the vanilla inventory. */
    public static final int CACHE_TOP = 178;
    public static final int CACHE_LABEL_Y = 169;

    /** Total height of the vertical layout: vanilla inventory + a six-row cache extension. */
    public static final int PANEL_HEIGHT = CACHE_TOP + ROWS * SLOT + 8; // 294

    /*
     * Compact layout used when the window is not tall enough for the vertical screen. The cache
     * moves to the right of the vanilla inventory as 5 columns x 10 rows instead of 9 x 6.
     */
    public static final int SIDE_CACHE_X = 184;
    public static final int SIDE_CACHE_Y = 18;
    public static final int SIDE_COLUMNS = 5;
    public static final int SIDE_ROWS = 10;
    public static final int SIDE_WIDTH = SIDE_CACHE_X + SIDE_COLUMNS * SLOT + 8; // 282
    public static final int SIDE_HEIGHT = SIDE_CACHE_Y + SIDE_ROWS * SLOT + 8; // 206

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

    /**
     * Y of a cache slot in the vertical layout. Rows are below the vanilla inventory, so the
     * vanilla slots (result, crafting, armour, main, hotbar, offhand) keep their own positions.
     */
    public static int slotY(int row) {
        return CACHE_TOP + row * SLOT;
    }

    /** X of a cache slot in the compact, side-by-side layout. */
    public static int sideSlotX(int column) {
        return SIDE_CACHE_X + column * SLOT;
    }

    /** Y of a cache slot in the compact, side-by-side layout. */
    public static int sideSlotY(int row) {
        return SIDE_CACHE_Y + row * SLOT;
    }
}
