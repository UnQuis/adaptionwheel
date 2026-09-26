package ru.adaptionwheel.test;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import ru.adaptionwheel.adapt.AdaptationDomain;
import ru.adaptionwheel.adapt.AdaptationDefinition;
import ru.adaptionwheel.adapt.AdaptationRegistry;
import ru.adaptionwheel.category.Concepts;
import ru.adaptionwheel.category.FistTiers;
import ru.adaptionwheel.config.AdaptionConfig;

/**
 * Regression tests for Fist Mastery's data-driven parts: the six material tags, the harvest
 * gate built on them, the per-tier cost curve, and the registry wiring.
 *
 * <p>These deliberately avoid creating a {@code ServerPlayer}. NeoForge's
 * {@code GameTestHelper#makeMockServerPlayerInLevel()} logs the mock player in, which makes
 * Curios fire its {@code curios:sync_data} payload at a client that does not exist and throw
 * {@code UnsupportedOperationException: Payload curios:sync_data may not be sent to the client}
 * before the test body ever runs. The player-dependent half of the fist (unlock, levelling,
 * instabreak stance) therefore still needs an in-game test; see DEVELOPMENT_PLAN.md.</p>
 *
 * <p>The tag contents are the part most easily broken by accident — a block id that does not
 * exist in this Minecraft version, or a block listed under two tiers, silently disables part
 * of the progression — so they are pinned here.</p>
 */
@GameTestHolder(ru.adaptionwheel.AdaptionWheel.MODID)
@PrefixGameTestTemplate(false)
public final class FistGameTests {

    private FistGameTests() {
    }

    // ================= material tags =================

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void everyTierTagCoversItsOwnMaterial(GameTestHelper helper) {
        assertTier(helper, 0, Blocks.DIRT, Blocks.SAND, Blocks.OAK_PLANKS, Blocks.OAK_LOG, Blocks.WHITE_WOOL);
        assertTier(helper, 1, Blocks.STONE, Blocks.DEEPSLATE, Blocks.COBBLESTONE, Blocks.SANDSTONE, Blocks.QUARTZ_BLOCK);
        assertTier(helper, 2, Blocks.IRON_ORE, Blocks.COPPER_ORE, Blocks.COAL_ORE, Blocks.IRON_BLOCK, Blocks.LAPIS_ORE);
        assertTier(helper, 3, Blocks.REDSTONE_ORE, Blocks.GOLD_ORE);
        assertTier(helper, 4, Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN, Blocks.DIAMOND_ORE, Blocks.DIAMOND_BLOCK);
        assertTier(helper, 5, Blocks.ANCIENT_DEBRIS, Blocks.NETHERITE_BLOCK, Blocks.BASALT, Blocks.BLACKSTONE);
        helper.succeed();
    }

    /** A block must resolve to exactly one tier — the lowest — or the ladder breaks. */
    private static void assertTier(GameTestHelper helper, int tier, Block... blocks) {
        for (Block block : blocks) {
            BlockState state = block.defaultBlockState();
            helper.assertTrue(FistTiers.tierOf(state) == tier,
                    blockName(block) + " should be fist tier " + tier
                            + " (" + FistTiers.suffix(tier) + ") but resolved to " + FistTiers.tierOf(state));
        }
    }

