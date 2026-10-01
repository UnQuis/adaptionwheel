package ru.adaptionwheel.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import ru.adaptionwheel.category.Concepts;
import ru.adaptionwheel.category.WheelTier;
import ru.adaptionwheel.data.PlayerAdaption;
import ru.adaptionwheel.item.ModItems;
import ru.adaptionwheel.network.TradeSyncPayload;
import ru.adaptionwheel.server.AdaptionEvents;
import ru.adaptionwheel.server.DomainExchange;

import java.util.List;

/**
 * What the Domain Stone and the Resonance Altar have in common: two slots, a wheel, a list of
 * adaptations, a price, and a server that decides.
 *
 * <p>Two blocks now trade, and the mechanics of the trade are identical — the item narrows the
 * pool, the wheel must be present, the pool is rebuilt on every change, the selection is an index,
 * and the price is the item plus experience. Only the answer to "which adaptations does this item
 * open" differs. So that answer is the one thing left abstract, and everything that would otherwise
 * be copied between two menus is written once here.</p>
 *
 * <p>Copying it instead would be the same trap the rest of this mod has walked into three times:
 * two copies of a rule, one of them fixed later, and nothing that notices. So the shared rule lives
 * here and the two subclasses only say what they sell.</p>
 *
 * @see ru.adaptionwheel.server.DomainExchange for the price list
 * @see ru.adaptionwheel.server.AltarOfferings for the altar's item-to-mob index
 */
public abstract class TradeMenu extends AbstractContainerMenu {

    public static final int OFFER_SLOT = 0;
    public static final int WHEEL_SLOT = 1;
    private static final int INPUT_SIZE = 2;

    private static final int INV_START = INPUT_SIZE;
    private static final int INV_END = INV_START + 27;
    private static final int HOTBAR_END = INV_END + 9;

    // ---- The whole layout, and it lives here rather than in the screens.
    //
    // A slot's position and the well drawn behind it are two numbers that must agree, and when they
    // lived in two files they did not: the slots were placed at x=26 while the screen drew their
    // wells at x=8, so the items rendered outside their own squares. One declaration, read by both
    // sides, is the only version of this that cannot drift.
    public static final int IMAGE_WIDTH = 176;
    public static final int IMAGE_HEIGHT = 229;

    public static final int OFFER_X = 8;
    public static final int OFFER_Y = 20;
    public static final int WHEEL_X = 8;
    public static final int WHEEL_Y = 54;

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

    private List<String> candidates = List.of();
    private int selectedIndex = -1;

    /** The two slots are the menu's own, never the player's — see the class comment. */
    private final SimpleContainer input = new SimpleContainer(INPUT_SIZE) {
        @Override
        public void setChanged() {
            super.setChanged();
            // The hook a Slot does not give for free: a SimpleContainer has no listener wiring
            // back into the menu, so without this a single item landing in the slot would leave the
            // candidate list showing whatever was there before.
            recompute();
        }
    };

