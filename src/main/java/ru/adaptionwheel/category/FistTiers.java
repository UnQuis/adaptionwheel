package ru.adaptionwheel.category;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
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
}
