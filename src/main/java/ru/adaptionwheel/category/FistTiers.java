package ru.adaptionwheel.category;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Layout of the "Fist Mastery" progression unlocked by {@link Concepts#MUTATION_FIST}.
 *
 * <p>Six materials, each with {@link ru.adaptionwheel.data.PlayerAdaption#MAX_LEVEL} levels.
 * A fist at tier <em>n</em> can harvest every block belonging to tiers {@code 0..n}, so
 * higher tiers are strictly more capable; what makes them <em>harder</em> is that a level is
 * only earned by breaking blocks of that tier's <em>own</em> class, which are rarer and
 * slower to break by hand than the previous tier's.</p>
 *
 * <p>The material classes are plain block tags ({@code data/adaptionwheel/tags/block/}),
 * so modpacks can retarget or extend every tier without touching code.</p>
 */
@SuppressWarnings("unchecked") // the TAGS array is written with a constant length in the static block
public final class FistTiers {
    public static final int TIER_COUNT = 6;

    /** Wood, Stone, Copper, Iron, Diamond, Netherite — index order is the unlock order. */
    public static final String[] CONCEPTS = {
            "Fist_Wood", "Fist_Stone", "Fist_Copper", "Fist_Iron", "Fist_Diamond", "Fist_Netherite"
    };

    private static final TagKey<Block>[] TAGS = new TagKey[TIER_COUNT];

    /** One HUD/chat color per tier, roughly following the material itself. */
    public static final int[] COLORS = {
            0xFFA9784A, // Wood   - bark brown
            0xFF9A9A9A, // Stone  - grey
            0xFFE07A4B, // Copper - oxidized orange
            0xFFDCDCDC, // Iron   - pale steel
            0xFF4FE3D8, // Diamond- cyan
            0xFF6B4A78  // Netherite - dark violet
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
            case 2 -> "copper";
            case 3 -> "iron";
            case 4 -> "diamond";
            case 5 -> "netherite";
            default -> throw new IllegalArgumentException("bad fist tier " + tier);
        };
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