    /**
     * @param type the menu type, passed in rather than asked for: an abstract method cannot be
     *             called from a super constructor, and the alternative -- a registration lookup by
     *             class name -- is exactly the kind of reflection this mod has been bitten by.
     */
    protected TradeMenu(int windowId, Inventory playerInv, BlockPos pos,
                        net.minecraft.world.inventory.MenuType<?> type) {
        super(type, windowId);
        this.pos = pos.immutable();
        this.playerInventory = playerInv;
        // A client menu is built from the position alone and must not run any recomputation: there
        // is no PlayerAdaption to read, and the client's own state arrives by sync instead.
        this.serverSide = playerInv.player instanceof ServerPlayer;
        addSlot(new Slot(input, OFFER_SLOT, OFFER_X, OFFER_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                // Anything goes in; what it buys is decided by the lookup. Refusing here would
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
        // 26.3 has addStandardInventorySlots on AbstractContainerMenu: main grid at `top`,
        // hotbar at top + 58, with a 4 px separator. Same layout the hand-rolled 1.21.1 helper
        // produced, so the constants above carry over unchanged.
        addStandardInventorySlots(playerInv, INV_X, INV_Y);
        if (serverSide) {
            recompute();
        }
    }

    /** Client factory. The position is the whole of the extra data the server sends. */
    protected TradeMenu(int windowId, Inventory playerInv, net.minecraft.network.FriendlyByteBuf extra,
                        net.minecraft.world.inventory.MenuType<?> type) {
        this(windowId, playerInv, extra.readBlockPos(), type);
    }

    /**
     * What the item in the offer slot would buy.
     *
     * <p>Two blocks, two answers. The stone asks its price list; the altar asks which mobs drop this
     * item and then sells those mobs' adaptations.</p>
     */
    protected abstract List<String> candidatesFor(ServerPlayer player, PlayerAdaption data,
                                                  ItemStack offering);

    /** How many of the item one exchange consumes. */
    protected abstract int itemPrice(ServerPlayer player, ItemStack offering);

    /**
     * Grants the chosen adaptation, in whatever way that concept is meant to be earned.
     *
     * <p>Separate from the trade because the two concepts are not the same kind of thing: a
     * {@code Drop_NPC_} level is *derived* from a kill count, so an altar that set the level
     * directly would leave that counter lying about why the player has it.</p>
     */
    protected abstract void grant(ServerPlayer player, PlayerAdaption data, String concept);

    public String titleKey() {
        return "container.adaptionwheel.domain_stone";
    }

    /** Extra line under the list, or null. The altar uses it to name the mob it is selling. */
    public Component subtitle(String concept) {
        return null;
    }

    public static boolean isWheel(ItemStack stack) {
        return !stack.isEmpty()
                && (stack.is(ModItems.MAHORAGA_WHEEL.get()) || stack.is(ModItems.MAHORAGA_WHEEL_WOOD.get()));
    }

    @Override
    public boolean stillValid(Player player) {
        // 64.0 is the reach vanilla's own block menus use, measured from the entity rather than the
        // eye because Player has no getBlockReach() in 1.21.1. 26.3 spells the same check
        // isWithinBlockInteractionRange(pos, 4.0).
        return isTradeBlock(player.level().getBlockState(pos))
                && player.isWithinBlockInteractionRange(pos, 4.0);
    }

    /** Which block this menu belongs to. Subclasses answer for themselves. */
    protected abstract boolean isTradeBlock(net.minecraft.world.level.block.state.BlockState state);

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
        candidates = candidatesFor(player, data, input.getItem(OFFER_SLOT));
        if (selectedIndex >= candidates.size()) {
            // The pool shrank under the selection. Clear it rather than clamping, because a clamped
            // index would silently point at a neighbouring adaptation nobody asked for.
            selectedIndex = -1;
        }
        if (selectedIndex < 0 && !candidates.isEmpty()) {
            selectedIndex = 0;
        }
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
        sync(player);
    }

    public String selectedConcept() {
        return selectedIndex >= 0 && selectedIndex < candidates.size() ? candidates.get(selectedIndex) : null;
    }

    /**
     * Performs the exchange: verify, take the price, grant.
     *
     * <p>Every check is against live state rather than against what the screen showed, and the pool
     * is rebuilt here rather than reused — the wheel may have finished that adaptation by some
     * other route while the screen was open, and paying for an adaptation already held is the worst
     * outcome available, because the item would be gone and nothing would have changed.</p>
     */
    public void exchange() {
        ServerPlayer player = serverPlayer();
        if (player == null) {
            return;
        }
        PlayerAdaption data = AdaptionEvents.dataOf(player);
        ItemStack offering = input.getItem(OFFER_SLOT);
        List<String> pool = candidatesFor(player, data, offering);
        if (pool.isEmpty()) {
            refuse(player, "adaptionwheel.msg.stone_nothing");
            return;
        }
        if (!isWheel(input.getItem(WHEEL_SLOT))) {
            refuse(player, "adaptionwheel.msg.stone_no_wheel");
            return;
        }
        if (selectedIndex < 0 || selectedIndex >= pool.size()) {
            refuse(player, "adaptionwheel.msg.stone_stale");
            return;
        }
        String concept = pool.get(selectedIndex);
        int items = itemPrice(player, offering);
        if (items <= 0 || offering.getCount() < items) {
            refuse(player, "adaptionwheel.msg.stone_too_few");
            return;
        }
        // Experience is the second price. Checked before either is taken, so a refusal costs the
        // player nothing.
        int price = DomainExchange.priceFor(concept);
        if (player.experienceLevel < price) {
            player.sendSystemMessage(Component.translatable("adaptionwheel.msg.stone_no_experience",
                    price, player.experienceLevel));
            sync(player);
            return;
        }

        offering.shrink(items);
        // giveExperienceLevels takes a negative amount -- this is exactly how vanilla charges an
        // enchanting table, so the client's experience bar updates through the normal path.
        player.giveExperienceLevels(-price);
        grant(player, data, concept);
        // The wheel is in this menu, not in a Curios slot, so the one-second item save is not
        // looking at it. Write it now so the stack the player is holding is the one just fed.
        AdaptionEvents.saveToStack(input.getItem(WHEEL_SLOT), data);

        Level level = player.level();
        level.playSound(null, pos, ru.adaptionwheel.sound.ModSounds.REF.get(),
                net.minecraft.sounds.SoundSource.BLOCKS, 0.9f, 0.7f);
        // The grant already played the adaptation voice; the block adds its ring on top, so an
        // exchange sounds like the block rather than like a task finishing in the next room.
        level.playSound(null, pos, ru.adaptionwheel.sound.ModSounds.ADAPT_VOICE.get(),
                net.minecraft.sounds.SoundSource.BLOCKS, 0.5f, 0.6f);

        selectedIndex = -1;
        recompute();
    }

    private void refuse(ServerPlayer player, String key) {
        player.sendSystemMessage(Component.translatable(key));
        sync(player);
    }

    private void sync(ServerPlayer player) {
        TradeSyncPayload.send(player, this);
    }

    /** Applies a server sync on the client. */
    public void acceptSync(List<String> pool, int selected) {
        this.candidates = pool;
        this.selectedIndex = selected;
    }

    // ------------------------------------------------------------------ read by both sides

    public BlockPos stonePos() {
        return pos;
    }

    public List<String> candidates() {
        return candidates;
    }

    public int selectedIndex() {
        return selectedIndex;
    }

    public ItemStack offering() {
        return input.getItem(OFFER_SLOT);
    }

    /** Whether anything at all is in the offering slot. */
    public boolean hasOffering() {
        return !offering().isEmpty();
    }

    public boolean hasWheel() {
        return isWheel(input.getItem(WHEEL_SLOT));
    }

    /** How many items the current trade consumes. */
    public int itemCost() {
        ServerPlayer player = serverPlayer();
        return player == null ? 0 : itemPrice(player, offering());
    }

    /** What the current choice costs in whole levels of the player's own experience. */
    public int xpPrice() {
        return DomainExchange.priceFor(selectedConcept());
    }

    /** Whether both prices are covered: the items in the slot and the levels the player holds. */
    public boolean canAfford(int playerLevel) {
        String concept = selectedConcept();
        int items = itemCost();
        return concept != null && items > 0 && offering().getCount() >= items
                && playerLevel >= xpPrice();
    }
}