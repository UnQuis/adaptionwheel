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
import net.minecraft.world.phys.Vec3;
import ru.adaptionwheel.category.Concepts;
import ru.adaptionwheel.category.WheelTier;
import ru.adaptionwheel.data.PlayerAdaption;
import ru.adaptionwheel.item.ModItems;
import ru.adaptionwheel.network.TradeSyncPayload;
import ru.adaptionwheel.server.AdaptionEvents;
import ru.adaptionwheel.server.DomainExchange;

import java.util.ArrayList;
import java.util.List;

/**
 * The trade itself: two slots, a wheel, a list of adaptations, a price, and a server that decides.
 *
 * <p>This was written when two blocks traded, and the mechanics of the two were identical — the item
 * narrowed the pool, the wheel had to be present, the pool was rebuilt on every change, the selection
 * was an index, and the price was the item plus experience. Only the answer to "which adaptations does
 * this item open" differed, so that is the one thing left abstract and everything else is written
 * once, here.</p>
 *
 * <p>There is now one block rather than two, and one subclass rather than two — but the shape is
 * unchanged, because the shape was never the problem: the two menus differed by a dozen lines each
 * and shared a hundred. Deleting the stone deleted one subclass, not the shared half.</p>
 *
 * <p>Copying the shared half instead would be the same trap the rest of this mod has walked into
 * three times: two copies of a rule, one of them fixed later, and nothing that notices.</p>
 *
 * @see ru.adaptionwheel.server.DomainExchange for the price list
 * @see ru.adaptionwheel.server.AltarOfferings for the item-to-mob index
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
     * One price per candidate, in the same order, as of the last {@link #recompute()}.
     *
     * <p>Server-computed and synced; see {@link TradePrice}. The two lists are built and sent
     * together and are always the same length, because a price with no row to belong to is a price
     * the player cannot act on.</p>
     */
    private List<TradePrice> prices = List.of();
    /**
     * Whether the altar takes the offering <em>at all</em>, which is a different question from
     * whether anything is left to buy with it.
     *
     * <p>This is what lets an empty list say which of the two things happened. A wrong item and a
     * wheel that already knows everything this item opens are both an empty list and they want
     * opposite answers from the player, so the screen is told which one it is looking at rather
     * than being left to guess from an empty box.</p>
     */
    private boolean offeringAccepted;

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
        // 26.3 grew `addStandardInventorySlots` on AbstractContainerMenu with the layout this helper
        // produces by hand (main grid at `top`, hotbar at top + 58, a 4 px separator), so on 26.3 that
        // call replaces this one. The constants above are the same either way, and a hand-rolled copy
        // of vanilla's own layout is one fewer thing to re-check when the method does arrive.
        addPlayerInventory(playerInv, INV_X, INV_Y);
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
     * <p>The pool is <b>what this wheel has not finished</b>, and the fed wheel's state is what makes
     * that true: an adaptation bought here and one earned by standing in the thing until it stopped
     * mattering both live in the fed stack's {@code wheel_data}, both are subtracted before the list
     * is drawn, and the list is a shopping list for the wheel in the slot rather than a catalogue of
     * the item. That is also why the wheel slot is not optional — see {@link #recompute()}.</p>
     *
     * <p>Which is exactly what makes a stale index dangerous, and what {@link #exchange()} is careful
     * about: the pool shrinks as the player buys, so an index that was valid a moment ago names a
     * different adaptation now. A row is therefore a <em>name</em> in the pool and an index only
     * inside this class.</p>
     */
    protected abstract List<String> candidatesFor(ServerPlayer player, PlayerAdaption data,
                                                  ItemStack offering);

    /**
     * What one item is worth at the altar, before any level is bought: the recipe's own price, or
     * one for a mob's drop.
     *
     * <p><b>This is the FIRST level's price, not the price of a purchase.</b> Every level after it
     * is a multiple of this, capped, and the multiplication is {@code DomainExchange}'s job rather
     * than each block's — see {@link DomainExchange#itemsForLevel}. A base of zero means the altar
     * does not take this item at all, and it is also how the screen tells "wrong item" from
     * "nothing left to learn".</p>
     */
    protected abstract int baseItemPrice(ServerPlayer player, ItemStack offering);

    /**
     * Grants ONE level of the chosen adaptation into the wheel being fed, and writes it onto it.
     *
     * <p>One level, not the whole adaptation: an adaptation that goes to level eight is bought eight
     * times, at a price that grows each time, and that is the point of the block. One-time
     * adaptations ignore the number — they have no levels — and a {@code Drop_NPC_} level is still
     * <em>derived</em> from a kill count, so buying it tops the kills up rather than assigning a
     * level. That difference lives in one place, {@code AdaptionEvents.grantToWheel}, rather than in
     * each block, so both buy the same thing.</p>
     *
     * @param fed         the fed wheel's own state, from {@link #fedData()}; never the player's attachment
     * @param wheel       the stack to write back onto
     * @param targetLevel the level being bought, i.e. the level held plus one
     */
    protected abstract void grant(ServerPlayer player, PlayerAdaption fed, ItemStack wheel,
                                  String concept, int targetLevel);

    /**
     * One row's price, as the server computed it.
     *
     * <p>Sent rather than recomputed on the client: the price depends on the level the fed wheel
     * already holds, and the client does not have the fed wheel's state at all. A screen that
     * guessed would be a screen that can be wrong, and this one shows a number next to a button.</p>
     *
     * @param items how many of the offering this purchase consumes
     * @param xp    how many whole levels of the player's own experience
     */
    public record TradePrice(int items, int xp) {
    }

    public String titleKey() {
        return "container.adaptionwheel.resonance_altar";
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
        // eye. 26.3 spells the same check `player.isWithinBlockInteractionRange(pos, 4.0)`, which
        // arrived with that version; here the squared distance is written out, because the point of
        // stillValid is to stop a player walking away with an open menu, not to mirror one call.
        return isTradeBlock(player.level().getBlockState(pos))
                && player.distanceToSqr(Vec3.atCenterOf(pos)) <= 64.0D;
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
        // The list is the item's openings MINUS what the wheel in the slot has already finished, and
        // the purchase is written onto that same stack — so with the slot empty there is nothing to
        // subtract from and nothing to write to. The block has no answer that does not depend on the
        // wheel in it, which is what makes it a ritual rather than a shop.
        ItemStack offering = input.getItem(OFFER_SLOT);
        if (isWheel(input.getItem(WHEEL_SLOT))) {
            candidates = candidatesFor(player, fedData(), offering);
        } else {
            candidates = List.of();
        }
        // The base price doubles as the answer to "does this altar take this item": the subclasses
        // return zero for anything they do not take, and growth must never make a zero into a price.
        int base = isWheel(input.getItem(WHEEL_SLOT)) ? baseItemPrice(player, offering) : 0;
        offeringAccepted = base > 0;
        // Priced here rather than in the screen, because the price of a level depends on how many
        // levels the fed wheel already holds -- a number the client does not have.
        PlayerAdaption fed = fedData();
        List<TradePrice> built = new ArrayList<>(candidates.size());
        for (String concept : candidates) {
            built.add(priceFor(fed, concept, base));
        }
        prices = List.copyOf(built);
        // The pool shrank under the selection — which it does on every purchase, because the thing
        // bought is no longer unfinished — or there is no pool at all. Clear rather than clamp: a
        // clamped index would silently point at a neighbouring adaptation nobody asked for.
        if (selectedIndex < 0 || selectedIndex >= candidates.size()) {
            selectedIndex = candidates.isEmpty() ? -1 : 0;
        }
        sync(player);
    }

    /**
     * What one more level of {@code concept} costs this wheel.
     *
     * <p>The level the wheel already holds is what the price grows from, and it is the same number
     * the list is filtered by — so a row cannot be priced as though it were further along than it
     * is, or nearer than it is.</p>
     */
    private static TradePrice priceFor(PlayerAdaption fed, String concept, int baseItems) {
        int held = Concepts.isLevelBased(concept) ? fed.level(concept) : 0;
        return new TradePrice(DomainExchange.itemsForLevel(baseItems, held),
                DomainExchange.priceForLevel(concept, held));
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
     * is rebuilt here rather than reused — because the pool <em>is</em> a function of live state, and it
     * moves under the player. The wheel may have finished the chosen adaptation by some other route
     * since the screen was drawn; the offering may have run out; the item may have been swapped for
     * one that opens nothing.</p>
     *
     * <p><b>The chosen row is resolved by name, not by index.</b> This is the whole reason the pool is
     * rebuilt here instead of being read straight out of {@link #candidates}: the list subtracts what
     * the wheel has finished, so it shrinks as the player buys, and a row index that was 4 when the
     * screen was drawn can be a different adaptation by the time the packet lands. Taking
     * {@code candidates.get(selectedIndex)} — what the client actually saw — and then requiring
     * that name to still be on offer turns that into a refusal instead of a purchase of the wrong
     * thing, which is the only outcome here that cannot be undone by the player noticing.</p>
     *
     * <p>Nothing is charged until every check has passed, so a refusal costs the player nothing.</p>
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
            refuse(player, "adaptionwheel.msg.trade_no_wheel");
            return;
        }
        if (!offeringAccepted) {
            // Not an offering at all — the altar does not take this item. A different sentence from
            // the one below, because the player's next move is different: put something else in.
            refuse(player, "adaptionwheel.msg.trade_not_offering");
            return;
        }
        if (candidates.isEmpty()) {
            // Genuinely nothing on offer, as opposed to a row that has gone: the item opens only
            // what this wheel has already finished. The two deserve different words, because only one
            // of them is the player's fault.
            refuse(player, "adaptionwheel.msg.trade_nothing");
            return;
        }
        // The row the player clicked, by name: an index into the pool the screen drew. Resolved
        // BEFORE the live pool is built, because this is what the client was looking at.
        String concept = selectedIndex >= 0 && selectedIndex < candidates.size()
                ? candidates.get(selectedIndex) : null;
        if (concept == null) {
            refuse(player, "adaptionwheel.msg.trade_stale");
            return;
        }
        // The live pool, and the one thing that has to agree: the row must still be on offer. It
        // will not be if the wheel finished it while the screen was open, or if the offering
        // changed — and then the index would silently name a neighbour, which is why this compares
        // names rather than positions.
        List<String> pool = candidatesFor(player, fed, offering);
        if (!pool.contains(concept)) {
            refuse(player, "adaptionwheel.msg.trade_stale");
            return;
        }
        // The price, computed live rather than read out of what the screen drew: it depends on the
        // level this wheel already holds, and the wheel may have gained one by another route since
        // the row was drawn. Charging the drawn price would be a way to buy a level cheaply by
        // making the list stale first.
        TradePrice price = priceFor(fed, concept, baseItemPrice(player, offering));
        if (price.items() <= 0 || offering.getCount() < price.items()) {
            refuse(player, "adaptionwheel.msg.trade_too_few");
            return;
        }
        // Experience is the second price. Checked before either is taken, so a refusal costs the
        // player nothing.
        if (player.experienceLevel < price.xp()) {
            player.sendSystemMessage(Component.translatable("adaptionwheel.msg.trade_no_experience",
                    price.xp(), player.experienceLevel));
            sync(player);
            return;
        }

        offering.shrink(price.items());
        // giveExperienceLevels takes a negative amount -- this is exactly how vanilla charges an
        // enchanting table, so the client's experience bar updates through the normal path.
        player.giveExperienceLevels(-price.xp());
        // Grants ONE level into the fed wheel and writes it straight back onto that stack: the wheel
        // is in this menu, not in a Curios slot, so nothing else would save it, and the player's own
        // attachment is the wrong store because it belongs to the wheel they took off.
        int held = Concepts.isLevelBased(concept) ? fed.level(concept) : 0;
        grant(player, fed, wheel, concept, held + 1);
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
    public void acceptSync(List<String> pool, List<TradePrice> prices, int selected,
                           boolean offeringAccepted) {
        this.candidates = pool;
        this.prices = prices;
        this.selectedIndex = selected;
        this.offeringAccepted = offeringAccepted;
    }

    // ------------------------------------------------------------------ read by both sides

    public BlockPos stonePos() {
        return pos;
    }

    public List<String> candidates() {
        return candidates;
    }

    /** One price per candidate, in the same order. */
    public List<TradePrice> prices() {
        return prices;
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
        return priceOfSelected().items();
    }

    /** What the current choice costs in whole levels of the player's own experience. */
    public int xpPrice() {
        return priceOfSelected().xp();
    }

    /** Whether the altar takes the offering at all, as opposed to having nothing left to sell. */
    public boolean isOfferingAccepted() {
        return offeringAccepted;
    }

    /**
     * The selected row's price, or nothing at all if there is no row.
     *
     * <p>Both lists are built and sent in one pass and are always the same length, so the index that
     * names a candidate names a price — but a sync from a mismatched build could still be short, and
     * an out-of-range read here would be a crash on the client's render thread rather than a wrong
     * number.</p>
     */
    private TradePrice priceOfSelected() {
        if (selectedIndex < 0 || selectedIndex >= prices.size()) {
            return new TradePrice(0, 0);
        }
        return prices.get(selectedIndex);
    }

    /** Whether both prices are covered: the items in the slot and the levels the player holds. */
    public boolean canAfford(int playerLevel) {
        String concept = selectedConcept();
        int items = itemCost();
        return concept != null && items > 0 && offering().getCount() >= items
                && playerLevel >= xpPrice();
    }
}