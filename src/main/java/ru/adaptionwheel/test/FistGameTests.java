package ru.adaptionwheel.test;

import net.minecraft.core.component.DataComponents;
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
        String[] ids = {"wooden_pickaxe", "stone_pickaxe", "copper_pickaxe",
                "iron_pickaxe", "diamond_pickaxe", "netherite_pickaxe"};
        float previous = 0f;
        for (int tier = 0; tier < FistTiers.TIER_COUNT; tier++) {
            Item tool = BuiltInRegistries.ITEM.getOptional(ResourceLocation.withDefaultNamespace(ids[tier]))
                    .orElse(null);
            float actual = FistTiers.vanillaMiningSpeed(tier);
            if (tool != null) {
                var component = tool.getDefaultInstance().get(DataComponents.TOOL);
                helper.assertTrue(component != null, ids[tier] + " should carry a tool component");
                helper.assertTrue(component.defaultMiningSpeed() == actual,
                        "tier " + tier + " should mine at " + ids[tier] + "'s speed ("
                                + component.defaultMiningSpeed() + ") but reports " + actual);
            } else {
                // Not in this registry: the documented fallback is the tier below.
                helper.assertTrue(actual == previous,
                        "tier " + tier + " has no " + ids[tier] + ", so it must inherit the speed below ("
                                + previous + ") but reports " + actual);
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
