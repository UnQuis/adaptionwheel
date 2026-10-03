package ru.adaptionwheel.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

public class WheelTotemBlock extends Block {

    public static final double RANGE = 6.0D;

    public WheelTotemBlock() {
        super(ModBlocks.base("wheel_totem", MapColor.COLOR_RED, 4.0F, 8.0F).lightLevel(state -> 5));
    }

    public static boolean isTotem(BlockState state) {
        return state.is(ModBlocks.WHEEL_TOTEM.get());
    }

    public static boolean isTotem(Level level, BlockPos pos) {
        return isTotem(level.getBlockState(pos));
    }
}
