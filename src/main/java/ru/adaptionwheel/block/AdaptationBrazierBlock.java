package ru.adaptionwheel.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

/**
 * Adaptation Brazier: a light that heals a wheel-wearer standing beside it.
 *
 * <p>Light level 9 rather than a dim glow, because in a cave this is the thing that makes a room
 * worth standing in, and a brazier that emits almost nothing is a lit-up block, not a brazier.</p>
 *
 * <p>No behaviour of its own — the healing is driven from {@code RitualAuras}, which is where all
 * four blocks' effects live so that one pass over the world's wheel-wearers covers them all.</p>
 */
public class AdaptationBrazierBlock extends Block {

    /** How far the healing reaches, in blocks. Shared with the aura pass, not a config value. */
    public static final double RANGE = 5.0D;

    public AdaptationBrazierBlock() {
        super(ModBlocks.base("adaptation_brazier", MapColor.COLOR_PURPLE, 3.0F, 6.0F).lightLevel(state -> 9));
    }

    /** Whether this position is a brazier, for the aura pass's block scan. */
    public static boolean isBrazier(BlockState state) {
        return state.is(ModBlocks.ADAPTATION_BRAZIER.get());
    }

    /** Convenience for callers holding a position rather than a state. */
    public static boolean isBrazier(Level level, BlockPos pos) {
        return isBrazier(level.getBlockState(pos));
    }
}
