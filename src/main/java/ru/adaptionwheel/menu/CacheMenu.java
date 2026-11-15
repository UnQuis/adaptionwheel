package ru.adaptionwheel.menu;

import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.ArmorSlot;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import org.jetbrains.annotations.Nullable;
import ru.adaptionwheel.data.PlayerAdaption;
import ru.adaptionwheel.server.CacheService;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The adapted player's inventory.
 *
 * <p>This is deliberately a copy of vanilla's {@link net.minecraft.world.inventory.InventoryMenu}
 * slot layout rather than a subclass of that menu: {@code InventoryMenu} hard-codes a null menu type
 * and container id 0 in its super constructor, while this menu has to be a real registered type with
 * the server-assigned window id so it can be opened and synchronised normally. The crafting grid,
 * armour slots and offhand are therefore rebuilt here with the same positions, the same
 * {@link ArmorSlot}/{@link net.minecraft.world.inventory.ResultSlot} classes, and the same recipe update
 * as vanilla. It extends the recipe-book-capable menu hierarchy, so the recipe book keeps working
 * where the 26.3 screen/container pipeline supports it.
 *
 * <p>The cache is copied out of {@link PlayerAdaption} into a {@link SimpleContainer} because the
 * attachment is not a {@code Container}. It is written back on every broadcast and on close, which
 * is what makes a stack survive a crash or a logout while the screen is open.
 */
public class CacheMenu extends AbstractCraftingMenu {

    public static final int COLUMNS = CacheService.COLUMNS;

    public static final int RESULT_SLOT = 0;
    public static final int CRAFT_START = 1;
    public static final int ARMOR_START = 5;
    public static final int INV_START = 9;
    public static final int INV_END = 36;
    public static final int HOTBAR_START = 36;
    public static final int HOTBAR_END = 45;
    public static final int OFFHAND_SLOT = 45;
    public static final int CACHE_SLOT_START = 46;

    private static final EquipmentSlot[] ARMOR_ORDER = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private static final Map<EquipmentSlot, Identifier> EMPTY_ARMOR_ICONS = Map.of(
            EquipmentSlot.FEET, net.minecraft.world.inventory.InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS,
            EquipmentSlot.LEGS, net.minecraft.world.inventory.InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS,
            EquipmentSlot.CHEST, net.minecraft.world.inventory.InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE,
            EquipmentSlot.HEAD, net.minecraft.world.inventory.InventoryMenu.EMPTY_ARMOR_SLOT_HELMET);

    private final SimpleContainer cache = new SimpleContainer(CacheService.SLOTS);
    private final PlayerAdaption data;
    private final Player owner;
    public final boolean active;
    private boolean placingRecipe;

    public CacheMenu(int windowId, Inventory inventory, Player player) {
        super(ModMenus.CACHE.get(), windowId, 2, 2);
        this.owner = player;
        this.active = !player.level().isClientSide();
        this.data = CacheService.data(player);
        pushIn();

        // Result, then the 2x2 grid: identical positions to InventoryMenu.
        addResultSlot(player, 154, 28);
        addCraftingGridSlots(98, 18);

        // Four armour slots, in the same order and at the same positions as vanilla.
        for (int index = 0; index < 4; index++) {
            EquipmentSlot equipment = ARMOR_ORDER[index];
            addSlot(new ArmorSlot(inventory, owner, equipment, 39 - index,
                    8, 8 + index * 18, EMPTY_ARMOR_ICONS.get(equipment)));
        }

        // Main inventory and hotbar.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + (row + 1) * 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, 142));
        }

        // Offhand. The setByPlayer override is what tells the player about the equipment change.
        addSlot(new Slot(inventory, 40, 77, 62) {
            @Override
            public void setByPlayer(ItemStack next, ItemStack previous) {
                owner.onEquipItem(EquipmentSlot.OFFHAND, previous, next);
                super.setByPlayer(next, previous);
            }

            @Override
            public Identifier getNoItemIcon() {
                return net.minecraft.world.inventory.InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD;
            }
        });

        if (slots.size() != CACHE_SLOT_START) {
            throw new IllegalStateException("The vanilla inventory layout changed; cache slot indices are stale");
        }

