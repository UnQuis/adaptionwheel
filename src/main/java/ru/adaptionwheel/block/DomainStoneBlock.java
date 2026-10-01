package ru.adaptionwheel.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import ru.adaptionwheel.config.AdaptionConfig;
import ru.adaptionwheel.menu.DomainStoneMenu;

/**
 * Domain Stone: an item in, an adaptation out.
 *
 * <p>It used to hand an adaptation over outright, on a cooldown, picked at random. That was the one
 * place in the mod where progress cost nothing, and it made every other adaptation — each one paid
 * for in suffering and time — optional. Now it is an exchange, and the item is the price. See
 * {@link ru.adaptionwheel.server.DomainExchange} for what each item buys and why the wheel's tier
 * does not filter it.</p>
 *
 * <p>The block itself is now almost nothing: open a menu. That it is still a plain {@code Block}
 * with no block entity is deliberate and load-bearing — the position is handed to the client as
 * menu-open data instead, so a chest full of stones costs the server nothing to keep.</p>
 */
public class DomainStoneBlock extends Block {

    public DomainStoneBlock() {
        super(ModBlocks.base("domain_stone", MapColor.COLOR_BLACK, 6.0F, 12.0F)
                .lightLevel(state -> 4));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, net.minecraft.world.phys.BlockHitResult hit) {
        if (!AdaptionConfig.DOMAIN_STONE_ENABLED.get()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide() || !(player instanceof ServerPlayer server)) {
            return InteractionResult.PASS;
        }
        // Not "wearing the wheel". The wheel now lives in this menu's own slot, which is the point
        // of the sketch: the stone is where you bring it, not somewhere you have to be wearing it.
        // 26.3 dropped the openMenu(provider, BlockPos) convenience that IPlayerExtension added on
        // 1.21.1, so the position is written into the extra data by hand -- which is what that
        // convenience did anyway.
        server.openMenu(new Provider(pos), buf -> buf.writeBlockPos(pos));
        return InteractionResult.CONSUME;
    }

    /**
     * Builds the menu on the server and ships the position to the client.
     *
     * <p>Writing the position is not optional. With empty extra data the server sends a plain
     * open-screen packet, the client factory is handed an empty buffer, and reading a
     * {@code BlockPos} off it throws — so a provider that "forgot" this would fail at the moment a
     * player opened the block rather than at boot.</p>
     */
    private record Provider(BlockPos pos) implements MenuProvider {

        @Override
        public Component getDisplayName() {
            return Component.translatable("container.adaptionwheel.domain_stone");
        }

        @Override
        public AbstractContainerMenu createMenu(int windowId, Inventory inventory, Player player) {
            return new DomainStoneMenu(windowId, inventory, pos);
        }
    }
}