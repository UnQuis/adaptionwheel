package ru.adaptionwheel.test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
        // Stone tier owns the whole stone family plus every ore a stone pickaxe gets.
        assertTier(helper, 1, Blocks.STONE, Blocks.DEEPSLATE, Blocks.COBBLESTONE, Blocks.SANDSTONE,
                Blocks.QUARTZ_BLOCK, Blocks.IRON_ORE, Blocks.COPPER_ORE, Blocks.COAL_ORE,
                Blocks.DEEPSLATE_IRON_ORE, Blocks.LAPIS_ORE, Blocks.IRON_BLOCK, Blocks.COPPER_BLOCK);
        // Copper tier is the Nether band (vanilla has no copper tool band of its own).
        assertTier(helper, 2, Blocks.NETHERRACK, Blocks.BASALT, Blocks.BLACKSTONE, Blocks.GLOWSTONE,
                Blocks.MAGMA_BLOCK, Blocks.CRIMSON_NYLIUM, Blocks.SOUL_SOIL, Blocks.BONE_BLOCK);
        // Iron tier owns what needs an iron pickaxe, Diamond tier what needs a diamond one.
        // Note vanilla leaves a few blocks untiered because any pickaxe suffices — redstone
        // blocks, coal ore, most metal blocks — so those are deliberately NOT asserted here.
        assertTier(helper, 3, Blocks.REDSTONE_ORE, Blocks.GOLD_ORE, Blocks.DEEPSLATE_REDSTONE_ORE,
                Blocks.DEEPSLATE_GOLD_ORE, Blocks.DIAMOND_ORE, Blocks.EMERALD_ORE, Blocks.GOLD_BLOCK,
                Blocks.RAW_GOLD_BLOCK);
        // Vanilla gates ancient debris and netherite blocks behind a DIAMOND pickaxe, so the
        // final band is what no pickaxe can harvest at all.
        assertTier(helper, 4, Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN, Blocks.ANCIENT_DEBRIS,
                Blocks.NETHERITE_BLOCK, Blocks.RESPAWN_ANCHOR, Blocks.LODESTONE);
        assertTier(helper, 5, Blocks.DRAGON_EGG, Blocks.SPAWNER);
        helper.succeed();
    }

    /**
     * A tier must mine exactly as fast as the vanilla tool it stands in for. Where that tool is
     * absent from the registry the tier is documented to inherit the one below it instead, so
     * that is what gets checked — the ladder must never develop a hole.
     */
    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void tierSpeedMatchesItsVanillaTool(GameTestHelper helper) {
        BlockState reference = Blocks.STONE.defaultBlockState();
        float previous = 0f;
        for (int tier = 0; tier < FistTiers.TIER_COUNT; tier++) {
            Item tool = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(FistTiers.toolId(tier)))
                    .orElse(null);
            float actual = FistTiers.vanillaMiningSpeed(tier);
            if (tool != null) {
                // The real per-tool speed lives in the matching Tool.Rule, so it has to be read
                // through Item.getDestroySpeed. DataComponents.TOOL.defaultMiningSpeed is the
                // fallback for uncovered blocks and reads 1.0 for every vanilla tool.
                float toolSpeed = tool.getDestroySpeed(tool.getDefaultInstance(), reference);
                helper.assertTrue(toolSpeed == actual,
                        "tier " + tier + " should mine at " + FistTiers.toolId(tier) + "'s speed ("
                                + toolSpeed + ") but reports " + actual);
            } else {
                helper.assertTrue(actual == previous,
                        "tier " + tier + " has no " + FistTiers.toolId(tier)
                                + ", so it must inherit the speed below (" + previous + ") but reports " + actual);
            }
            previous = actual;
        }
        helper.succeed();
    }

    /** The ladder must never go backwards, whatever the tools turn out to be. */
    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void tierSpeedNeverDecreases(GameTestHelper helper) {
        float previous = 0f;
        for (int tier = 0; tier < FistTiers.TIER_COUNT; tier++) {
            float speed = FistTiers.vanillaMiningSpeed(tier);
            helper.assertTrue(speed > 0f, "tier " + tier + " must have a usable speed");
            helper.assertTrue(speed >= previous,
                    "tier " + tier + " (" + speed + ") must not be slower than tier " + (tier - 1)
                            + " (" + previous + ")");
            previous = speed;
        }
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
                Blocks.DIRT.defaultBlockState(),            // 0 wood
                Blocks.IRON_ORE.defaultBlockState(),       // 1 stone   (needs a stone pickaxe)
                Blocks.NETHERRACK.defaultBlockState(),      // 2 copper  (the Nether band)
                Blocks.REDSTONE_ORE.defaultBlockState(),   // 3 iron    (needs an iron pickaxe)
                Blocks.OBSIDIAN.defaultBlockState(),        // 4 diamond (needs a diamond pickaxe)
                Blocks.DRAGON_EGG.defaultBlockState(),     // 5 netherite (no pickaxe at all)
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

    /**
     * The player-facing number, not just the speed value: how many ticks a Stone Fist needs to
     * break one stone block must equal what an actual stone pickaxe needs.
     *
     * <p>This is the assertion that would have caught the real bug. The speed value was already
     * correct on the server, but the client never granted the harvest check, so it divided by
     * 100 instead of 30 and the player spent 3.3x longer holding the button than the server
     * thought. Comparing speeds alone cannot see that; comparing durations can.</p>
     */
    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void stoneFistBreaksStoneAsFastAsAStonePickaxe(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        BlockState stone = Blocks.STONE.defaultBlockState();
        helper.getLevel().setBlockAndUpdate(pos, stone);
        float hardness = stone.getDestroySpeed(helper.getLevel(), pos);
        helper.assertTrue(hardness > 0f, "stone must have a destroy speed");

        ItemStack pickaxe = new ItemStack(Items.STONE_PICKAXE);
        int withPickaxe = ticksToBreak(Items.STONE_PICKAXE.getDestroySpeed(pickaxe, stone), hardness);
        int withFist = ticksToBreak(FistTiers.vanillaMiningSpeed(1), hardness);

        helper.assertTrue(withFist == withPickaxe,
                "a Stone Fist should break stone in the same " + withPickaxe
                        + " ticks as a stone pickaxe, but takes " + withFist);
        helper.succeed();
    }

    /** Same check for the tier that matters most, where the old speed really did feel like a joke. */
    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void netheriteFistMatchesANetheritePickaxe(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        BlockState obsidian = Blocks.OBSIDIAN.defaultBlockState();
        helper.getLevel().setBlockAndUpdate(pos, obsidian);
        float hardness = obsidian.getDestroySpeed(helper.getLevel(), pos);

        ItemStack pickaxe = new ItemStack(Items.NETHERITE_PICKAXE);
        int withPickaxe = ticksToBreak(Items.NETHERITE_PICKAXE.getDestroySpeed(pickaxe, obsidian), hardness);
        int withFist = ticksToBreak(FistTiers.vanillaMiningSpeed(FistTiers.TIER_COUNT - 1), hardness);
        helper.assertTrue(withFist == withPickaxe,
                "a Netherite Fist should break obsidian as fast as a netherite pickaxe ("
                        + withPickaxe + " vs " + withFist + " ticks)");

        // And the fist must never be slower than a bare hand.
        int bareHand = ticksToBreak(1.0f, hardness);
        helper.assertTrue(withFist < bareHand, "the fist must beat a bare hand (" + withFist + " vs " + bareHand + ")");
        helper.succeed();
    }

    /** Ticks to break a block, reproducing vanilla's progress formula for a correct tool. */
    private static int ticksToBreak(float speed, float hardness) {
        float progressPerTick = speed / hardness / 30f; // 30 = correct-tool divisor from the harvest check
        if (progressPerTick <= 0f) {
            return Integer.MAX_VALUE;
        }
        return (int) Math.ceil(1.0f / progressPerTick);
    }

    /**
     * Pin the actual ladder to vanilla's numbers. The tier speed is looked up rather than
     * hardcoded, so this is the test that notices if the lookup silently starts returning
     * fallbacks again — which is exactly what happened once, when reading
     * {@code DataComponents.TOOL.defaultMiningSpeed} instead of {@code Item.getDestroySpeed}
     * quietly reduced every tier to bare-hand speed while the "speed equals tool" test still
     * passed, because it was comparing against the same wrong field.
     */
    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void tierSpeedIsTheVanillaLadder(GameTestHelper helper) {
        // Copper has no pickaxe in this registry, so it inherits stone's speed.
        float[] expected = {2.0f, 4.0f, 4.0f, 6.0f, 8.0f, 9.0f};
        for (int tier = 0; tier < FistTiers.TIER_COUNT; tier++) {
            helper.assertTrue(FistTiers.vanillaMiningSpeed(tier) == expected[tier],
                    "tier " + tier + " (" + FistTiers.suffix(tier) + ") should mine at " + expected[tier]
                            + "x but reports " + FistTiers.vanillaMiningSpeed(tier));
        }
        helper.succeed();
    }

    // ================= tier reach =================

    /**
     * The reach rule, pinned as data. This is the logic the client and the server both call, and
     * the bug it caused was a silent disagreement between two hand-written copies of it — the
     * client kept answering "Wood" after the server had already unlocked Stone, and since the
     * client accumulates break progress, every block took twice as long as it should.
     */
    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void reachOpensTheNextTierTheMomentThePreviousOneIsMaxed(GameTestHelper helper) {
        int max = ru.adaptionwheel.data.PlayerAdaption.MAX_LEVEL;
        int[] levels = new int[FistTiers.TIER_COUNT];
        java.util.function.IntUnaryOperator levelOf = t -> levels[t];

        helper.assertTrue(FistTiers.reachTier(levelOf) == 0, "a fresh fist reaches Wood");

        levels[0] = 1;
        helper.assertTrue(FistTiers.reachTier(levelOf) == 0, "Wood 1 is still Wood");
        levels[0] = max - 1;
        helper.assertTrue(FistTiers.reachTier(levelOf) == 0, "Wood 7 is still Wood");

        // The whole point: Stone must be reachable at Wood 8 even though Fist_Stone is still 0.
        levels[0] = max;
        helper.assertTrue(FistTiers.reachTier(levelOf) == 1,
                "maxing Wood must open Stone immediately, not after levelling Stone first");

        levels[1] = 1;
        helper.assertTrue(FistTiers.reachTier(levelOf) == 1, "Stone 1 stays Stone");
        levels[1] = max;
        helper.assertTrue(FistTiers.reachTier(levelOf) == 2, "maxing Stone opens Copper");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void reachHonoursTiersGrantedOutOfOrder(GameTestHelper helper) {
        int[] levels = new int[FistTiers.TIER_COUNT];
        java.util.function.IntUnaryOperator levelOf = t -> levels[t];
        levels[4] = 3; // /adaptionwheel grant Fist_Diamond 3, with nothing before it
        helper.assertTrue(FistTiers.reachTier(levelOf) == 4,
                "a directly granted tier must count, otherwise the command is useless for testing");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void reachStopsAtTheLastTier(GameTestHelper helper) {
        int max = ru.adaptionwheel.data.PlayerAdaption.MAX_LEVEL;
        int[] levels = new int[FistTiers.TIER_COUNT];
        java.util.Arrays.fill(levels, max);
        helper.assertTrue(FistTiers.reachTier(t -> levels[t]) == FistTiers.TIER_COUNT - 1,
                "everything maxed must clamp to the last tier, never past it");
        helper.succeed();
    }

    // ================= what counts as a bare hand =================

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void anEmptyHandAlwaysUsesTheFist(GameTestHelper helper) {
        helper.assertTrue(FistTiers.usableWith(ItemStack.EMPTY, Blocks.STONE.defaultBlockState()),
                "an empty hand is the fist");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void nonToolItemsStillUseTheFist(GameTestHelper helper) {
        // Holding a block, food or a random item must not disable the fist - only real tools
        // and weapons do.
        for (Item item : new Item[]{
                Blocks.STONE.asItem(), Blocks.DIRT.asItem(), Items.APPLE, Items.BREAD,
                Items.STICK, Items.FLINT, Items.COBBLESTONE.asItem(), Items.OAK_LOG.asItem()}) {
            helper.assertTrue(FistTiers.usableWith(new ItemStack(item), Blocks.STONE.defaultBlockState()),
                    item + " is not a tool or weapon, the fist should still work");
        }
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void toolsAndWeaponsTakeTheFistOutOfPlay(GameTestHelper helper) {
        BlockState stone = Blocks.STONE.defaultBlockState();
        for (Item item : new Item[]{
                Items.DIAMOND_PICKAXE, Items.IRON_PICKAXE, Items.STONE_PICKAXE,
                Items.DIAMOND_SWORD, Items.IRON_SWORD, Items.STONE_SWORD,
                Items.IRON_AXE, Items.IRON_SHOVEL, Items.IRON_HOE, Items.SHEARS}) {
            helper.assertTrue(!FistTiers.usableWith(new ItemStack(item), stone),
                    item + " is a tool or weapon and should suppress the fist");
        }
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void aToolOnlyCountsWhenItOutminesAFist(GameTestHelper helper) {
        // A hoe cannot mine stone, so its mining speed on that block is a bare hand's — but the
        // TOOL component still takes the fist out of play, because the fist is about tools in
        // general rather than this one block.
        helper.assertTrue(!FistTiers.usableWith(new ItemStack(Items.IRON_HOE), Blocks.STONE.defaultBlockState()),
                "a hoe still counts as a tool even on a block it cannot mine");
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
