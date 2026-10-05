package ru.adaptionwheel.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import ru.adaptionwheel.block.ModBlocks;
import ru.adaptionwheel.category.Concepts;
import ru.adaptionwheel.data.PlayerAdaption;
import ru.adaptionwheel.server.AdaptionEvents;
import ru.adaptionwheel.server.AltarOfferings;
import ru.adaptionwheel.server.DomainExchange;

import java.util.ArrayList;
import java.util.List;

public class ResonanceAltarMenu extends TradeMenu {

    public ResonanceAltarMenu(int windowId, Inventory playerInv, BlockPos pos) {
        super(windowId, playerInv, pos, ModMenus.RESONANCE_ALTAR_TRADE.get());
    }

    public ResonanceAltarMenu(int windowId, Inventory playerInv, FriendlyByteBuf extra) {
        super(windowId, playerInv, extra, ModMenus.RESONANCE_ALTAR_TRADE.get());
    }

    @Override
    protected boolean isTradeBlock(BlockState state) {
        return state.is(ModBlocks.RESONANCE_ALTAR.get());
    }

    @Override
    public String titleKey() {
        return "container.adaptionwheel.resonance_altar";
    }

    @Override
    protected List<String> candidatesFor(ServerPlayer player, PlayerAdaption fed, ItemStack offering) {
        List<String> pool = new ArrayList<>();

        DomainExchange.Recipe recipe = DomainExchange.recipeFor(offering);
        if (recipe != null) {
            pool.addAll(DomainExchange.candidates(fed, recipe));
        }

        for (String mob : AltarOfferings.mobsFor(offering, serverOf(player))) {
            addIfUnfinished(pool, fed, Concepts.offense(mob));
            addIfUnfinished(pool, fed, Concepts.drop(mob));
        }
        return List.copyOf(pool);
    }

    private static net.minecraft.server.MinecraftServer serverOf(ServerPlayer player) {
        return player.level() instanceof net.minecraft.server.level.ServerLevel level
                ? level.getServer() : null;
    }

    private static void addIfUnfinished(List<String> pool, PlayerAdaption fed, String concept) {
        if (!DomainExchange.isFinished(fed, concept)) {
            pool.add(concept);
        }
    }

    @Override
    protected int baseItemPrice(ServerPlayer player, ItemStack offering) {
        DomainExchange.Recipe recipe = DomainExchange.recipeFor(offering);
        if (recipe != null) {
            return recipe.itemsPerTrade();
        }
        return AltarOfferings.mobsFor(offering, serverOf(player)).isEmpty() ? 0 : 1;
    }

    @Override
    protected void grant(ServerPlayer player, PlayerAdaption fed, ItemStack wheel, String concept,
                         int targetLevel) {
        AdaptionEvents.grantToWheel(player, fed, wheel, concept, targetLevel);
    }
}
