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
 * nothing to find, place or walk past. These three are the whole of that, and they are deliberately
 * auras rather than machines — every one of them gives something to a player standing near it, and
 * the altar additionally trades. There is no upkeep, no fuel and no currency, because the mod's
 * premise is that the wheel only ever gives.</p>
 *
 * <p><b>There were four of these, and one of them was the Domain Stone.</b> The stone and the altar
 * were the same block twice over — two slots, a wheel, a list of adaptations, an item and some
 * experience levels as the price — differing only in where they looked for what an offering item
 * opens. So the stone is gone and its price list is answered by the altar, which asks both its own
 * mob-drop index and {@link ru.adaptionwheel.server.DomainExchange}. One block to learn, one recipe,
 * one advancement.</p>
 *
 * <p>All of them are plain {@link Block}s with no block entity. Their effects are driven from one
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

    /** Raises the Resonance rung of a wheel-wearer standing near it, and is the mod's one trade. */
    public static final DeferredHolder<Block, ResonanceAltarBlock> RESONANCE_ALTAR =
            BLOCKS.register("resonance_altar", () -> new ResonanceAltarBlock());

    /**
     * The properties every block item needs on 26.3.
     *
     * <p>{@code setId} is mandatory and the failure is at the deepest possible point:
     * {@code BlockItem}'s constructor reaches {@code Item.<init>}, which asks the properties for
     * their description id, which asks for the id, which is not set yet — so the mod does not
     * load and the message is a bare "Item id not set" with no mention of the block that caused
     * it. A plain {@code new Item.Properties()} is fine for an ordinary item and not for a
     * BlockItem, which is exactly the kind of difference that costs an hour.</p>
     *
     * <p>{@code useBlockDescriptionPrefix()} is the other half, and its absence is silent. On 26.3
     * {@code Item.getDescriptionId()} is {@code final} and read out of the properties, so the name a
     * block item shows is decided here rather than by asking the block — and vanilla decides it in a
     * <em>private</em> helper, {@code Items.registerBlock}, which adds the prefix on the way past.
     * A mod that builds its own {@code BlockItem} never passes through that helper, so every block
     * item in the mod showed its raw key — {@code item.adaptionwheel.resonance_altar} in a tooltip,
     * with {@code block.adaptionwheel.resonance_altar} sitting translated and unused in the lang
     * file. One call, here, fixes all of them.</p>
     */
    private static Item.Properties itemProps(String name) {
        return new Item.Properties()
                .setId(net.minecraft.resources.ResourceKey.create(Registries.ITEM,
                        net.minecraft.resources.Identifier.fromNamespaceAndPath(
                                AdaptionWheel.MODID, name)))
                .useBlockDescriptionPrefix();
    }

    public static final DeferredHolder<Item, BlockItem> ADAPTATION_BRAZIER_ITEM =
            BLOCK_ITEMS.register("adaptation_brazier", () -> new BlockItem(
                    ADAPTATION_BRAZIER.get(), itemProps("adaptation_brazier")));
    public static final DeferredHolder<Item, BlockItem> WHEEL_TOTEM_ITEM =
            BLOCK_ITEMS.register("wheel_totem", () -> new BlockItem(
                    WHEEL_TOTEM.get(), itemProps("wheel_totem")));
    public static final DeferredHolder<Item, BlockItem> RESONANCE_ALTAR_ITEM =
            BLOCK_ITEMS.register("resonance_altar", () -> new BlockItem(
                    RESONANCE_ALTAR.get(), itemProps("resonance_altar")));

    private ModBlocks() {
    }

    // ------------------------------------------------------------------ shared shapes

    /**
     * The shared look: dark, hard, faintly lit.
     *
     * <p>All four read as one set on sight. The mod had no world presence to establish, so the
     * blocks are what establish it, and four unrelated-looking props scattered around a world look
     * like four unrelated mods.</p>
     */
    static BlockBehaviour.Properties base(String name, MapColor colour, float strength,
                                          float resistance) {
        // 26.3 resolves a block's drops from its own registry key, and does it while the block is
        // being constructed — before the registry ever gets to see it. Without setId the
        // constructor throws "NullPointerException: Block id not set" and the mod does not load.
        // That is why the key is built here: the id has to exist before the Block does, which is
        // the opposite of every other Minecraft version this mod has been ported to.
        return BlockBehaviour.Properties.of()
                .setId(net.minecraft.resources.ResourceKey.create(Registries.BLOCK,
                        net.minecraft.resources.Identifier.fromNamespaceAndPath(
                                AdaptionWheel.MODID, name)))
                .mapColor(colour)
                .strength(strength)
                .sound(SoundType.STONE)
                .requiresCorrectToolForDrops()
                .noOcclusion();
    }
}
