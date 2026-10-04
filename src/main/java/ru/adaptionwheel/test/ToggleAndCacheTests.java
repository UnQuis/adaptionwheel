package ru.adaptionwheel.test;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.category.Concepts;
import ru.adaptionwheel.data.PlayerAdaption;
import ru.adaptionwheel.server.CacheService;

@GameTestHolder(AdaptionWheel.MODID)
@PrefixGameTestTemplate(false)
public class ToggleAndCacheTests {

    private static PlayerAdaption empty() {
        return new PlayerAdaption();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void aDisabledConceptReadsAsZeroAndInactive(GameTestHelper helper) {

        PlayerAdaption data = empty();
        data.levels.put(Concepts.type(ru.adaptionwheel.category.AdaptionCategory.FIRE), 5);
        data.adapted.add(Concepts.ENV_LAVA);

        helper.assertTrue(data.level(Concepts.type(ru.adaptionwheel.category.AdaptionCategory.FIRE)) == 5, "the level is still stored");
        helper.assertTrue(data.isAdapted(Concepts.ENV_LAVA), "the adaptation is still stored");

        helper.assertTrue(data.toggleEnabled(Concepts.type(ru.adaptionwheel.category.AdaptionCategory.FIRE)) == false, "the first toggle turns it off");
        helper.assertTrue(data.toggleEnabled(Concepts.ENV_LAVA) == false, "one-time adaptations toggle too");

        helper.assertTrue(data.level(Concepts.type(ru.adaptionwheel.category.AdaptionCategory.FIRE)) == 5, "turning it off does not forget the level");
        helper.assertTrue(data.isAdapted(Concepts.ENV_LAVA), "turning it off does not forget the adaptation");
        helper.assertTrue(data.levelOrZero(Concepts.type(ru.adaptionwheel.category.AdaptionCategory.FIRE)) == 0, "effects read zero while it is off");
        helper.assertTrue(!data.active(Concepts.ENV_LAVA), "effects see it as inactive while it is off");

        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void aSecondToggleTurnsItBackOn(GameTestHelper helper) {

        PlayerAdaption data = empty();
        data.levels.put(Concepts.type(ru.adaptionwheel.category.AdaptionCategory.FIRE), 5);
        data.toggleEnabled(Concepts.type(ru.adaptionwheel.category.AdaptionCategory.FIRE));
        helper.assertTrue(data.toggleEnabled(Concepts.type(ru.adaptionwheel.category.AdaptionCategory.FIRE)), "the second toggle turns it back on");
        helper.assertTrue(data.levelOrZero(Concepts.type(ru.adaptionwheel.category.AdaptionCategory.FIRE)) == 5, "and the level reads again");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void theCacheIsFiftySlotsWideAndKeepsWhatIsInIt(GameTestHelper helper) {

        PlayerAdaption data = empty();
        data.cache.add(new ItemStack(Items.DIAMOND, 7));
        data.cache.add(new ItemStack(Items.EMERALD, 3));

        CacheService.pad(data);
        helper.assertTrue(data.cache.size() == CacheService.SLOTS, "padding brings it to the full width");
        helper.assertTrue(data.cache.get(0).is(Items.DIAMOND) && data.cache.get(0).getCount() == 7,
                "the first stack is untouched");
        helper.assertTrue(data.cache.get(1).is(Items.EMERALD) && data.cache.get(1).getCount() == 3,
                "the second stack is untouched");
        helper.assertTrue(data.cache.get(2).isEmpty(), "and the rest are empty slots");

        data.cache.set(0, ItemStack.EMPTY);
        helper.assertTrue(data.cache.get(1).is(Items.EMERALD), "emptying one slot leaves the others alone");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void aShortCacheGrowsWithoutLosingStacks(GameTestHelper helper) {

        PlayerAdaption data = empty();
        data.cache.add(new ItemStack(Items.NETHERITE_INGOT, 2));
        data.cache.add(new ItemStack(Items.GOLD_INGOT, 64));

        CacheService.pad(data);
        helper.assertTrue(data.cache.size() == CacheService.SLOTS, "it grows to the full width");
        helper.assertTrue(data.cache.get(0).is(Items.NETHERITE_INGOT), "the first stack survives");
        helper.assertTrue(data.cache.get(1).is(Items.GOLD_INGOT) && data.cache.get(1).getCount() == 64,
                "a full stack keeps its count");
        helper.succeed();
    }
}