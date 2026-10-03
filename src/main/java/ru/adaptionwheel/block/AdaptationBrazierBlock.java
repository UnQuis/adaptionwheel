package ru.adaptionwheel.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

public class AdaptationBrazierBlock extends Block {

    public static final double RANGE = 5.0D;

    public AdaptationBrazierBlock() {
        super(ModBlocks.base(MapColor.COLOR_PURPLE, 3.0F, 6.0F).lightLevel(state -> 9));
    }

    public static boolean isBrazier(BlockState state) {
        return state.is(ModBlocks.ADAPTATION_BRAZIER.get());
    }

    public static boolean isBrazier(Level level, BlockPos pos) {
        return isBrazier(level.getBlockState(pos));
    }
}
