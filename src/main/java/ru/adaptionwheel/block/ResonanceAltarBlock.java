package ru.adaptionwheel.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

/**
 * Resonance Altar: one extra Resonance rung for a wheel-wearer standing beside it.
 *
 * <p>The counterweight to Resonance itself. That buff is built out of other players, which makes it
 * a reason to travel together and useless alone; the altar is the answer for a player playing by
 * themselves, and it also gives the mechanic something physical to build. It raises the rung rather
 * than widening the search radius, so stacking altars cannot paper over a full server.</p>
 */
public class ResonanceAltarBlock extends Block {

    /** Radius in which the altar adds a rung. */
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
}
