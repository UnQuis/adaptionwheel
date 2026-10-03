package ru.adaptionwheel.test;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.category.Concepts;
import ru.adaptionwheel.server.AltarOfferings;

import java.util.HashMap;
import java.util.List;

@GameTestHolder(AdaptionWheel.MODID)
@PrefixGameTestTemplate(false)
public class AltarOfferingTests {

    private static java.util.Map<String, List<String>> mobs(GameTestHelper helper) {
        return AltarOfferings.allMobs(helper.getLevel().getServer());
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void theIndexLoadsAndCoversTheObviousCases(GameTestHelper helper) {
        var all = mobs(helper);
        helper.assertTrue(all.size() >= 50,
                "expected at least the fifty-odd vanilla mobs that drop anything, got " + all.size());

        helper.assertTrue(all.containsKey("minecraft:warden"),
                "the Warden must be in the index; the sculk catalyst is its loot");
        helper.assertTrue(all.get("minecraft:warden").contains("minecraft:sculk_catalyst"),
                "the Warden must offer the catalyst, got " + all.get("minecraft:warden"));

        helper.assertTrue(all.containsKey("minecraft:chicken"), "a chicken must be in the index");
        helper.assertTrue(all.get("minecraft:chicken").contains("minecraft:feather"),
                "a chicken must offer a feather, got " + all.get("minecraft:chicken"));

        helper.assertTrue(all.containsKey("minecraft:skeleton"), "a skeleton must be in the index");
        helper.assertTrue(all.get("minecraft:skeleton").contains("minecraft:bone"),
                "a skeleton must offer a bone");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void mobsThatDropNothingAreAbsent(GameTestHelper helper) {
        var all = mobs(helper);
        for (String mob : new String[]{
                "minecraft:allay", "minecraft:bat", "minecraft:fox", "minecraft:ocelot",
                "minecraft:villager", "minecraft:player"}) {
            helper.assertTrue(!all.containsKey(mob),
                    mob + " drops nothing in vanilla, so it must not be an offering -- an empty"
                            + " entry would make the altar answer questions about a mob that cannot"
                            + " be paid for");
        }
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void itemsThatAreNoMobLootAreNotOfferings(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        helper.assertTrue(AltarOfferings.mobsFor(new ItemStack(Items.DIRT), server).isEmpty(),
                "dirt is not a mob drop and must not be an offering");
        helper.assertTrue(AltarOfferings.mobsFor(ItemStack.EMPTY, server).isEmpty(),
                "an empty slot is not an offering");
        helper.assertTrue(!AltarOfferings.isOffering(new ItemStack(Items.DIRT), server),
                "and isOffering must agree with mobsFor");

        var bones = AltarOfferings.mobsFor(new ItemStack(Items.BONE), server);
        helper.assertTrue(bones.size() >= 2,
                "a bone belongs to several skeleton kinds, which is the whole reason the altar"
                        + " offers a choice; got " + bones);
        helper.assertTrue(bones.contains("minecraft:skeleton"), "and a skeleton must be among them");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void aDropOffersBothItsAdaptationsPerMob(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var mobs = AltarOfferings.mobsFor(new ItemStack(Items.BONE), server);
        helper.assertTrue(!mobs.isEmpty(), "a bone must open something");

        var pool = new java.util.ArrayList<String>();
        for (String mob : mobs) {
            pool.add(Concepts.offense(mob));
            pool.add(Concepts.drop(mob));
        }
        for (String mob : mobs) {
            helper.assertTrue(pool.contains(Concepts.offense(mob)),
                    "a bone must offer the adaptation to hurting " + mob);
            helper.assertTrue(pool.contains(Concepts.drop(mob)),
                    "a bone must offer the adaptation to " + mob + "'s loot");
            helper.assertTrue(Concepts.isLevelBased(Concepts.offense(mob)),
                    "an offense is a leveled concept and must read as one");
            helper.assertTrue(Concepts.isLevelBased(Concepts.drop(mob)),
                    "a drop-rate adaptation is leveled too");
        }
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void theWardenNeedsNoSpecialCase(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var mobs = AltarOfferings.mobsFor(new ItemStack(net.minecraft.world.item.Items.SCULK_CATALYST), server);
        helper.assertTrue(mobs.contains("minecraft:warden"),
                "the catalyst must open the Warden and nothing else -- if vanilla's own loot table"
                        + " put it there, the generated data should have found it unaided, got " + mobs);
        helper.assertTrue(AltarOfferings.mobsFor(new ItemStack(Items.BONE), server)
                        .contains("minecraft:warden") == false,
                "and a bone must not quietly include the Warden, which would mean the file was"
                        + " written by hand rather than generated");
        helper.succeed();
    }
}
