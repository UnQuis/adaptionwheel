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

    /** The vanilla inventory texture is exactly 176x166. */
    public static final int VANILLA_HEIGHT = 166;

    /**
     * Additional layout constants kept for 26.3 parity/integration with screen-side layout logic.
     */
    public static final int CACHE_TOP_PAD = 14;
    public static final int CACHE_BOTTOM_PAD = 6;
    public static final int CACHE_TEXTURE_HEIGHT = CACHE_TOP_PAD + ROWS * SLOT + CACHE_BOTTOM_PAD;
    public static final int CACHE_TOP = VANILLA_HEIGHT + CACHE_TOP_PAD;
    public static final int CACHE_LABEL_Y = VANILLA_HEIGHT + 4;

    public static final int SIDE_CACHE_X = 176;
    public static final int SIDE_CACHE_Y = 0;
    public static final int SIDE_COLUMNS = COLUMNS;
    public static final int SIDE_ROWS = ROWS;
    public static final int SIDE_WIDTH = SIDE_CACHE_X + PANEL_WIDTH; // 352
    public static final int SIDE_HEIGHT = VANILLA_HEIGHT;

    public static PlayerAdaption data(Player player) {
        // Always read off the player's live attachment. The old `new PlayerAdaption()` fallback on
        // the client path was returning a BRAND NEW empty instance every call -- which meant every
        // client-side caller that touched `data.cache` was mutating a local list that nothing
        // persisted. On top of that, any code path that called this method during a menu open on a
        // non-ServerPlayer shadowed the real attachment with an empty list and "saw" an empty
        // cache, which is one of the ways the extra inventory could look wiped across a relog.
        return player.getData(ru.adaptionwheel.data.AttachmentTypes.ADAPTION.get());
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
        return !player.getOffhandItem().isEmpty();
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

    /** X of a cache slot in the compact, side-by-side layout. */
    public static int sideSlotX(int column) {
        return SIDE_CACHE_X + 8 + column * SLOT;
    }

    /** Y of a cache slot in the compact, side-by-side layout. */
    public static int sideSlotY(int row) {
        return SIDE_CACHE_Y + CACHE_TOP_PAD + row * SLOT;
    }
}
