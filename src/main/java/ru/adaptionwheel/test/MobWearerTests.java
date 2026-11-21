package ru.adaptionwheel.test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import top.theillusivec4.curios.api.CuriosApi;

import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.category.AdaptionCategory;
import ru.adaptionwheel.category.Concepts;
import ru.adaptionwheel.data.AdaptionTask;
import ru.adaptionwheel.data.ModDataComponents;
import ru.adaptionwheel.data.PlayerAdaption;
import ru.adaptionwheel.data.WheelData;
import ru.adaptionwheel.item.MahoragaWheelItem;
import ru.adaptionwheel.item.ModItems;
import ru.adaptionwheel.server.AdaptionEvents;
import ru.adaptionwheel.server.Wearers;

/**
 * Gametests for the mob-wheel feature: a vanilla {@link Zombie} can carry a Mahoraga Wheel in its
 * dedicated Curios {@code wheel} slot, and a mob wearer reads the same adaptation data a player
 * wearer does.
 *
 * <p>Everything here goes through public APIs. Equipping mirrors {@code /aw mobwheel equip} exactly:
 * {@code CuriosApi.getCuriosInventory(mob).get().setEquippedCurio("wheel", 0, stack)}. No mock player
 * is created (Curios rejects the mock login), and no command is executed in a gametest; argument
 * safety is a compile-time/static concern only.
 */
@GameTestHolder(AdaptionWheel.MODID)
@PrefixGameTestTemplate(false)
public class MobWearerTests {

