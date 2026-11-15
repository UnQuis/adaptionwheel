package ru.adaptionwheel.menu;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import ru.adaptionwheel.data.PlayerAdaption;
import ru.adaptionwheel.server.CacheService;

/**
 * Cache container menu for 26.3 integration.
 *
 * <p>This preserves the cache storage, slot layout, and shift-move semantics needed by the cache
 * screen and overlay sync/click flow. It is intentionally based on {@link AbstractContainerMenu}
 * for 26.3 API compatibility in this branch.
 */
public class CacheMenu extends AbstractContainerMenu {

    public static final int COLUMNS = CacheService.COLUMNS;

    /** First index of the player inventory slots in this menu. */
    public static final int PLAYER_SLOT_START = CacheService.SLOTS;
    public static final int PLAYER_INV_END = PLAYER_SLOT_START + 27;
    public static final int HOTBAR_END = PLAYER_INV_END + 9;

    private final PlayerAdaption data;
    private final SimpleContainer cache = new SimpleContainer(CacheService.SLOTS);

    public CacheMenu(int windowId, Inventory inventory, Player player) {
        super(ModMenus.CACHE.get(), windowId);
        this.data = CacheService.data(player);
        pushIn();

        // Cache slots (54 slots, 9 columns x 6 rows)
        for (int index = 0; index < CacheService.SLOTS; index++) {
            addSlot(new Slot(cache, index,
                    CacheService.slotX(index % COLUMNS),
                    CacheService.slotY(index / COLUMNS)));
        }

        // Player main inventory (3x9)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9,
                        CacheService.slotX(col),
                        CacheService.playerSlotY(row)));
            }
        }

        // Hotbar (9)
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col,
                    CacheService.slotX(col),
                    CacheService.playerSlotY(3)));
        }
    }

    public CacheMenu(int windowId, Player player) {
        this(windowId, player.getInventory(), player);
    }

    /**
     * Kept for 26.3 client integration; cache slot positions are stable in this menu layout.
     */
    public void relayoutCache(boolean side) {
        // no-op: 26.3 cache screen/menu currently uses the fixed cache-over-inventory layout.
    }

    private void pushIn() {
        CacheService.pad(data);
        for (int index = 0; index < CacheService.SLOTS; index++) {
            cache.setItem(index, data.cache.get(index));
        }
    }

    private void writeBack() {
        CacheService.pad(data);
        for (int index = 0; index < CacheService.SLOTS; index++) {
            data.cache.set(index, cache.getItem(index));
        }
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        writeBack();
    }

    @Override
    public void removed(Player player) {
        writeBack();
        super.removed(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();

        if (index < CacheService.SLOTS) {
            // From cache -> player inventory/hotbar
            if (!moveItemStackTo(stack, PLAYER_SLOT_START, HOTBAR_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (index >= PLAYER_SLOT_START && index < PLAYER_INV_END) {
            // From main inventory -> cache, then hotbar
            if (!moveItemStackTo(stack, 0, CacheService.SLOTS, false)
                    && !moveItemStackTo(stack, PLAYER_INV_END, HOTBAR_END, false)) {
                return ItemStack.EMPTY;
            }
        } else if (index >= PLAYER_INV_END && index < HOTBAR_END) {
            // From hotbar -> cache, then main inventory
            if (!moveItemStackTo(stack, 0, CacheService.SLOTS, false)
                    && !moveItemStackTo(stack, PLAYER_SLOT_START, PLAYER_INV_END, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        if (stack.getCount() == copy.getCount()) {
            return ItemStack.EMPTY;
        }

        slot.onTake(player, stack);
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    public SimpleContainer getCacheContainer() {
        return cache;
    }
}
