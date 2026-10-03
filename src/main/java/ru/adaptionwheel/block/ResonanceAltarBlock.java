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

/**
 * Resonance Altar: the mod's one extra Resonance rung beside it, and the mod's one trade.
 *
 * <p>Two jobs on one block, and they do not collide because they answer different questions. The
 * aura is passive and about who is standing near you; the trade is active and about what you are
 * willing to spend. Keeping both is also why the altar is the right host for the trade: it is
 * already the mod's "this is a rite, not a machine" block, and a ritual that only fires a buff
 * would have been the weak one.</p>
 *
 * <p><b>The trade is the Domain Stone's as well.</b> The two were separate blocks with identical
 * mechanics — two slots, a wheel, a list, a price — differing only in where they looked for what
 * an offering opens. Right-clicking this block now asks both and shows the union, so the stone is
 * gone: one block to place, one recipe, one advancement, one set of rules. What the item buys is
 * {@link ru.adaptionwheel.menu.ResonanceAltarMenu}'s answer; the arithmetic is
 * {@link ru.adaptionwheel.menu.TradeMenu}'s.</p>
 *
 * <p>The aura lives in {@code RitualAuras}, a one-second pass over wheel-wearing players, and is
 * switched by its own config key — so a world that wants the trade without the buff, or a buff
 * without the trade, can have either.</p>
 *
 * <p>No block entity, same as every other block here: the position travels as menu-open data, which
 * 26.3 requires to be written by hand because it dropped the {@code openMenu(provider, BlockPos)}
 * convenience.</p>
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

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        // Only the trade is gated here. The aura is checked where it is applied, in RitualAuras,
        // so the two switches are independent: a world can have the altar as a buff and not as a
        // shop, or the other way round.
        if (!AdaptionConfig.ALTAR_TRADE_ENABLED.get()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide() || !(player instanceof ServerPlayer server)) {
            return InteractionResult.PASS;
        }
        // Not "wearing the wheel": the wheel lives in this menu's own slot — the altar is where you
        // bring it, not somewhere you have to have it equipped.
        server.openMenu(new Provider(pos), buf -> buf.writeBlockPos(pos));
        return InteractionResult.CONSUME;
    }

    /** Builds the menu on the server and ships the position to the client. */
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
