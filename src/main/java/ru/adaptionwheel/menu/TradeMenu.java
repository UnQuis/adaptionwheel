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
import ru.adaptionwheel.data.PlayerAdaption;
import ru.adaptionwheel.item.ModItems;
import ru.adaptionwheel.network.TradeSyncPayload;
import ru.adaptionwheel.server.AdaptionEvents;
import ru.adaptionwheel.server.DomainExchange;

import java.util.ArrayList;
import java.util.List;

public abstract class TradeMenu extends AbstractContainerMenu {

    public static final int OFFER_SLOT = 0;
    public static final int WHEEL_SLOT = 1;
    private static final int INPUT_SIZE = 2;

    private static final int INV_START = INPUT_SIZE;
    private static final int INV_END = INV_START + 27;
    private static final int HOTBAR_END = INV_END + 9;

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

    private List<TradePrice> prices = List.of();

    private boolean offeringAccepted;

    private PlayerAdaption fed;
    private ItemStack fedStack;

    private final SimpleContainer input = new SimpleContainer(INPUT_SIZE) {
        @Override
        public void setChanged() {
            super.setChanged();

            recompute();
        }
    };

    protected TradeMenu(int windowId, Inventory playerInv, BlockPos pos,
                        net.minecraft.world.inventory.MenuType<?> type) {
        super(type, windowId);
        this.pos = pos.immutable();
        this.playerInventory = playerInv;

        this.serverSide = playerInv.player instanceof ServerPlayer;
        addSlot(new Slot(input, OFFER_SLOT, OFFER_X, OFFER_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {

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

    protected TradeMenu(int windowId, Inventory playerInv, net.minecraft.network.FriendlyByteBuf extra,
                        net.minecraft.world.inventory.MenuType<?> type) {
        this(windowId, playerInv, extra.readBlockPos(), type);
    }

    protected abstract List<String> candidatesFor(ServerPlayer player, PlayerAdaption data,
                                                  ItemStack offering);

    protected abstract int baseItemPrice(ServerPlayer player, ItemStack offering);

    protected abstract void grant(ServerPlayer player, PlayerAdaption fed, ItemStack wheel,
                                  String concept, int targetLevel);

    public record TradePrice(int items, int xp) {
    }

    public String titleKey() {
        return "container.adaptionwheel.resonance_altar";
    }

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

        return isTradeBlock(player.level().getBlockState(pos))
                && player.distanceToSqr(Vec3.atCenterOf(pos)) <= 64.0D;
    }

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

            clearContainer(player, input);
        }
    }

    private ServerPlayer serverPlayer() {
        return serverSide && playerInventory.player instanceof ServerPlayer server ? server : null;
    }

    private PlayerAdaption fedData() {
        ItemStack stack = input.getItem(WHEEL_SLOT);
        if (fed == null || fedStack != stack) {
            fed = AdaptionEvents.readFrom(stack);
            fedStack = stack;
        }
        return fed;
    }

    public void recompute() {
        ServerPlayer player = serverPlayer();
        if (player == null) {
            return;
        }

        ItemStack offering = input.getItem(OFFER_SLOT);
        if (isWheel(input.getItem(WHEEL_SLOT))) {
            candidates = candidatesFor(player, fedData(), offering);
        } else {
            candidates = List.of();
        }

        int base = isWheel(input.getItem(WHEEL_SLOT)) ? baseItemPrice(player, offering) : 0;
        offeringAccepted = base > 0;

        PlayerAdaption fed = fedData();
        List<TradePrice> built = new ArrayList<>(candidates.size());
        for (String concept : candidates) {
            built.add(priceFor(fed, concept, base));
        }
        prices = List.copyOf(built);

        if (selectedIndex < 0 || selectedIndex >= candidates.size()) {
            selectedIndex = candidates.isEmpty() ? -1 : 0;
        }
        sync(player);
    }

    private static TradePrice priceFor(PlayerAdaption fed, String concept, int baseItems) {
        int held = Concepts.isLevelBased(concept) ? fed.level(concept) : 0;
        return new TradePrice(DomainExchange.itemsForLevel(baseItems, held),
                DomainExchange.priceForLevel(concept, held));
    }

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

    public void exchange() {
        ServerPlayer player = serverPlayer();
        if (player == null) {
            return;
        }
        ItemStack offering = input.getItem(OFFER_SLOT);
        ItemStack wheel = input.getItem(WHEEL_SLOT);
        PlayerAdaption fed = fedData();

        if (!isWheel(wheel)) {
            refuse(player, "adaptionwheel.msg.trade_no_wheel");
            return;
        }
        if (!offeringAccepted) {

            refuse(player, "adaptionwheel.msg.trade_not_offering");
            return;
        }
        if (candidates.isEmpty()) {

            refuse(player, "adaptionwheel.msg.trade_nothing");
            return;
        }

        String concept = selectedIndex >= 0 && selectedIndex < candidates.size()
                ? candidates.get(selectedIndex) : null;
        if (concept == null) {
            refuse(player, "adaptionwheel.msg.trade_stale");
            return;
        }

        List<String> pool = candidatesFor(player, fed, offering);
        if (!pool.contains(concept)) {
            refuse(player, "adaptionwheel.msg.trade_stale");
            return;
        }

        TradePrice price = priceFor(fed, concept, baseItemPrice(player, offering));
        if (price.items() <= 0 || offering.getCount() < price.items()) {
            refuse(player, "adaptionwheel.msg.trade_too_few");
            return;
        }

        if (player.experienceLevel < price.xp()) {
            player.sendSystemMessage(Component.translatable("adaptionwheel.msg.trade_no_experience",
                    price.xp(), player.experienceLevel));
            sync(player);
            return;
        }

        offering.shrink(price.items());

        player.giveExperienceLevels(-price.xp());

        int held = Concepts.isLevelBased(concept) ? fed.level(concept) : 0;
        grant(player, fed, wheel, concept, held + 1);

        fedStack = null;

        Level level = player.level();
        level.playSound(null, pos, ru.adaptionwheel.sound.ModSounds.REF.get(),
                net.minecraft.sounds.SoundSource.BLOCKS, 0.9f, 0.7f);

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

    public void acceptSync(List<String> pool, List<TradePrice> prices, int selected,
                           boolean offeringAccepted) {
        this.candidates = pool;
        this.prices = prices;
        this.selectedIndex = selected;
        this.offeringAccepted = offeringAccepted;
    }

    public BlockPos stonePos() {
        return pos;
    }

    public List<String> candidates() {
        return candidates;
    }

    public List<TradePrice> prices() {
        return prices;
    }

    public int selectedIndex() {
        return selectedIndex;
    }

    public ItemStack offering() {
        return input.getItem(OFFER_SLOT);
    }

    public boolean hasOffering() {
        return !offering().isEmpty();
    }

    public boolean hasWheel() {
        return isWheel(input.getItem(WHEEL_SLOT));
    }

    public int itemCost() {
        return priceOfSelected().items();
    }

    public int xpPrice() {
        return priceOfSelected().xp();
    }

    public boolean isOfferingAccepted() {
        return offeringAccepted;
    }

    private TradePrice priceOfSelected() {
        if (selectedIndex < 0 || selectedIndex >= prices.size()) {
            return new TradePrice(0, 0);
        }
        return prices.get(selectedIndex);
    }

    public boolean canAfford(int playerLevel) {
        String concept = selectedConcept();
        int items = itemCost();
        return concept != null && items > 0 && offering().getCount() >= items
                && playerLevel >= xpPrice();
    }
}
