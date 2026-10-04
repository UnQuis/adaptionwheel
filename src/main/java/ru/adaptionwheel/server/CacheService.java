package ru.adaptionwheel.server;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import ru.adaptionwheel.data.PlayerAdaption;

public final class CacheService {

    private CacheService() {
    }

    public static final int SLOTS = 50;
    public static final int COLUMNS = 9;
    public static final int ROWS = 6;
    public static final int GAP = 14;
    public static final int PANEL_WIDTH = 176;
    public static final int PANEL_HEIGHT = 200;
    public static final int SLOT = 18;

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

    public static int playerSlotY(int row) {
        return slotY(ROWS) + GAP + row * SLOT;
    }
}