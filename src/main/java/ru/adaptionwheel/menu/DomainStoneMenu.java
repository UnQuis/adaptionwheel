package ru.adaptionwheel.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import ru.adaptionwheel.block.ModBlocks;
import ru.adaptionwheel.category.WheelTier;
import ru.adaptionwheel.data.PlayerAdaption;
import ru.adaptionwheel.item.ModItems;
import ru.adaptionwheel.network.DomainStoneSyncPayload;
import ru.adaptionwheel.server.AdaptionEvents;
import ru.adaptionwheel.server.DomainExchange;
import ru.adaptionwheel.sound.ModSounds;

import java.util.List;

/**
 * The Domain Stone's screen: an offering item in, an adaptation out.
 *
 * <p><b>The server owns every number in here.</b> The candidate list, the selected row and the
 * requested level count all live on this object; the client holds a mirror and sends <em>intent</em>
 * — "row three", "three levels", "exchange" — never state. That is not defensiveness for its own
 * sake: a row is chosen by index, and the pool is rebuilt whenever anything in the menu changes, so
 * an index is potentially stale the moment it is sent. Re-deciding legality at the moment of the
 * exchange is what makes a stale index a refusal rather than a wrong grant.</p>
 *
 * <p><b>Its own container, not the player's.</b> The two slots wrap a {@link SimpleContainer}, never
 * player-inventory indices — those are already spoken for, since {@code addStandardInventorySlots}
 * lays the hotbar over indices 0-8 and a second {@code Slot} on the same index is how a menu ends
 * up rendering one item twice and moving it twice.</p>
 *
 * <p><b>No block entity.</b> The stone stays a plain {@code Block}, like every other block in the
 * mod, and the position arrives as menu-open data. A block entity would mean every stone a player
 * ever placed becoming something the server must keep loaded, ticked and saved, for a screen that
 * holds nothing between openings.</p>
 */
public class DomainStoneMenu extends AbstractContainerMenu {

    public static final int OFFER_SLOT = 0;
    public static final int WHEEL_SLOT = 1;
    private static final int INPUT_SIZE = 2;

    private static final int INV_START = INPUT_SIZE;
    private static final int INV_END = INV_START + 27;
    private static final int HOTBAR_END = INV_END + 9;

    // ---- The whole layout, and it lives here rather than in the screen.
    //
    // A slot's position and the well drawn behind it are two numbers that must agree, and when
    // they lived in two files they did not: the slots were placed at x=26 while the screen drew
    // their wells at x=8, so the items rendered outside their own squares. One declaration, read by
    // both sides, is the only version of this that cannot drift.
    public static final int IMAGE_WIDTH = 176;
    public static final int IMAGE_HEIGHT = 229;

    public static final int OFFER_X = 8;
    public static final int OFFER_Y = 20;
    public static final int WHEEL_X = 8;
    public static final int WHEEL_Y = 54;

    // The left column is 60 wide, and that is the whole reason for the number: at 112 the levels
    // bar and its two captions ran underneath the list panel and were drawn over by it. The list
    // starts at 72 to leave the gap.
    public static final int SLIDER_X = 8;
    public static final int SLIDER_Y = 84;
    public static final int SLIDER_W = 60;

    public static final int LIST_X = 72;
    public static final int LIST_Y = 20;
    public static final int LIST_W = IMAGE_WIDTH - LIST_X - 8;
    public static final int LIST_H = 92;

    public static final int BUTTON_X = 8;
    public static final int BUTTON_Y = 116;
    public static final int BUTTON_W = IMAGE_WIDTH - 16;
    public static final int BUTTON_H = 18;

    public static final int INV_X = 8;
    public static final int INV_Y = 149;
    public static final int INVENTORY_LABEL_Y = INV_Y - 11;

    private final BlockPos pos;
    private final boolean serverSide;
    private final Inventory playerInventory;
    private final SimpleContainer input = new SimpleContainer(INPUT_SIZE) {
        @Override
        public void setChanged() {
            super.setChanged();
            // The hook a Slot does not give for free: a SimpleContainer has no listener wiring back
            // into the menu, so without this a single feather landing in the slot would leave the
            // candidate list showing whatever was there before.
            recompute();
        }
    };

    private DomainExchange.Recipe recipe;
    private List<String> candidates = List.of();
    private int selectedIndex = -1;
    private int levels = 1;

