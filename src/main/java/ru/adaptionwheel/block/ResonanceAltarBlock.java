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
import net.minecraft.world.phys.BlockHitResult;
import ru.adaptionwheel.config.AdaptionConfig;
import ru.adaptionwheel.menu.ResonanceAltarMenu;

public class ResonanceAltarBlock extends Block {

    public static final double RANGE = 8.0D;

    public ResonanceAltarBlock() {
        super(ModBlocks.base("resonance_altar", MapColor.COLOR_CYAN, 4.5F, 9.0F).lightLevel(state -> 7));
    }

    public static boolean isAltar(BlockState state) {
        return state.is(ModBlocks.RESONANCE_ALTAR.get());
    }

    public static boolean isAltar(Level level, BlockPos pos) {
        return isAltar(level.getBlockState(pos));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {

        if (!AdaptionConfig.ALTAR_TRADE_ENABLED.get()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide() || !(player instanceof ServerPlayer server)) {
            return InteractionResult.PASS;
        }

        server.openMenu(new Provider(pos), buf -> buf.writeBlockPos(pos));
        return InteractionResult.CONSUME;
    }

    private record Provider(BlockPos pos) implements MenuProvider {

        @Override
        public Component getDisplayName() {
            return Component.translatable("container.adaptionwheel.resonance_altar");
        }

        @Override
        public AbstractContainerMenu createMenu(int windowId, Inventory inventory, Player player) {
            return new ResonanceAltarMenu(windowId, inventory, pos);
        }
    }
}
