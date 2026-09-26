package ru.adaptionwheel.category;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.IntUnaryOperator;

/**
 * Layout of the "Fist Mastery" progression unlocked by {@link Concepts#MUTATION_FIST}.
 *
 * <p>Five materials, each with {@link ru.adaptionwheel.data.PlayerAdaption#MAX_LEVEL} levels.
 * A fist at tier <em>n</em> can harvest every block belonging to tiers {@code 0..n}, so
 * higher tiers are strictly more capable; what makes them <em>harder</em> is that a level is
 * only earned by breaking blocks of that tier's <em>own</em> class, which are rarer and
 * slower to break by hand than the previous tier's.</p>
 *
 * <p>Vanilla's own tool ladder is Wood, Stone, Copper, Iron, Diamond, Netherite, but copper has
 * no tool band of its own — a copper pickaxe is a stone-band tool — so a literal six-tier ladder
 * had a tier that granted nothing. Copper was folded into Iron: that tier now trains on the union
 * of the Nether band and everything needing an iron pickaxe, which is one honest eight-level
 * climb instead of two thin ones.</p>
 *
 * <p>The material classes are plain block tags ({@code data/adaptionwheel/tags/block/}),
 * so modpacks can retarget or extend every tier without touching code.</p>
 */
@SuppressWarnings("unchecked") // the TAGS array is written with a constant length in the static block
public final class FistTiers {

    public static final int TIER_COUNT = 5;

    /** Wood, Stone, Iron, Diamond, Netherite — index order is the unlock order. */
    public static final String[] CONCEPTS = {
            "Fist_Wood", "Fist_Stone", "Fist_Iron", "Fist_Diamond", "Fist_Netherite"
    };

    private static final TagKey<Block>[] TAGS = new TagKey[TIER_COUNT];

    /**
     * The vanilla implement each tier stands in for, in the same order. Looked up by id rather
     * than through {@code Items} constants on purpose: those constants are not all present in
     * every mapping set, and a missing one would otherwise cost the whole tier its speed.
     */
    private static final String[] TIER_TOOL_IDS = {
            "minecraft:wooden_pickaxe", "minecraft:stone_pickaxe",
            "minecraft:iron_pickaxe", "minecraft:diamond_pickaxe", "minecraft:netherite_pickaxe"
    };

    private static final float[] SPEED_CACHE = new float[TIER_COUNT];

    /**
     * Block the per-tier speed is measured against when no specific block is at hand. Stone is
     * in {@code #minecraft:mineable/pickaxe}, so every pickaxe tier resolves a real rule on it.
     */
    private static final BlockState REFERENCE_BLOCK =
            net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();

    /** One HUD/chat color per tier, roughly following the material itself. */
    public static final int[] COLORS = {
            0xFFA9784A, // Wood     - bark brown
            0xFF9A9A9A, // Stone    - grey
            0xFFDCDCDC, // Iron     - pale steel
            0xFF4FE3D8, // Diamond  - cyan
            0xFF6B4A78  // Netherite- dark violet
    };

    static {
        for (int i = 0; i < TIER_COUNT; i++) {
            TAGS[i] = TagKey.create(Registries.BLOCK,
                    ResourceLocation.fromNamespaceAndPath(ru.adaptionwheel.AdaptionWheel.MODID,
                            "fist_" + suffix(i)));
        }
    }

    private FistTiers() {
    }

    /** Short material name for a tier, matching its tag suffix and config-table order. */
    public static String suffix(int tier) {
        return switch (tier) {
            case 0 -> "wood";
            case 1 -> "stone";
            case 2 -> "iron";
            case 3 -> "diamond";
            case 4 -> "netherite";
            default -> throw new IllegalArgumentException("bad fist tier " + tier);
        };
    }

    /** The vanilla tool id a tier mirrors; exposed for diagnostics and tests. */
    public static String toolId(int tier) {
        return TIER_TOOL_IDS[tier];
    }

    public static String concept(int tier) {
        return CONCEPTS[tier];
    }

    public static TagKey<Block> tag(int tier) {
        return TAGS[tier];
    }

    public static int color(int tier) {
        return COLORS[tier];
    }

    /**
     * Bare-hand mining speed for a tier, taken from the vanilla tool that tier stands in for.
     * Wooden/stone/iron/diamond/netherite pickaxes mine at 2/4/6/8/9 in 1.21.1, but reading them
     * beats copying those numbers: a pack that retunes tool speed moves the fist with it.
     *
     * <p>Measured with {@link Item#getDestroySpeed} against a reference block rather than by
     * reading {@code DataComponents.TOOL} directly. A tool's real speed lives in the matching
     * {@code Tool.Rule}, and {@code defaultMiningSpeed} is only the fallback for blocks no rule
     * covers — reading that field returns 1.0 for every vanilla tool, which silently reduced the
     * whole ladder to bare-hand speed.</p>
     *
     * <p>If a tool is missing (modded away, odd mapping), the tier inherits the speed of the one
     * below it rather than dropping to a bare hand, so the ladder stays monotone.</p>
     */
    public static float vanillaMiningSpeed(int tier) {
        return vanillaMiningSpeed(tier, REFERENCE_BLOCK);
    }