    private static String blockName(Block block) {
        return BuiltInRegistries.BLOCK.getKey(block).toString();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void blocksOutsideEveryTierAreAlwaysHarvestable(GameTestHelper helper) {
        // An oak sign belongs to no tier, so vanilla already lets a bare hand take it and the
        // fist must not get in the way.
        BlockState sign = Blocks.OAK_SIGN.defaultBlockState();
        helper.assertTrue(FistTiers.tierOf(sign) < 0, "oak sign should be in no fist tier");
        for (int tier = 0; tier < FistTiers.TIER_COUNT; tier++) {
            helper.assertTrue(FistTiers.canHarvest(sign, tier), "untiered blocks stay harvestable at tier " + tier);
        }
        helper.succeed();
    }

    // ================= harvest gate =================

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void harvestGateIsCumulativeAndStrict(GameTestHelper helper) {
        BlockState[] ladder = {
                Blocks.DIRT.defaultBlockState(),      // 0
                Blocks.STONE.defaultBlockState(),     // 1
                Blocks.IRON_ORE.defaultBlockState(),  // 2
                Blocks.REDSTONE_ORE.defaultBlockState(), // 3
                Blocks.OBSIDIAN.defaultBlockState(),  // 4
                Blocks.ANCIENT_DEBRIS.defaultBlockState(), // 5
        };
        for (int required = 0; required < ladder.length; required++) {
            for (int held = 0; held < FistTiers.TIER_COUNT; held++) {
                boolean expected = held >= required;
                helper.assertTrue(FistTiers.canHarvest(ladder[required], held) == expected,
                        "block of tier " + required + " at fist tier " + held + " should be "
                                + (expected ? "harvestable" : "out of reach"));
            }
        }
        helper.succeed();
    }

    // ================= cost curve =================

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void costGrowsWithinATier(GameTestHelper helper) {
        for (int tier = 0; tier < FistTiers.TIER_COUNT; tier++) {
            int previous = 0;
            for (int level = 1; level <= 8; level++) {
                int cost = AdaptionConfig.fistBlocksForNextLevel(tier, level);
                helper.assertTrue(cost > 0, "tier " + tier + " level " + level + " must cost something");
                helper.assertTrue(cost >= previous,
                        "tier " + tier + " level " + level + " (" + cost + ") must not be cheaper than the previous ("
                                + previous + ")");
                previous = cost;
            }
        }
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void laterTiersCostMoreThanEarlierOnes(GameTestHelper helper) {
        for (int level = 1; level <= 8; level++) {
            for (int tier = 1; tier < FistTiers.TIER_COUNT; tier++) {
                int lower = AdaptionConfig.fistBlocksForNextLevel(tier - 1, level);
                int upper = AdaptionConfig.fistBlocksForNextLevel(tier, level);
                helper.assertTrue(upper > lower,
                        "tier " + tier + " at level " + level + " (" + upper + ") must cost more than tier "
                                + (tier - 1) + " (" + lower + ")");
            }
        }
        helper.succeed();
    }

    // ================= registry wiring =================

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void fistConceptsAreRegisteredAsLeveledMiningConcepts(GameTestHelper helper) {
        for (int tier = 0; tier < FistTiers.TIER_COUNT; tier++) {
            String concept = FistTiers.concept(tier);
            AdaptationDefinition def = AdaptationRegistry.get(concept);
            helper.assertTrue(def != null, concept + " must be registered");
            helper.assertTrue(def.leveled(), concept + " must be a leveled concept");
            helper.assertTrue(def.domain() == AdaptationDomain.MINING, concept + " must live in the mining domain");
            helper.assertTrue(def.maxLevel() == ru.adaptionwheel.data.PlayerAdaption.MAX_LEVEL,
                    concept + " must run to the shared max level");
            helper.assertTrue(Concepts.isLevelBased(concept) && !Concepts.isOneTime(concept),
                    concept + " must resolve as leveled through Concepts");
        }
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void theMutationIsAOneTimeSpecial(GameTestHelper helper) {
        AdaptationDefinition def = AdaptationRegistry.get(Concepts.MUTATION_FIST);
        helper.assertTrue(def != null, "the fist mutation must be registered");
        helper.assertTrue(!def.leveled(), "the fist mutation is one-time");
        helper.assertTrue(def.domain() == AdaptationDomain.SPECIAL, "mutations live in the special domain");
        helper.assertTrue(Concepts.isOneTime(Concepts.MUTATION_FIST), "must resolve as one-time");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void everyTierHasItsOwnDisplayColor(GameTestHelper helper) {
        for (int tier = 0; tier < FistTiers.TIER_COUNT; tier++) {
            helper.assertTrue(Concepts.color(FistTiers.concept(tier)) == FistTiers.color(tier),
                    "concept color must match the tier color for " + FistTiers.concept(tier));
        }
        helper.succeed();
    }
}
