package ru.adaptionwheel.block;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.item.ModItems;

/**
 * The mod's first blocks.
 *
 * <p>Until now the wheel had no presence in the world at all: five items, two projectiles, and
 * nothing to find, place or walk past. These three are the whole of that, and two of them are
 * deliberately auras rather than containers — every one of them gives something to a player standing
 * near it, and none of them asks for anything back. There is no upkeep, no fuel and no currency,
 * because the mod's premise is that the wheel only ever gives.</p>
 *
 * <p>The fourth of the original four, the Domain Stone, is <b>deleted rather than merged in spirit
 * only</b>: it traded adaptations for an item, and its Resonance Altar sibling traded the same
 * adaptations for the same item, so keeping both meant two copies of one trade rule — the exact trap
 * this mod has walked into three times already. The altar kept the aura and took the trade, behind
 * its own {@code altarTradeEnabled} switch so its two jobs stay separately controllable. No block,
 * item, menu, screen, recipe, advancement, blockstate, model, loot table, texture, pickaxe-tag entry
 * or lang key survives it.</p>
 *
 * <p>All three are plain {@link Block}s with no block entity. Their effects are driven from one
 * server-side pass in {@code RitualAuras} that walks the (few) wheel-wearing players rather than
 * ticking the blocks, so a player standing beside three braziers costs three block lookups a
 * second instead of three tickers running forever.</p>
 */
public final class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, AdaptionWheel.MODID);

    /**
     * The block items, in their own register.
     *
     * <p>Registered next to the blocks rather than in {@code ModItems} so that each block and the
     * thing you pick up in the world are declared on adjacent lines. That is the whole reason: a
     * block with no item is unplaceable from the inventory, and the mistake is invisible until
     * someone notices the block exists but cannot be obtained.</p>
     */
    public static final DeferredRegister<Item> BLOCK_ITEMS =
            DeferredRegister.create(Registries.ITEM, AdaptionWheel.MODID);

    /** Emits light and heals a wheel-wearer standing near it. */
    public static final DeferredHolder<Block, AdaptationBrazierBlock> ADAPTATION_BRAZIER =
            BLOCKS.register("adaptation_brazier", () -> new AdaptationBrazierBlock());

    /** Speeds up every analysis running for a wheel-wearer standing near it. */
    public static final DeferredHolder<Block, WheelTotemBlock> WHEEL_TOTEM =
            BLOCKS.register("wheel_totem", () -> new WheelTotemBlock());

    /** Raises the Resonance rung of a wheel-wearer standing near it. */
    public static final DeferredHolder<Block, ResonanceAltarBlock> RESONANCE_ALTAR =
            BLOCKS.register("resonance_altar", () -> new ResonanceAltarBlock());

    public static final DeferredHolder<Item, BlockItem> ADAPTATION_BRAZIER_ITEM =
            BLOCK_ITEMS.register("adaptation_brazier", () -> new BlockItem(
                    ADAPTATION_BRAZIER.get(), new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> WHEEL_TOTEM_ITEM =
            BLOCK_ITEMS.register("wheel_totem", () -> new BlockItem(
                    WHEEL_TOTEM.get(), new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> RESONANCE_ALTAR_ITEM =
            BLOCK_ITEMS.register("resonance_altar", () -> new BlockItem(
                    RESONANCE_ALTAR.get(), new Item.Properties()));

    private ModBlocks() {
    }

    // ------------------------------------------------------------------ shared shapes

    /**
     * The shared look: dark, hard, faintly lit.
     *
     * <p>All three read as one set on sight. The mod had no world presence to establish, so the
     * blocks are what establish it, and unrelated-looking props scattered around a world look like
     * unrelated mods.</p>
     */
    static BlockBehaviour.Properties base(MapColor colour, float strength, float resistance) {
        return BlockBehaviour.Properties.of()
                .mapColor(colour)
                .strength(strength)
                .sound(SoundType.STONE)
                .requiresCorrectToolForDrops()
                .noOcclusion();
    }
}