    /**
     * Speed of this tier's tool on one specific block. Preferred at runtime: a tool may carry
     * per-block speeds, and the fist should match the tool it stands in for on the block actually
     * being mined.
     */
    public static float vanillaMiningSpeed(int tier, BlockState target) {
        if (tier < 0 || tier >= TIER_COUNT) {
            return 1.0f;
        }
        if (target == REFERENCE_BLOCK && SPEED_CACHE[tier] > 0f) {
            return SPEED_CACHE[tier];
        }
        float speed = 0f;
        Item tool = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(TIER_TOOL_IDS[tier]))
                .orElse(null);
        if (tool != null) {
            speed = tool.getDestroySpeed(tool.getDefaultInstance(), target);
        }
        if (speed <= 1.0f) {
            // A tool that does not out-mine a bare hand on this block tells us nothing; fall
            // back to the tier below so the ladder still climbs.
            speed = tier == 0 ? 1.0f : vanillaMiningSpeed(tier - 1, target);
        }
        if (target == REFERENCE_BLOCK) {
            SPEED_CACHE[tier] = speed;
        }
        return speed;
    }

    /**
     * Index of the lowest tier whose material class contains this block, or {@code -1}
     * when the block belongs to no tier at all (then it is always harvestable by hand,
     * because vanilla already lets a bare hand take such blocks).
     */
    public static int tierOf(BlockState state) {
        for (int i = 0; i < TIER_COUNT; i++) {
            if (state.is(TAGS[i])) {
                return i;
            }
        }
        return -1;
    }

    /** True when a fist at {@code tier} is strong enough to harvest this block. */
    public static boolean canHarvest(BlockState state, int tier) {
        int required = tierOf(state);
        return required < 0 || required <= tier;
    }

    /**
     * The tier a fist with these levels can reach.
     *
     * <p>Shared by the server ({@code FistMastery}) and the client mirror
     * ({@code ClientAdaption}) on purpose. They used to be separate copies and drifted: the
     * server switched to "the previous tier being maxed opens this one" while the client kept
     * "the highest tier with a level", so after maxing Wood the two sides disagreed about whether
     * the Stone fist existed — and since the client is what accumulates break progress, the
     * player mined at wooden speed for a whole tier.</p>
     *
     * <p>One implementation, so they cannot drift again. {@code levelOf} maps a tier index to its
     * current level.</p>
     */
    public static int reachTier(IntUnaryOperator levelOf) {
        int maxLevel = ru.adaptionwheel.data.PlayerAdaption.MAX_LEVEL;
        int progression = 0;
        while (progression < TIER_COUNT - 1 && levelOf.applyAsInt(progression) >= maxLevel) {
            progression++;
        }
        int granted = 0;
        for (int i = TIER_COUNT - 1; i >= 0; i--) {
            if (levelOf.applyAsInt(i) > 0) {
                granted = i;
                break;
            }
        }
        return Math.max(progression, granted);
    }

    // ================= what counts as a bare hand =================

    /**
     * Whether the fist still works while {@code stack} is held. An empty hand obviously does;
     * so does anything that is not a tool or a weapon — a block, food, a modded trinket. Only a
     * real digging implement or a weapon takes the fist out of play, because those are exactly
     * what the fist is meant to replace.
     *
     * <p>Three independent checks so modded items are covered too:</p>
     * <ol>
     *   <li>the {@link DataComponents#TOOL} component, which in 1.21.1 is what every vanilla and
     *       modded implement declares (pickaxes, axes, shovels, hoes, shears alike);</li>
     *   <li>any positive main-hand attack-damage modifier, which is how a weapon declares itself
     *       without carrying a tool component;</li>
     *   <li>a mining speed above a bare hand's 1.0 for the block actually being looked at, as a
     *       catch-all for items that dig well without declaring anything.</li>
     * </ol>
     */
    public static boolean usableWith(ItemStack stack, BlockState target) {
        if (stack.isEmpty()) {
            return true;
        }
        if (stack.get(DataComponents.TOOL) != null) {
            return false;
        }
        if (dealsExtraAttackDamage(stack)) {
            return false;
        }
        return stack.getItem().getDestroySpeed(stack, target) <= 1.0f;
    }

    private static boolean dealsExtraAttackDamage(ItemStack stack) {
        boolean[] weapon = {false};
        stack.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (attribute.is(Attributes.ATTACK_DAMAGE) && modifier.amount() > 0.0) {
                weapon[0] = true;
            }
        });
        return weapon[0];
    }
}
