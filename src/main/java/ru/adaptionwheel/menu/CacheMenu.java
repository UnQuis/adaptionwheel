package ru.adaptionwheel.menu;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import ru.adaptionwheel.data.PlayerAdaption;
import ru.adaptionwheel.server.CacheService;

public class CacheMenu extends AbstractContainerMenu {

    public static final int COLUMNS = CacheService.COLUMNS;

    private final PlayerAdaption data;

    private final SimpleContainer cache = new SimpleContainer(CacheService.SLOTS);

    public CacheMenu(int windowId, Player player) {
        super(ModMenus.CACHE.get(), windowId);
        this.data = CacheService.data(player);
        pushIn();

        for (int index = 0; index < CacheService.SLOTS; index++) {
            addSlot(new Slot(cache, index,
                    CacheService.slotX(index % COLUMNS), CacheService.slotY(index / COLUMNS)));
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < COLUMNS; col++) {
                addSlot(new Slot(player.getInventory(), col + row * COLUMNS + 9,
                        CacheService.slotX(col), CacheService.playerSlotY(row)));
            }
        }
        for (int col = 0; col < COLUMNS; col++) {
            addSlot(new Slot(player.getInventory(), col, CacheService.slotX(col),
                    CacheService.playerSlotY(3)));
        }
        addSlot(new Slot(player.getInventory(), Inventory.getSelectionSize(),
                CacheService.slotX(8), CacheService.playerSlotY(4)));
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

    public int playerSlotBase() {
        return CacheService.SLOTS + CacheService.GAP;
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
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int playerBase = playerSlotBase();
        if (index < CacheService.SLOTS) {
            if (!moveItemStackTo(stack, playerBase, slots.size() - playerBase, true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, CacheService.SLOTS, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (slot.container == cache) {
            writeBack();
        }
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}