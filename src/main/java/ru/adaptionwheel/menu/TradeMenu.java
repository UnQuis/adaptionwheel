package ru.adaptionwheel.menu;

import net.minecraft.core.BlockPos;
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
    /**
     * The item price of the current trade, as of the last {@link #recompute()}.
     *
     * <p>Server-computed and synced; see {@link #itemCost()}. Set in the same pass as the pool,
     * because it is a property of the offering in the slot and must move with it.</p>
     */
    private int itemCost;

    // The wheel being fed, and the stack its state was read from.
    //
    // This is the field the whole bug lived in. A trade's subject is the stack in the wheel slot,
    // but the player's attachment belongs to the wheel they took OFF to put it there — and
    // unequipping empties that attachment. Granting into the attachment and writing it onto the fed
    // stack therefore replaced every adaptation on the wheel with just the one bought, which is
    // what was reported. So the fed wheel gets its own detached state, read off its own
    // wheel_data, and nothing here touches the player's attachment.
    private PlayerAdaption fed;
    private ItemStack fedStack;

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
     *
     * <p><b>The pool is a property of the offering item, never of the wheel's contents.</b> A pool
     * filtered by "what this wheel has already finished" is the list emptying itself as the player
     * buys, and the same row appearing and vanishing depending on which of the two slots happened to
     * be filled. The wheel is still consulted — for its tier, which orders the list — and
     * {@link #exchange()} is still the thing that refuses an adaptation the wheel already has, but it
     * refuses with a message instead of silently removing the row.</p>
     */
    protected abstract List<String> candidatesFor(ServerPlayer player, PlayerAdaption data,
                                                  ItemStack offering);

    /** How many of the item one exchange consumes. */
    protected abstract int itemPrice(ServerPlayer player, ItemStack offering);

    /**
     * Grants the chosen adaptation into the wheel being fed, and writes it onto that wheel.
     *
     * <p>Separate from the trade because the two concepts are not the same kind of thing: a
     * {@code Drop_NPC_} level is <em>derived</em> from a kill count, so buying it has to pay in
     * kills rather than assign the level. That difference lives in one place —
     * {@code AdaptionEvents.grantToWheel} — rather than in each block, so both buy the same
     * thing.</p>
     *
     * @param fed   the fed wheel's own state, from {@link #fedData()}; never the player's attachment
     * @param wheel the stack to write back onto
     */
    protected abstract void grant(ServerPlayer player, PlayerAdaption fed, ItemStack wheel,
                                  String concept);

    public String titleKey() {
        return "container.adaptionwheel.domain_stone";
    }

    /** Extra line under the list, or null. The altar uses it to name the mob it is selling. */
    public Component subtitle(String concept) {
        return null;
    }

    /**
     * The player's own 27 + 9, laid out from a top-left corner.
     *
     * <p>Written out rather than delegated: 1.21.1 vanilla has no
     * {@code addStandardInventorySlots} on {@code AbstractContainerMenu} — 26.3 grew one with
     * exactly this layout, which is why the constants above carry over unchanged between branches.</p>
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

    /**
     * The wheel in the wheel slot's own adaptations, detached from the player.
     *
     * <p>Reread whenever the slot holds a different stack — which is the moment the player swaps in
     * another wheel, or takes the one they were holding out. Identity comparison rather than a
     * count comparison, because {@code saveToStack} mutates the same stack instance and a fresh
     * instance is exactly what "a different wheel" means.</p>
     *
     * <p>Never null, and an empty slot gives an empty state rather than nothing: the list still has
     * to show what the offering opens, and it is the exchange that refuses when no wheel is
     * present, not the list.</p>
     */
    private PlayerAdaption fedData() {
        ItemStack stack = input.getItem(WHEEL_SLOT);
        if (fed == null || fedStack != stack) {
            fed = AdaptionEvents.readFrom(stack);
            fedStack = stack;
        }
        return fed;
    }

    /** Rebuilds the pool from the slot contents and pushes it to the client. */
    public void recompute() {
        ServerPlayer player = serverPlayer();
        if (player == null) {
            return;
        }
        // Nothing is offered without a wheel in the wheel slot.
        //
        // The purchase is written onto the stack in that slot, so with the slot empty there is
        // nothing the block could honestly be selling — and showing a pool anyway meant the list
        // appeared the moment an item landed in the offering slot and then *shrank* when the wheel
        // went in, because the wheel is where "already learned" is read from. Two different answers
        // to the same question depending on which slot was filled, from two blocks.
        if (isWheel(input.getItem(WHEEL_SLOT))) {
            candidates = candidatesFor(player, fedData(), input.getItem(OFFER_SLOT));
        } else {
            candidates = List.of();
        }
        // The pool shrank under the selection, or there is no pool at all. Clear rather than clamp:
        // a clamped index would silently point at a neighbouring adaptation nobody asked for.
        if (selectedIndex < 0 || selectedIndex >= candidates.size()) {
            selectedIndex = candidates.isEmpty() ? -1 : 0;
        }
        itemCost = itemPrice(player, input.getItem(OFFER_SLOT));
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
     * is rebuilt here rather than reused. That is not paranoia about the pool's <em>contents</em> —
     * the pool is a function of the offering item alone — but about the wheel: it may have finished
     * the chosen adaptation by some other route while the screen was open, and charging for one the
     * wheel already holds is the worst outcome available, because the item would be gone and nothing
     * would have changed. So it is refused by name, before either price is taken.</p>
     */
    public void exchange() {
        ServerPlayer player = serverPlayer();
        if (player == null) {
            return;
        }
        ItemStack offering = input.getItem(OFFER_SLOT);
        ItemStack wheel = input.getItem(WHEEL_SLOT);
        PlayerAdaption fed = fedData();
        // The wheel first, and for the same reason recompute() checks it first: with the slot empty
        // there is no offer at all, and the message that says so is the one about the wheel.
        if (!isWheel(wheel)) {
            refuse(player, "adaptionwheel.msg.stone_no_wheel");
            return;
        }
        List<String> pool = candidatesFor(player, fed, offering);
        if (pool.isEmpty()) {
            refuse(player, "adaptionwheel.msg.stone_nothing");
            return;
        }
        if (selectedIndex < 0 || selectedIndex >= pool.size()) {
            refuse(player, "adaptionwheel.msg.stone_stale");
            return;
        }
        String concept = pool.get(selectedIndex);
        // The list does not filter itself by what the wheel already has -- a pool that emptied
        // itself as the wheel filled up was the bug, not the feature -- so this is where the
        // question is answered instead. Checked before either price is taken, so it costs nothing.
        // "Finished", not "any progress": a wheel sitting at level 3 of a damage type is precisely
        // the case the stone exists for, and only the finished thing is already bought.
        if (fed.isAdapted(concept) || fed.level(concept) >= PlayerAdaption.MAX_LEVEL) {
            refuse(player, "adaptionwheel.msg.stone_already");
            return;
        }
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
        // Grants into the fed wheel and writes it straight back onto that stack: the wheel is in
        // this menu, not in a Curios slot, so nothing else would save it, and the player's own
        // attachment is the wrong store because it belongs to the wheel they took off.
        grant(player, fed, wheel, concept);
        // fedData() re-read on a different stack, so drop the memoised one: the stack it was read
        // from is the same instance, which would otherwise make it look unchanged when it is not.
        fedStack = null;

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
    public void acceptSync(List<String> pool, int selected, int itemCost) {
        this.candidates = pool;
        this.selectedIndex = selected;
        this.itemCost = itemCost;
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

    /**
     * How many items the current trade consumes.
     *
     * <p>Computed on the server and <em>sent</em>, not recomputed on the client. That is not an
     * optimisation: the altar's price asks {@link ru.adaptionwheel.server.AltarOfferings} which mobs
     * drop the offering, and that index is read out of the server's resources, so a client asked the
     * same question answers "nothing drops this" — and a button that is never affordable is a button
     * that is never pressed. Reading it live from {@link #itemPrice} on the client returned 0
     * instead, so {@link #canAfford} was permanently false and the whole exchange was
     * unreachable.</p>
     */
    public int itemCost() {
        return itemCost;
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