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

public final class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, AdaptionWheel.MODID);

    public static final DeferredRegister<Item> BLOCK_ITEMS =
            DeferredRegister.create(Registries.ITEM, AdaptionWheel.MODID);

    public static final DeferredHolder<Block, AdaptationBrazierBlock> ADAPTATION_BRAZIER =
            BLOCKS.register("adaptation_brazier", () -> new AdaptationBrazierBlock());

    public static final DeferredHolder<Block, WheelTotemBlock> WHEEL_TOTEM =
            BLOCKS.register("wheel_totem", () -> new WheelTotemBlock());

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

    static BlockBehaviour.Properties base(MapColor colour, float strength, float resistance) {
        return BlockBehaviour.Properties.of()
                .mapColor(colour)
                .strength(strength)
                .sound(SoundType.STONE)
                .requiresCorrectToolForDrops()
                .noOcclusion();
    }
}