    public DomainStoneMenu(int windowId, Inventory playerInv, BlockPos pos) {
        super(ModMenus.DOMAIN_STONE.get(), windowId);
        this.pos = pos.immutable();
        this.playerInventory = playerInv;
        // A client menu is built from the position alone and must not run any recomputation: there
        // is no PlayerAdaption to read, and the client's own state arrives by sync instead.
        this.serverSide = playerInv.player instanceof ServerPlayer;
        addSlot(new Slot(input, OFFER_SLOT, OFFER_X, OFFER_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                // Anything goes in; the recipe lookup decides what it buys. Refusing here would
                // mean a slot whose answer changes as the price list is retuned, and a slot that
                // rejects the item a player is holding is a worse thing to debug than an empty list.
                return true;
            }
        });
        addSlot(new Slot(input, WHEEL_SLOT, WHEEL_X, WHEEL_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return isWheel(stack);
            }
        });
        addPlayerInventory(playerInv, INV_X, INV_Y);
        if (serverSide) {
            recompute();
        }
    }

    /** Client factory. The position is the whole of the extra data the server sends. */
    public DomainStoneMenu(int windowId, Inventory playerInv, FriendlyByteBuf extra) {
        this(windowId, playerInv, extra.readBlockPos());
    }

    /**
     * The player's own 27 + 9, laid out from a top-left corner.
     *
     * <p>Written out rather than delegated: there is no {@code addStandardInventorySlots} in 1.21.1
     * vanilla — it is a NeoForge-era convenience that is not on {@code AbstractContainerMenu} —
     * and the layout has to match this menu's own slots rather than a default anyway.</p>
     */
    private void addPlayerInventory(Inventory inventory, int x, int y) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, 9 + col + row * 9, x + col * 18, y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, x + col * 18, y + 58));
        }
    }

    public static boolean isWheel(ItemStack stack) {
        return !stack.isEmpty()
                && (stack.is(ModItems.MAHORAGA_WHEEL.get()) || stack.is(ModItems.MAHORAGA_WHEEL_WOOD.get()));
    }

    @Override
    public boolean stillValid(Player player) {
        // 64.0 is the reach vanilla's own block menus use, measured from the entity rather than the
        // eye because Player has no getBlockReach() in 1.21.1.
        return player.level().getBlockState(pos).is(ModBlocks.DOMAIN_STONE.get())
                && player.distanceToSqr(Vec3.atCenterOf(pos)) <= 64.0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < INV_START) {
            if (!moveItemStackTo(stack, INV_START, HOTBAR_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (isWheel(stack)) {
            // Shift-clicking a wheel goes to the wheel slot rather than the hotbar, so feeding it
            // does not mean aiming at one particular square of a nine-wide grid. Nowhere else: a
            // wheel that does not fit the one slot it fits must not be scattered into the hotbar
            // instead, which is what this used to try.
            if (!moveItemStackTo(stack, WHEEL_SLOT, WHEEL_SLOT + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, OFFER_SLOT, OFFER_SLOT + 1, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return original;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (serverSide && !player.level().isClientSide()) {
            // These two slots are not the player's inventory, so nothing else would ever give the
            // contents back and they would vanish into the menu.
            clearContainer(player, input);
        }
    }

    // ------------------------------------------------------------------ server authority

    private ServerPlayer serverPlayer() {
        return serverSide && playerInventory.player instanceof ServerPlayer server ? server : null;
    }

    /** Rebuilds the pool from the slot contents and pushes it to the client. */
    public void recompute() {
        ServerPlayer player = serverPlayer();
        if (player == null) {
            return;
        }
        PlayerAdaption data = AdaptionEvents.dataOf(player);
        recipe = DomainExchange.recipeFor(input.getItem(OFFER_SLOT));
        candidates = DomainExchange.candidates(data, WheelTier.forCount(data.getAdaptCount()), recipe);
        if (selectedIndex >= candidates.size()) {
            // The pool shrank under the selection. Clear it rather than clamping, because a clamped
            // index would silently point at a neighbouring adaptation nobody asked for.
            selectedIndex = -1;
        }
        if (selectedIndex < 0 && !candidates.isEmpty()) {
            selectedIndex = 0;
        }
        levels = selectedConcept() == null ? 1 : DomainExchange.clampLevels(selectedConcept(), levels);
        sync(player);
    }

    /**
     * Selects a row. An index past the end clears the selection rather than clamping, for the same
     * reason {@link #recompute()} does.
     */
    public void select(int index) {
        ServerPlayer player = serverPlayer();
        if (player == null || index < -1 || index >= candidates.size()) {
            return;
        }
        selectedIndex = index;
        levels = 1;
        sync(player);
    }

    public void setLevels(int requested) {
        ServerPlayer player = serverPlayer();
        String concept = selectedConcept();
        if (player == null || concept == null) {
            return;
        }
        int clamped = DomainExchange.clampLevels(concept, requested);
        if (clamped == levels) {
            return;
        }
        levels = clamped;
        sync(player);
    }

    public String selectedConcept() {
        return selectedIndex >= 0 && selectedIndex < candidates.size() ? candidates.get(selectedIndex) : null;
    }

    /**
     * Performs the exchange: verify, take the price, grant.
     *
     * <p>Every check is against live state rather than against what the screen showed, and the
     * pool is rebuilt here rather than reused — the wheel may have finished that adaptation by some
     * other route while the screen was open, and paying for an adaptation already held is the worst
     * outcome available, because the item would be gone and nothing would have changed.</p>
     */
    public void exchange() {
        ServerPlayer player = serverPlayer();
        if (player == null) {
            return;
        }
        PlayerAdaption data = AdaptionEvents.dataOf(player);
        DomainExchange.Recipe current = DomainExchange.recipeFor(input.getItem(OFFER_SLOT));
        if (current == null) {
            refuse(player, "adaptionwheel.msg.stone_nothing");
            return;
        }
        if (!isWheel(input.getItem(WHEEL_SLOT))) {
            refuse(player, "adaptionwheel.msg.stone_no_wheel");
            return;
        }
        List<String> pool = DomainExchange.candidates(data,
                WheelTier.forCount(data.getAdaptCount()), current);
        if (selectedIndex < 0 || selectedIndex >= pool.size()) {
            refuse(player, "adaptionwheel.msg.stone_stale");
            return;
        }
        String concept = pool.get(selectedIndex);
        int wanted = DomainExchange.clampLevels(concept, levels);
        int cost = current.costFor(wanted);
        if (input.getItem(OFFER_SLOT).getCount() < cost) {
            refuse(player, "adaptionwheel.msg.stone_too_few");
            return;
        }

        input.getItem(OFFER_SLOT).shrink(cost);
        AdaptionEvents.grantConceptUpTo(player, data, concept, wanted);
        // The wheel is in this menu, not in a Curios slot, so the one-second item save is not
        // looking at it. Write it now so the stack the player is holding is the one just fed.
        AdaptionEvents.saveToStack(input.getItem(WHEEL_SLOT), data);

        Level level = player.level();
        level.playSound(null, pos, ModSounds.REF.get(), SoundSource.BLOCKS, 0.9f, 0.7f);
        // The grant already played the adaptation voice; the stone adds its low ring on top, so an
        // exchange sounds like the stone rather than like a task finishing in the next room.
        level.playSound(null, pos, ModSounds.ADAPT_VOICE.get(), SoundSource.BLOCKS, 0.5f, 0.6f);

        selectedIndex = -1;
        levels = 1;
        recompute();
    }

    private void refuse(ServerPlayer player, String key) {
        player.sendSystemMessage(Component.translatable(key), false);
        sync(player);
    }

    private void sync(ServerPlayer player) {
        DomainStoneSyncPayload.send(player, this);
    }

    /** Applies a server sync on the client. */
    public void acceptSync(List<String> pool, int selected, int wantedLevels) {
        this.candidates = pool;
        this.selectedIndex = selected;
        this.levels = wantedLevels;
    }

    // ------------------------------------------------------------------ read by both sides

    public List<String> candidates() {
        return candidates;
    }

    public int selectedIndex() {
        return selectedIndex;
    }

    public int levels() {
        return levels;
    }

    public DomainExchange.Recipe recipe() {
        return recipe;
    }

    public ItemStack offering() {
        return input.getItem(OFFER_SLOT);
    }

    public ItemStack wheelStack() {
        return input.getItem(WHEEL_SLOT);
    }

    public boolean hasWheel() {
        return isWheel(wheelStack());
    }

    /** How many items the current choice costs, for the screen to print beside the slider. */
    public int currentCost() {
        DomainExchange.Recipe current = recipe();
        return current == null ? 0 : current.costFor(levels);
    }

    /** Whether the current choice is affordable with what is actually in the slot. */
    public boolean canAfford() {
        return recipe() != null && selectedConcept() != null
                && offering().getCount() >= currentCost();
    }
}