        for (int index = 0; index < CacheService.SLOTS; index++) {
            addSlot(new Slot(cache, index,
                    CacheService.slotX(index % COLUMNS),
                    CacheService.slotY(index / COLUMNS)));
        }
    }

    /**
     * Rebuilds only the cache slot wrappers at the compact or vertical positions. Slot positions
     * are final, so the screen cannot move them in place; replacing the wrappers is safe because
     * they all point at the same cache container and the slot count is unchanged.
     */
    public void relayoutCache(boolean side) {
        for (int index = slots.size() - 1; index >= CACHE_SLOT_START; index--) {
            slots.remove(index);
        }
        int columns = side ? CacheService.SIDE_COLUMNS : CacheService.COLUMNS;
        for (int index = 0; index < CacheService.SLOTS; index++) {
            addSlot(new Slot(cache, index,
                    side ? CacheService.sideSlotX(index % columns) : CacheService.slotX(index % columns),
                    side ? CacheService.sideSlotY(index / columns) : CacheService.slotY(index / columns)));
        }
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
    public void slotsChanged(Container container) {
        if (container == this.craftSlots && !this.placingRecipe) {
            if (this.owner.level() instanceof ServerLevel level) {
                slotChangedCraftingGrid(this, level, this.owner, this.craftSlots, this.resultSlots, null);
            }
        }
    }

    private static void slotChangedCraftingGrid(
            AbstractCraftingMenu menu,
            ServerLevel level,
            Player player,
            CraftingContainer craftSlots,
            ResultContainer resultSlots,
            @Nullable RecipeHolder<CraftingRecipe> recipeHint) {
        CraftingInput input = craftSlots.asCraftInput();
        ServerPlayer serverPlayer = (ServerPlayer) player;
        ItemStack result = ItemStack.EMPTY;
        Optional<RecipeHolder<CraftingRecipe>> maybeRecipe = level.getServer().getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, input, level, recipeHint);
        if (maybeRecipe.isPresent()) {
            RecipeHolder<CraftingRecipe> recipeHolder = maybeRecipe.get();
            CraftingRecipe craftingRecipe = recipeHolder.value();
            if (resultSlots.setRecipeUsed(serverPlayer, recipeHolder)) {
                ItemStack assembled = craftingRecipe.assemble(input);
                if (assembled.isItemEnabled(level.enabledFeatures())) {
                    result = assembled;
                }
            }
        }

        resultSlots.setItem(0, result);
        menu.setRemoteSlot(0, result);
        serverPlayer.connection.send(new ClientboundContainerSetSlotPacket(
                menu.containerId, menu.incrementStateId(), 0, result));
    }

    @Override
    protected void beginPlacingRecipe() {
        this.placingRecipe = true;
    }

    @Override
    protected void finishPlacingRecipe(ServerLevel level, RecipeHolder<CraftingRecipe> recipe) {
        this.placingRecipe = false;
        slotChangedCraftingGrid(this, level, this.owner, this.craftSlots, this.resultSlots, recipe);
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
        this.resultSlots.clearContent();
        if (!player.level().isClientSide()) {
            clearContainer(player, this.craftSlots);
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        EquipmentSlot equipment = player.getEquipmentSlotForItem(stack);

        if (index == RESULT_SLOT) {
            if (!moveItemStackTo(stack, INV_START, HOTBAR_END, true)) {
                return ItemStack.EMPTY;
            }
            slot.onQuickCraft(stack, copy);
        } else if (index >= CRAFT_START && index < ARMOR_START) {
            if (!moveItemStackTo(stack, INV_START, HOTBAR_END, false)) {
                return ItemStack.EMPTY;
            }
        } else if (index >= ARMOR_START && index < INV_START) {
            if (!moveItemStackTo(stack, INV_START, HOTBAR_END, false)) {
                return ItemStack.EMPTY;
            }
        } else if (equipment.getType() == EquipmentSlot.Type.HUMANOID_ARMOR
                && !slots.get(8 - equipment.getIndex()).hasItem()) {
            int target = 8 - equipment.getIndex();
            if (!moveItemStackTo(stack, target, target + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (equipment == EquipmentSlot.OFFHAND && !slots.get(OFFHAND_SLOT).hasItem()) {
            if (!moveItemStackTo(stack, OFFHAND_SLOT, OFFHAND_SLOT + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (index >= CACHE_SLOT_START) {
            if (!moveItemStackTo(stack, INV_START, HOTBAR_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (index >= INV_START && index < INV_END) {
            if (!moveItemStackTo(stack, CACHE_SLOT_START, slots.size(), false)
                    && !moveItemStackTo(stack, HOTBAR_START, HOTBAR_END, false)) {
                return ItemStack.EMPTY;
            }
        } else if (index >= HOTBAR_START && index < HOTBAR_END) {
            if (!moveItemStackTo(stack, CACHE_SLOT_START, slots.size(), false)
                    && !moveItemStackTo(stack, INV_START, INV_END, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, INV_START, HOTBAR_END, false)) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY, copy);
        } else {
            slot.setChanged();
        }

        if (stack.getCount() == copy.getCount()) {
            return ItemStack.EMPTY;
        }

        slot.onTake(player, stack);
        if (index == RESULT_SLOT) {
            player.drop(stack, false, Prediction.PREDICTED);
        }
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public Slot getResultSlot() {
        return this.slots.get(RESULT_SLOT);
    }

    @Override
    public List<Slot> getInputGridSlots() {
        return this.slots.subList(CRAFT_START, ARMOR_START);
    }

    @Override
    public RecipeBookType getRecipeBookType() {
        return RecipeBookType.CRAFTING;
    }

    @Override
    protected Player owner() {
        return this.owner;
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.container != this.resultSlots && super.canTakeItemForPickAll(stack, slot);
    }
}
