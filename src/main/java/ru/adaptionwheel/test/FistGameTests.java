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

@GameTestHolder(ru.adaptionwheel.AdaptionWheel.MODID)
@PrefixGameTestTemplate(false)
public final class FistGameTests {

    private FistGameTests() {
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void everyTierTagCoversItsOwnMaterial(GameTestHelper helper) {
        assertTier(helper, 0, Blocks.DIRT, Blocks.SAND, Blocks.OAK_PLANKS, Blocks.OAK_LOG, Blocks.WHITE_WOOL);

        assertTier(helper, 1, Blocks.STONE, Blocks.DEEPSLATE, Blocks.COBBLESTONE, Blocks.SANDSTONE,
                Blocks.QUARTZ_BLOCK, Blocks.IRON_ORE, Blocks.COPPER_ORE, Blocks.COAL_ORE,
                Blocks.DEEPSLATE_IRON_ORE, Blocks.LAPIS_ORE, Blocks.IRON_BLOCK, Blocks.COPPER_BLOCK);

        assertTier(helper, 2, Blocks.NETHERRACK, Blocks.BASALT, Blocks.BLACKSTONE, Blocks.GLOWSTONE,
                Blocks.MAGMA_BLOCK, Blocks.CRIMSON_NYLIUM, Blocks.SOUL_SOIL, Blocks.BONE_BLOCK,
                Blocks.REDSTONE_ORE, Blocks.GOLD_ORE, Blocks.DEEPSLATE_REDSTONE_ORE,
                Blocks.DIAMOND_ORE, Blocks.EMERALD_ORE, Blocks.GOLD_BLOCK, Blocks.RAW_GOLD_BLOCK);

        assertTier(helper, 3, Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN, Blocks.ANCIENT_DEBRIS,
                Blocks.NETHERITE_BLOCK, Blocks.RESPAWN_ANCHOR, Blocks.LODESTONE);
        assertTier(helper, 4, Blocks.DRAGON_EGG, Blocks.SPAWNER);
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void tierSpeedMatchesItsVanillaTool(GameTestHelper helper) {
        BlockState reference = Blocks.STONE.defaultBlockState();
        float previous = 0f;
        for (int tier = 0; tier < FistTiers.TIER_COUNT; tier++) {
            Item tool = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(FistTiers.toolId(tier)))
                    .orElse(null);
            float actual = FistTiers.vanillaMiningSpeed(tier);
            if (tool != null) {

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

        BlockState sign = Blocks.OAK_SIGN.defaultBlockState();
        helper.assertTrue(FistTiers.tierOf(sign) < 0, "oak sign should be in no fist tier");
        for (int tier = 0; tier < FistTiers.TIER_COUNT; tier++) {
            helper.assertTrue(FistTiers.canHarvest(sign, tier), "untiered blocks stay harvestable at tier " + tier);
        }
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void harvestGateIsCumulativeAndStrict(GameTestHelper helper) {
        BlockState[] ladder = {
                Blocks.DIRT.defaultBlockState(),
                Blocks.IRON_ORE.defaultBlockState(),
                Blocks.REDSTONE_ORE.defaultBlockState(),
                Blocks.OBSIDIAN.defaultBlockState(),
                Blocks.DRAGON_EGG.defaultBlockState(),
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

        int bareHand = ticksToBreak(1.0f, hardness);
        helper.assertTrue(withFist < bareHand, "the fist must beat a bare hand (" + withFist + " vs " + bareHand + ")");
        helper.succeed();
    }

    private static int ticksToBreak(float speed, BlockState state) {
        return ticksToBreak(speed, state.getDestroySpeed(
                net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
    }

    private static int ticksToBreak(float speed, float hardness) {
        float progressPerTick = speed / hardness / 30f;
        if (progressPerTick <= 0f) {
            return Integer.MAX_VALUE;
        }
        return (int) Math.ceil(1.0f / progressPerTick);
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void tierSpeedIsTheVanillaLadder(GameTestHelper helper) {

        float[] expected = {2.0f, 4.0f, 6.0f, 8.0f, 9.0f};
        helper.assertTrue(FistTiers.TIER_COUNT == expected.length,
                "the tier table and this expectation must stay in step");
        for (int tier = 0; tier < FistTiers.TIER_COUNT; tier++) {
            helper.assertTrue(FistTiers.vanillaMiningSpeed(tier) == expected[tier],
                    "tier " + tier + " (" + FistTiers.suffix(tier) + ") should mine at " + expected[tier]
                            + "x but reports " + FistTiers.vanillaMiningSpeed(tier));
        }
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void copperTierIsFullyFoldedIntoIron(GameTestHelper helper) {
        for (int i = 0; i < FistTiers.TIER_COUNT; i++) {
            helper.assertTrue(!FistTiers.concept(i).equals("Fist_Copper"),
                    "no tier may be called Fist_Copper any more (index " + i + ")");
        }

        for (Block block : new Block[]{
                Blocks.NETHERRACK, Blocks.BASALT, Blocks.BLACKSTONE, Blocks.GLOWSTONE,
                Blocks.MAGMA_BLOCK, Blocks.SOUL_SOIL, Blocks.CRIMSON_NYLIUM}) {
            assertTier(helper, 2, block);
        }
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void removedCopperProgressMigratesIntoIron(GameTestHelper helper) {
        int max = ru.adaptionwheel.data.PlayerAdaption.MAX_LEVEL;

        var loaded = loadLegacyWheel(java.util.Map.of("Fist_Copper", 3));
        helper.assertTrue(loaded.level("Fist_Iron") == 3,
                "3 copper levels should arrive as 3 iron levels, got " + loaded.level("Fist_Iron"));
        helper.assertTrue(loaded.level("Fist_Copper") == 0,
                "the dead concept must not linger, or it shows up as a broken row in the screen");

        loaded = loadLegacyWheel(java.util.Map.of("Fist_Copper", 2, "Fist_Iron", 5));
        helper.assertTrue(loaded.level("Fist_Iron") == 7,
                "2 copper + 5 iron should merge to 7 iron, got " + loaded.level("Fist_Iron"));

        loaded = loadLegacyWheel(java.util.Map.of("Fist_Copper", max, "Fist_Iron", max));
        helper.assertTrue(loaded.level("Fist_Iron") == max,
                "a merged level above " + max + " would break the tier ladder, got "
                        + loaded.level("Fist_Iron"));

        loaded = loadLegacyWheel(java.util.Map.of("Fist_Iron", 4));
        helper.assertTrue(loaded.level("Fist_Iron") == 4,
                "a current save must pass through unchanged, got " + loaded.level("Fist_Iron"));
        helper.succeed();
    }

    private static ru.adaptionwheel.data.PlayerAdaption loadLegacyWheel(
            java.util.Map<String, Integer> levels) {
        var wheel = new ru.adaptionwheel.data.WheelData(
                new java.util.HashMap<>(levels), java.util.List.of(), java.util.List.of(),
                new java.util.HashMap<>(), java.util.List.of(), java.util.List.of());
        var data = new ru.adaptionwheel.data.PlayerAdaption(
                new java.util.HashMap<>(), java.util.List.of(), java.util.List.of(),
                java.util.List.of(), java.util.List.of(), new java.util.HashMap<>(),
                new java.util.HashMap<>(), 0, 0, 0, false, 0f, 0f, false, 0, java.util.List.of());
        wheel.loadInto(data);
        return data;
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void tierCostHasOneEntryPerMaterial(GameTestHelper helper) {
        double[] defaults = {1.0, 1.5, 2.5, 4.0, 5.0};
        helper.assertTrue(defaults.length == FistTiers.TIER_COUNT,
                "the cost table and the tier table must stay the same length");
        for (int tier = 0; tier < FistTiers.TIER_COUNT; tier++) {
            double cost = ru.adaptionwheel.config.AdaptionConfig.fistTierCost(tier);
            helper.assertTrue(cost > 0,
                    "tier " + tier + " must have a positive cost multiplier, got " + cost);
        }

        double previous = 0;
        for (int tier = 0; tier < FistTiers.TIER_COUNT; tier++) {
            double cost = ru.adaptionwheel.config.AdaptionConfig.fistTierCost(tier);
            helper.assertTrue(cost > previous,
                    "tier " + FistTiers.suffix(tier) + " costs " + cost
                            + ", which is not harder than the tier below it (" + previous + ")");
            previous = cost;
        }
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void everyToolClassGetsItsOwnTiersSpeed(GameTestHelper helper) {
        for (int tier = 0; tier < FistTiers.TIER_COUNT; tier++) {
            float expected = FistTiers.vanillaMiningSpeed(tier);
            helper.assertTrue(expected > 1.0f,
                    "tier " + tier + " must out-mine a bare hand, got " + expected);

            for (String[] pair : new String[][]{
                    {"shovel", "dirt"}, {"shovel", "sand"}, {"axe", "oak_log"},
                    {"axe", "oak_planks"}, {"hoe", "wheat"}, {"pickaxe", "stone"},
                    {"pickaxe", "deepslate"}, {"pickaxe", "coal_ore"}}) {
                var block = BuiltInRegistries.BLOCK.getOptional(
                                ResourceLocation.parse("minecraft:" + pair[1]))
                        .orElse(null);
                helper.assertTrue(block != null, pair[1] + " must exist in this version");
                BlockState state = block.defaultBlockState();
                float actual = FistTiers.vanillaMiningSpeed(tier, state);
                helper.assertTrue(actual == expected,
                        "tier " + tier + " (" + FistTiers.suffix(tier) + ") on " + pair[1]
                                + " should copy the " + pair[0] + " at " + expected
                                + "x but reports " + actual);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void diamondFistBreaksDirtEssentiallyInstantly(GameTestHelper helper) {
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        int bareHand = ticksToBreak(1.0f, dirt);
        int wooden = ticksToBreak(FistTiers.vanillaMiningSpeed(0, dirt), dirt);
        int diamond = ticksToBreak(FistTiers.vanillaMiningSpeed(3, dirt), dirt);

        helper.assertTrue(diamond <= 2,
                "a diamond fist should take at most 2 ticks on dirt, but takes " + diamond);
        helper.assertTrue(wooden < bareHand,
                "even a wooden fist must beat a bare hand (" + wooden + " vs " + bareHand + ")");
        helper.assertTrue(diamond < wooden,
                "a higher tier must be faster on the same block (" + diamond + " vs " + wooden + ")");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void netheriteFistCutsLogsAndSoilLikeItsTools(GameTestHelper helper) {
        int top = FistTiers.TIER_COUNT - 1;
        float speed = FistTiers.vanillaMiningSpeed(top);
        for (Block block : new Block[]{Blocks.OAK_LOG, Blocks.DIRT, Blocks.SAND}) {
            BlockState state = block.defaultBlockState();
            float actual = FistTiers.vanillaMiningSpeed(top, state);
            helper.assertTrue(actual == speed,
                    "netherite fist on " + blockName(block) + " should be " + speed + "x but is " + actual);
            helper.assertTrue(ticksToBreak(actual, state) < ticksToBreak(1.0f, state),
                    blockName(block) + " must be faster than a bare hand");
        }
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void luckScalesToTenAtTheFinalTier(GameTestHelper helper) {
        int[] expected = {1, 2, 3, 5, 10};
        helper.assertTrue(FistTiers.TIER_COUNT == expected.length,
                "the tier table and this expectation must stay in step");
        int previous = 0;
        for (int tier = 0; tier < FistTiers.TIER_COUNT; tier++) {
            int luck = FistTiers.luckMultiplier(tier);
            helper.assertTrue(luck == expected[tier],
                    "tier " + tier + " (" + FistTiers.suffix(tier) + ") should have x" + expected[tier]
                            + " luck but has x" + luck);
            helper.assertTrue(luck > previous, "luck must strictly climb the ladder");
            previous = luck;
        }
        helper.assertTrue(FistTiers.luckMultiplier(-1) == 1, "no fist means no luck");
        helper.assertTrue(FistTiers.luckMultiplier(FistTiers.TIER_COUNT) == 1,
                "an out-of-range tier must not grant luck");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void luckCoversOresAndNothingElse(GameTestHelper helper) {
        for (Block block : new Block[]{Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE, Blocks.IRON_ORE,
                Blocks.DEEPSLATE_IRON_ORE, Blocks.COPPER_ORE, Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE,
                Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE, Blocks.LAPIS_ORE,
                Blocks.DEEPSLATE_LAPIS_ORE, Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE,
                Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE, Blocks.NETHER_GOLD_ORE,
                Blocks.NETHER_QUARTZ_ORE}) {
            helper.assertTrue(block.defaultBlockState().is(FistTiers.luckTag()),
                    blockName(block) + " is an ore and must be covered by fist_luck");
        }
        for (Block block : new Block[]{Blocks.STONE, Blocks.DEEPSLATE, Blocks.DIRT, Blocks.OAK_LOG,
                Blocks.SAND, Blocks.OBSIDIAN, Blocks.ANCIENT_DEBRIS, Blocks.NETHERITE_BLOCK}) {
            helper.assertTrue(!block.defaultBlockState().is(FistTiers.luckTag()),
                    blockName(block) + " is not an ore and must be left alone by luck");
        }
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void everyLuckyOreIsHarvestableAtItsOwnTier(GameTestHelper helper) {
        for (Block block : new Block[]{Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE, Blocks.IRON_ORE,
                Blocks.DEEPSLATE_IRON_ORE, Blocks.COPPER_ORE, Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE,
                Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE, Blocks.LAPIS_ORE,
                Blocks.DEEPSLATE_LAPIS_ORE, Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE,
                Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE, Blocks.NETHER_GOLD_ORE,
                Blocks.NETHER_QUARTZ_ORE}) {
            BlockState state = block.defaultBlockState();
            int required = FistTiers.tierOf(state);
            helper.assertTrue(required == -1 || required >= 0,
                    blockName(block) + " resolved to an impossible tier " + required);
            helper.assertTrue(FistTiers.canHarvest(state, Math.max(required, 0)),
                    blockName(block) + " must be harvestable by the fist that owns it");

            if (required > 0) {
                helper.assertTrue(state.is(FistTiers.tag(required)),
                        blockName(block) + " is owned by tier " + required + " but is not in that tag");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void theDeepFamilyTrainsTheDiamondTier(GameTestHelper helper) {
        int diamond = 3;
        for (Block block : new Block[]{Blocks.DEEPSLATE, Blocks.COBBLED_DEEPSLATE,
                Blocks.POLISHED_DEEPSLATE, Blocks.DEEPSLATE_BRICKS, Blocks.CRACKED_DEEPSLATE_BRICKS,
                Blocks.DEEPSLATE_TILES, Blocks.CRACKED_DEEPSLATE_TILES, Blocks.CHISELED_DEEPSLATE,
                Blocks.INFESTED_DEEPSLATE, Blocks.TUFF, Blocks.CHISELED_TUFF, Blocks.POLISHED_TUFF,
                Blocks.CALCITE, Blocks.DRIPSTONE_BLOCK, Blocks.POINTED_DRIPSTONE}) {
            BlockState state = block.defaultBlockState();
            helper.assertTrue(state.is(FistTiers.tag(diamond)),
                    blockName(block) + " must be listed in the diamond tag or the diamond tier cannot be levelled");
            helper.assertTrue(FistTiers.tierOf(state) < diamond,
                    blockName(block) + " must stay harvestable below diamond, so deep caves never lock");
        }
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void diamondTierKeepsItsOriginalBand(GameTestHelper helper) {
        assertTier(helper, 3, Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN, Blocks.ANCIENT_DEBRIS,
                Blocks.NETHERITE_BLOCK, Blocks.RESPAWN_ANCHOR, Blocks.LODESTONE);
        for (Block block : new Block[]{Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE,
                Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE}) {
            helper.assertTrue(block.defaultBlockState().is(FistTiers.tag(3)),
                    blockName(block) + " must still train the diamond tier where it is reachable");
        }
        helper.succeed();
    }

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

        levels[0] = max;
        helper.assertTrue(FistTiers.reachTier(levelOf) == 1,
                "maxing Wood must open Stone immediately, not after levelling Stone first");

        levels[1] = 1;
        helper.assertTrue(FistTiers.reachTier(levelOf) == 1, "Stone 1 stays Stone");
        levels[1] = max;
        helper.assertTrue(FistTiers.reachTier(levelOf) == 2, "maxing Stone opens Iron");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void reachHonoursTiersGrantedOutOfOrder(GameTestHelper helper) {
        int[] levels = new int[FistTiers.TIER_COUNT];
        java.util.function.IntUnaryOperator levelOf = t -> levels[t];
        levels[4] = 3;
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

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void anEmptyHandAlwaysUsesTheFist(GameTestHelper helper) {
        helper.assertTrue(FistTiers.usableWith(ItemStack.EMPTY, Blocks.STONE.defaultBlockState()),
                "an empty hand is the fist");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void nonToolItemsStillUseTheFist(GameTestHelper helper) {

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

        helper.assertTrue(!FistTiers.usableWith(new ItemStack(Items.IRON_HOE), Blocks.STONE.defaultBlockState()),
                "a hoe still counts as a tool even on a block it cannot mine");
        helper.succeed();
    }

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
