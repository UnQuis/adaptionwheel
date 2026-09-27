package ru.adaptionwheel.category;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Tool;
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

    /** The vanilla material each tier stands in for, in the same order. */
    private static final String[] MATERIALS = {"wooden", "stone", "iron", "diamond", "netherite"};

    /**
     * The tool classes a fist can impersonate. Vanilla divides the mining world between them:
     * stone and ore belong to the pickaxe, logs to the axe, soil to the shovel, foliage to the
     * hoe. A fist has to pick the class that owns the block in front of the player, or it ends up
     * measuring itself against a tool that cannot mine that block at all.
     */
    private static final String[] TOOL_CLASSES = {"pickaxe", "axe", "shovel", "hoe"};

    /**
     * Drop and experience luck per tier. Only a handful of steps so the HUD can state it in one
     * number, and deliberately steep at the end: the last tier is meant to feel like a reward
     * rather than one more rung.
     */
    private static final float[] LUCK = {1.0f, 2.0f, 3.0f, 5.0f, 10.0f};

    /** Cached declared tool speed, indexed [tier][tool class]. Block-independent, so always valid. */
    private static final float[][] DECLARED_SPEED = new float[TIER_COUNT][TOOL_CLASSES.length];

    /**
     * Block the per-tier speed is measured against when no specific block is at hand. Stone is
     * in {@code #minecraft:mineable/pickaxe}, so every pickaxe tier resolves a real rule on it.
     */
    private static final BlockState REFERENCE_BLOCK =
            net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();

    /** Blocks the tier's luck applies to. Ores by default; a pack can retarget the whole thing. */
    private static final TagKey<Block> LUCK_TAG = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(ru.adaptionwheel.AdaptionWheel.MODID, "fist_luck"));

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

    /**
     * Id of the tool a tier uses for a given class. Built from the material name rather than
     * taken from {@code Items} constants: those constants are not all present in every mapping
     * set, and a missing one would cost the whole tier its speed.
     */
    public static String toolId(int tier, String toolClass) {
        return "minecraft:" + MATERIALS[tier] + "_" + toolClass;
    }

    /** The pickaxe a tier mirrors, the tool used for the generic tier speed; for tests. */
    public static String toolId(int tier) {
        return toolId(tier, "pickaxe");
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
     * A tool's declared mining speed, read from its own rules rather than from any block.
     *
     * <p>Reading it off a block is a trap, and it is the trap this class already fell into twice.
     * {@link Item#getDestroySpeed} only returns anything above 1.0 when one of the tool's rules
     * actually <em>covers</em> the block, and every vanilla tool's rules name exactly one class of
     * blocks. So a diamond pickaxe reports {@code 1.0} on dirt: in 1.21.1 dirt is shovel-only, not
     * in {@code #minecraft:mineable/pickaxe} at all. A fist that measured itself against a pickaxe
     * on the block in front of the player therefore mined soil at bare-hand speed — a diamond fist
     * taking 15 ticks on dirt when a diamond shovel takes 2.</p>
     *
     * <p>Also not {@code defaultMiningSpeed}: that field is only the fallback for blocks no rule
     * covers and reads 1.0 for every vanilla tool.</p>
     *
     * <p>The rules themselves are the tier's real number, so this is block-independent and correct
     * for every material at once.</p>
     */
    public static float declaredSpeed(Item tool) {
        Tool component = tool.getDefaultInstance().get(DataComponents.TOOL);
        if (component == null) {
            return 1.0f;
        }
        float best = 0f;
        for (Tool.Rule rule : component.rules()) {
            best = Math.max(best, rule.speed().orElse(0f));
        }
        return best > 0f ? best : component.defaultMiningSpeed();
    }

    /**
     * Bare-hand mining speed for a tier, measured against a reference block the pickaxe owns.
     * Wooden/stone/iron/diamond/netherite mine at 2/4/6/8/9 in 1.21.1, read from the tools rather
     * than copied, so a pack that retunes tool speed moves the fist with it.
     */
    public static float vanillaMiningSpeed(int tier) {
        return vanillaMiningSpeed(tier, REFERENCE_BLOCK);
    }

    /**
     * Speed of this tier's fist on one specific block, taken from whichever vanilla tool class
     * owns that block: the shovel's number for soil, the axe's for logs, the pickaxe's for stone
     * and ore. In practice all four classes of a tier declare the same speed, but asking the tool
     * that can actually mine the block is what makes that true by construction rather than by luck.
     */
    public static float vanillaMiningSpeed(int tier, BlockState target) {
        if (tier < 0 || tier >= TIER_COUNT) {
            return 1.0f;
        }
        float best = 0f;
        for (int c = 0; c < TOOL_CLASSES.length; c++) {
            Item tool = toolOf(tier, c);
            if (tool == null) {
                continue;
            }
            // Above 1.0 exactly when a rule covers the block, i.e. this is the right prototype.
            if (tool.getDestroySpeed(tool.getDefaultInstance(), target) <= 1.0f) {
                continue;
            }
            best = Math.max(best, declaredSpeedOf(tier, c));
        }
        if (best > 1.0f) {
            return best;
        }
        // No tool in the set claims this block (bedrock, a portal frame). The tier's own pickaxe
        // number is the honest answer: the harvest gate is what stops the fist taking the block,
        // and that gate already passed. Falling to the tier below would be arbitrary.
        float own = declaredSpeedOf(tier, 0);
        if (own > 1.0f) {
            return own;
        }
        // Only if the tool itself is missing does the ladder step down, so it stays monotone.
        return tier == 0 ? 1.0f : vanillaMiningSpeed(tier - 1, target);
    }

    private static Item toolOf(int tier, int classIndex) {
        return BuiltInRegistries.ITEM
                .getOptional(ResourceLocation.parse(toolId(tier, TOOL_CLASSES[classIndex])))
                .orElse(null);
    }

    private static float declaredSpeedOf(int tier, int classIndex) {
        float cached = DECLARED_SPEED[tier][classIndex];
        if (cached > 0f) {
            return cached;
        }
        Item tool = toolOf(tier, classIndex);
        float speed = tool == null ? 1.0f : declaredSpeed(tool);
        DECLARED_SPEED[tier][classIndex] = speed;
        return speed;
    }

    /**
     * Drop and experience luck this tier grants, {@code 1} (none) up to {@code 10}. Read on both
     * sides: the server applies it, and the HUD states it.
     */
    public static int luckMultiplier(int tier) {
        if (tier < 0 || tier >= TIER_COUNT) {
            return 1;
        }
        return (int) LUCK[tier];
    }

    /** Blocks the tier's luck applies to. Ores by default. */
    public static TagKey<Block> luckTag() {
        return LUCK_TAG;
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
