package ru.adaptionwheel.server;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import ru.adaptionwheel.data.PlayerAdaption;

public final class CacheService {

    private CacheService() {
    }

    /**
     * Six full rows of nine. It used to be fifty, which left four slots' worth of empty panel in
     * the bottom-right corner of a nine-wide grid -- a gap that reads as a missing feature rather
     * than as a round number, and the first thing anyone notices about the screen.
     */
    public static final int SLOTS = 54;
    public static final int COLUMNS = 9;
    public static final int ROWS = 6;
    public static final int PANEL_WIDTH = 176;
    public static final int SLOT = 18;

    /** The vanilla inventory texture is exactly 176x166. */
    public static final int VANILLA_HEIGHT = 166;

    /**
     * The cache extension is drawn, not blitted, by {@code client/CachePanel} using the vanilla
     * inventory's own colours and slot sprite. These are its internal offsets: a label strip, then
     * the grid, then the panel's bottom bevel.
     */
    public static final int CACHE_TOP_PAD = 14;
    public static final int CACHE_BOTTOM_PAD = 6;
    public static final int CACHE_TEXTURE_HEIGHT = CACHE_TOP_PAD + ROWS * SLOT + CACHE_BOTTOM_PAD;

    /** First cache slot when the cache is stacked under the vanilla inventory. */
    public static final int CACHE_TOP = VANILLA_HEIGHT + CACHE_TOP_PAD;
    public static final int CACHE_LABEL_Y = VANILLA_HEIGHT + 4;

    /** Total height of the vertical layout: vanilla inventory + the six-row cache extension. */
    public static final int PANEL_HEIGHT = VANILLA_HEIGHT + CACHE_TEXTURE_HEIGHT;

    /*
     * Compact layout used when the window is not tall enough for the vertical screen. The cache
     * keeps its nine columns and moves beside the inventory instead of under it, so both layouts
     * show the same grid and the same texture.
     */
    public static final int SIDE_CACHE_X = 176;
    public static final int SIDE_CACHE_Y = 0;
    public static final int SIDE_COLUMNS = COLUMNS;
    public static final int SIDE_ROWS = ROWS;
    public static final int SIDE_WIDTH = SIDE_CACHE_X + PANEL_WIDTH; // 352
    public static final int SIDE_HEIGHT = VANILLA_HEIGHT;

    public static PlayerAdaption data(Player player) {
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            return AdaptionEvents.dataOf(serverPlayer);
        }
        return new PlayerAdaption();
    }

    /** Pushes the cache mirror for the overlay drawn inside other container screens. */
    public static void send(net.minecraft.server.level.ServerPlayer player, PlayerAdaption data) {
        pad(data);
        ru.adaptionwheel.network.CacheSyncPayload.send(player, data.cache);
    }

    /**
     * Performs one click on a cache cell shown inside some other container's screen.
     *
     * <p>The cache is not a {@code Slot} there, so this is the whole click pipeline for it: pick up,
     * place, merge, split, swap and shift-to-inventory, against the menu's own carried stack. The
     * carried stack belongs to the menu, which is why the server can do this at all — the client
     * only names the cell and the button.
     */
    public static void click(net.minecraft.server.level.ServerPlayer player, int index, int button,
                             boolean shift) {
        if (!ru.adaptionwheel.config.AdaptionConfig.ENABLE_INVENTORY_ADAPTATION.get()) {
            return;
        }
        PlayerAdaption data = data(player);
        if (!data.active(ru.adaptionwheel.category.Concepts.ENV_INVENTORY)) {
            return;
        }
        if (index < 0 || index >= SLOTS) {
            return;
        }
        net.minecraft.world.inventory.AbstractContainerMenu menu = player.containerMenu;
        // The cache screen has real slots for these cells; a click that reaches here as well would
        // be applied twice.
        if (menu instanceof ru.adaptionwheel.menu.CacheMenu) {
            return;
        }
        pad(data);
        ItemStack slot = data.cache.get(index);
        ItemStack carried = menu.getCarried();

        if (shift) {
            if (slot.isEmpty()) {
                return;
            }
            ItemStack moving = slot.copy();
            player.getInventory().add(moving);
            data.cache.set(index, moving.isEmpty() ? ItemStack.EMPTY : moving);
        } else if (button == 0) {
            if (carried.isEmpty()) {
                menu.setCarried(slot.copy());
                data.cache.set(index, ItemStack.EMPTY);
            } else if (slot.isEmpty()) {
                data.cache.set(index, carried.copy());
                menu.setCarried(ItemStack.EMPTY);
            } else if (ItemStack.isSameItemSameComponents(carried, slot)) {
                int moved = Math.min(slot.getMaxStackSize() - slot.getCount(), carried.getCount());
                if (moved > 0) {
                    slot.grow(moved);
                    carried.shrink(moved);
                    data.cache.set(index, slot);
                    menu.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
                }
            } else {
                data.cache.set(index, carried.copy());
                menu.setCarried(slot.copy());
            }
        } else if (button == 1) {
            if (carried.isEmpty()) {
                if (slot.isEmpty()) {
                    return;
                }
                int half = (slot.getCount() + 1) / 2;
                ItemStack taken = slot.copyWithCount(half);
                slot.shrink(half);
                data.cache.set(index, slot.isEmpty() ? ItemStack.EMPTY : slot);
                menu.setCarried(taken);
            } else if (slot.isEmpty()) {
                data.cache.set(index, carried.copyWithCount(1));
                carried.shrink(1);
                menu.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
            } else if (ItemStack.isSameItemSameComponents(carried, slot)
                    && slot.getCount() < slot.getMaxStackSize()) {
                slot.grow(1);
                carried.shrink(1);
                data.cache.set(index, slot);
                menu.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
            }
        } else {
            return;
        }

        menu.broadcastChanges();
        send(player, data);
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
        return SIDE_CACHE_X + 8 + column * SLOT;
    }

    /** Y of a cache slot in the compact, side-by-side layout. */
    public static int sideSlotY(int row) {
        return SIDE_CACHE_Y + CACHE_TOP_PAD + row * SLOT;
    }
}
