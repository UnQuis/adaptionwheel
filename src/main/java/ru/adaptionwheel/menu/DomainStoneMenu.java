package ru.adaptionwheel.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import ru.adaptionwheel.block.ModBlocks;
import ru.adaptionwheel.data.PlayerAdaption;
import ru.adaptionwheel.server.AdaptionEvents;
import ru.adaptionwheel.server.DomainExchange;

import java.util.List;

/**
 * The Domain Stone: an item narrows the pool, and the stone sells what it opens.
 *
 * <p>All of the mechanics are in {@link TradeMenu}; the only thing this class knows is where to ask
 * what an item opens.</p>
 */
public class DomainStoneMenu extends TradeMenu {

    public DomainStoneMenu(int windowId, Inventory playerInv, BlockPos pos) {
        super(windowId, playerInv, pos, ModMenus.DOMAIN_STONE.get());
    }

    public DomainStoneMenu(int windowId, Inventory playerInv, FriendlyByteBuf extra) {
        super(windowId, playerInv, extra, ModMenus.DOMAIN_STONE.get());
    }

    @Override
    protected boolean isTradeBlock(BlockState state) {
        return state.is(ModBlocks.DOMAIN_STONE.get());
    }

    @Override
    protected List<String> candidatesFor(ServerPlayer player, PlayerAdaption fed, ItemStack offering) {
        DomainExchange.Recipe recipe = DomainExchange.recipeFor(offering);
        if (recipe == null) {
            return List.of();
        }
        // `fed`, not the player's attachment: the tier that orders the list is the fed wheel's, so
        // the wheel in the slot and the list on screen describe the same wheel. The list itself does
        // not depend on what the wheel has — see TradeMenu.candidatesFor.
        return DomainExchange.candidates(
                ru.adaptionwheel.category.WheelTier.forCount(fed.getAdaptCount()), recipe);
    }

    @Override
    protected int itemPrice(ServerPlayer player, ItemStack offering) {
        DomainExchange.Recipe recipe = DomainExchange.recipeFor(offering);
        return recipe == null ? 0 : recipe.itemsPerTrade();
    }

    @Override
    protected void grant(ServerPlayer player, PlayerAdaption fed, ItemStack wheel, String concept) {
        // Granted whole: the exchange sells the adaptation, not a step of it, and the price ladder
        // is what says how big a thing that is.
        AdaptionEvents.grantToWheel(player, fed, wheel, concept, PlayerAdaption.MAX_LEVEL);
    }
}