    private static final String SLOT = MahoragaWheelItem.WHEEL_SLOT;

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void aVanillaMobHasTheWheelCuriosSlot(GameTestHelper helper) {
        Zombie mob = spawn(helper);

        var inv = CuriosApi.getCuriosInventory(mob);
        helper.assertTrue(inv.isPresent(),
                "Curios must attach an inventory handler to a vanilla mob");
        helper.assertTrue(inv.get().getStacksHandler(SLOT).isPresent(),
                "the mobs.json entity assignment must give a Zombie the dedicated 'wheel' slot");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void aMobWithoutAWheelIsNotAWearer(GameTestHelper helper) {
        Zombie mob = spawn(helper);

        helper.assertFalse(Wearers.isWearer(mob),
                "a zombie with nothing in its wheel slot must not be a wearer");
        helper.assertFalse(AdaptionEvents.isWearingWheel((LivingEntity) mob),
                "AdaptionEvents must agree with Wearers for a mob with no wheel");
        helper.assertTrue(AdaptionEvents.getWheelStack(mob).isEmpty(),
                "the public wheel lookup must be empty for a mob with no wheel");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void aVanillaMobCanWearAWheelAndReadItsDataBack(GameTestHelper helper) {
        Zombie mob = spawn(helper);
        String concept = Concepts.type(AdaptionCategory.MOB);
        ItemStack wheel = wheelWith(Map.of(concept, 3), List.of());
        equip(helper, mob, wheel);

        helper.assertTrue(Wearers.isWearer(mob),
                "a mob with a wheel in the Curios slot must be reported as a wearer");
        helper.assertTrue(AdaptionEvents.isWearingWheel((LivingEntity) mob),
                "AdaptionEvents.isWearingWheel must agree with Wearers.isWearer for a mob");
        helper.assertTrue(AdaptionEvents.getWheelStack(mob).isPresent(),
                "the public wheel lookup must find the equipped stack");

        ItemStack read = Wearers.wheelStack(mob);
        helper.assertTrue(read != null && read.getItem() == ModItems.MAHORAGA_WHEEL.get(),
                "the stack in the slot must be the Mahoraga Wheel");
        WheelData wd = read == null ? null : read.get(ModDataComponents.WHEEL_DATA);
        helper.assertTrue(wd != null, "the equipped wheel must carry its wheel_data component");
        helper.assertTrue(wd != null && wd.levels().getOrDefault(concept, 0) == 3,
                "the level written to wheel_data must be readable back off the equipped stack");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void unequippingTheWheelStopsTheMobBeingAWearerAndClearsCaches(GameTestHelper helper) {
        Zombie mob = spawn(helper);
        equip(helper, mob, emptyWheel());
        PlayerAdaption data = AdaptionEvents.data(mob);

        AdaptionEvents.tickWearer(mob, data, true);
        helper.assertTrue(Wearers.isWearer(mob), "test setup: the mob must be wearing first");

        unequip(helper, mob);

        String thrown = null;
        try {
            // MobWearerTick routes an unequip through this same public call; for a non-player it
            // also drops the UUID-keyed caches via AdaptionEvents.clearWearerCaches.
            AdaptionEvents.tickWearer(mob, data, false);
            AdaptionEvents.clearWearerCaches(mob.getUUID());
        } catch (RuntimeException e) {
            thrown = e.getClass().getSimpleName() + ": " + e.getMessage();
        }
        helper.assertTrue(thrown == null, "unequipping a mob's wheel threw: " + thrown);
        helper.assertFalse(Wearers.isWearer(mob),
                "after setEquippedCurio(wheel, 0, EMPTY) the mob must stop being a wearer");
        helper.assertFalse(AdaptionEvents.isWearingWheel((LivingEntity) mob),
                "AdaptionEvents must also report the mob as no longer wearing");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void aMobWearerTakesReducedDamageFromItsWheelAdaptation(GameTestHelper helper) {
        Zombie wearer = spawn(helper);
        Zombie control = spawn(helper);

        String concept = Concepts.type(AdaptionCategory.EXPLOSION);
        equip(helper, wearer, wheelWith(Map.of(concept, 4), List.of()));

        // The mob's own tick loads wheel_data into its attachment through the public tick path.
        PlayerAdaption data = AdaptionEvents.data(wearer);
        AdaptionEvents.tickWearer(wearer, data, true);
        helper.assertTrue(data.levelOrZero(concept) == 4,
                "the mob must have loaded the explosion level from the wheel before the hit");

        float wearerMax = wearer.getMaxHealth();
        float controlMax = control.getMaxHealth();
        wearer.setHealth(wearerMax);
        control.setHealth(controlMax);
        float raw = 10f;

        AdaptionEvents.handleDirectHealthReduction(wearer, wearerMax - raw);
        AdaptionEvents.handleDirectHealthReduction(control, controlMax - raw);

        float wearerLost = wearerMax - wearer.getHealth();
        float controlLost = controlMax - control.getHealth();

        helper.assertTrue(controlLost == raw,
                "the control mob without a wheel must take the full direct health change, got "
                        + controlLost);
        helper.assertTrue(wearerLost > 0f && wearerLost < controlLost,
                "a mob wearing a wheel with an adapted explosion level must take reduced damage: "
                        + wearerLost + " vs " + controlLost);
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void runningAnalysisIsSavedToTheWheelAndMergedBackOnReequip(GameTestHelper helper) {
        Zombie mob = spawn(helper);
        ItemStack wheel = emptyWheel();
        equip(helper, mob, wheel);

        String taskConcept = Concepts.type(AdaptionCategory.MOB);
        PlayerAdaption data = AdaptionEvents.data(mob);
        AdaptionEvents.startTask(mob, data, taskConcept, 200);
        helper.assertTrue(data.tasks.size() == 1,
                "the public startTask API must begin an analysis on the mob's attachment");

        // A level earned in the attachment while the wheel is on must travel with the wheel too.
        String otherConcept = Concepts.type(AdaptionCategory.PROJECTILE);
        data.levels.put(otherConcept, 6);
        data.invalidateAdaptCount();

        // The 1 Hz save path, exposed publicly.
        AdaptionEvents.saveToStack(wheel, data);
        WheelData saved = wheel.get(ModDataComponents.WHEEL_DATA);
        helper.assertTrue(saved != null && saved.tasks().size() == 1,
                "a running analysis must be written to the wheel the mob wears");
        helper.assertTrue(saved != null && saved.tasks().get(0).concept.equals(taskConcept),
                "the analysis written to the wheel must be the one that was started");
        helper.assertTrue(saved != null && saved.levels().getOrDefault(otherConcept, 0) == 6,
                "the wheel must also carry the levels the mob earned");

        // What a re-equip does: the in-memory task list is gone and a level gained meanwhile wins.
        data.tasks.clear();
        data.levels.put(otherConcept, 8);
        saved.loadInto(data);

        helper.assertTrue(data.tasks.size() == 1 && data.tasks.get(0).concept.equals(taskConcept),
                "the saved analysis must come back when the wheel is equipped again");
        helper.assertTrue(data.level(otherConcept) == 8,
                "the merge must keep the higher level, never walk it backwards");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void reloadingTheWheelNeverDuplicatesARunningAnalysis(GameTestHelper helper) {
        Zombie mob = spawn(helper);
        PlayerAdaption data = AdaptionEvents.data(mob);

        String concept = Concepts.type(AdaptionCategory.MAGIC);
        AdaptionTask original = new AdaptionTask(concept, 120, 200);
        WheelData saved = new WheelData(new HashMap<>(), new ArrayList<>(), new ArrayList<>(),
                new HashMap<>(), new ArrayList<>(), new ArrayList<>(List.of(original)), new HashMap<>());

        // An equip transition can load the same wheel more than once; a task is keyed by concept.
        saved.loadInto(data);
        saved.loadInto(data);

        helper.assertTrue(data.tasks.size() == 1,
                "loading the same wheel twice must not start the same analysis twice, got "
                        + data.tasks.size());
        helper.assertTrue(data.tasks.get(0).timer == 120,
                "the saved timer must survive the merge unchanged");
        helper.succeed();
    }

    /** A freshly created wheel carrying the exact data the mod's own equip command writes. */
    private static ItemStack wheelWith(Map<String, Integer> levels, List<AdaptionTask> tasks) {
        ItemStack stack = new ItemStack(ModItems.MAHORAGA_WHEEL.get());
        stack.set(ModDataComponents.WHEEL_DATA, new WheelData(
                new HashMap<>(levels),
                new ArrayList<>(),
                new ArrayList<>(),
                new HashMap<>(),
                new ArrayList<>(),
                new ArrayList<>(tasks),
                new HashMap<>()));
        return stack;
    }

    private static ItemStack emptyWheel() {
        return wheelWith(Map.of(), List.of());
    }

    /** A mob with no AI: no wandering or sun-seeking, so every assertion is deterministic. */
    private static Zombie spawn(GameTestHelper helper) {
        return helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(2, 2, 2));
    }

    private static void equip(GameTestHelper helper, LivingEntity mob, ItemStack wheel) {
        var inv = CuriosApi.getCuriosInventory(mob);
        helper.assertTrue(inv.isPresent() && inv.get().getStacksHandler(SLOT).isPresent(),
                "test setup: the mob must have the dedicated Curios wheel slot");
        inv.get().setEquippedCurio(SLOT, 0, wheel);
    }

    private static void unequip(GameTestHelper helper, LivingEntity mob) {
        var inv = CuriosApi.getCuriosInventory(mob);
        helper.assertTrue(inv.isPresent() && inv.get().getStacksHandler(SLOT).isPresent(),
                "test setup: the mob must have the dedicated Curios wheel slot");
        inv.get().setEquippedCurio(SLOT, 0, ItemStack.EMPTY);
    }
}
