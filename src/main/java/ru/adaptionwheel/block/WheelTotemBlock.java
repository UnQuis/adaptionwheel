package ru.adaptionwheel.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

/**
 * Wheel Totem: makes the analyses around it finish sooner.
 *
 * <p>Every analysis in the mod is a wait, and the waits are long on purpose. A totem does not make
 * them shorter for everyone — it makes them shorter for someone who is already wearing the wheel
 * and standing next to it, which is a reason to bring one to a fight rather than a reason to camp
 * a room with it. It accelerates the analyses that are already running rather than granting
 * anything, so a player with nothing to analyse gains nothing here.</p>
 */
public class WheelTotemBlock extends Block {

    /** Radius in which running analyses are accelerated. */
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
