package ru.adaptionwheel.test;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.category.AdaptionCategory;
import ru.adaptionwheel.category.Concepts;
import ru.adaptionwheel.config.AdaptionConfig;
import ru.adaptionwheel.data.PlayerAdaption;
import ru.adaptionwheel.server.AdaptionEvents;

/**
 * Pins "full adaptation is immunity, not a 60 % discount".
 *
 * <p>The leveled defence used to end at the reduction table: level eight of {@code Type_Magic}
 * took 60 % off a magic hit and that was the whole promise. The one-time {@code Env_*} adaptations
 * were already full immunity, so the mod had two different meanings of "adapted" depending on
 * which family the phenomenon belonged to. Max level now cancels the hit outright, and these tests
 * pin the boundaries and the three deliberate holes in it.
 *
 * <p>Two traps this file exists to keep closed:
 * <ul>
 *   <li>{@code active()} answers from the {@code adapted} set, and a leveled concept lives in
 *       {@code levels}. The old fire branch asked {@code active(Type_FIRE)} and was therefore false
 *       for every player at every level -- a full fire adaptation still burned. Nothing here may
 *       use {@code active()} for a leveled concept; {@code levelOrZero()} is the accessor that both
 *       reads the level and honours the panel's off switch.</li>
 *   <li>Lava is not fire. {@code Type_FIRE} is trained by fire ticks and fireballs, {@code Env_Lava}
 *       by standing in lava, and maxing one must not grant the other.</li>
 * </ul>
 */
@GameTestHolder(AdaptionWheel.MODID)
@PrefixGameTestTemplate(false)
public class DefenseImmunityTests {

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void fullImmunityIsOnByDefault(GameTestHelper helper) {
        helper.assertTrue(AdaptionConfig.FULL_IMMUNITY_AT_MAX.get(),
                "full adaptation must default to immunity; the reduction table is the road there,"
                        + " not the destination");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void levelEightIsTheBoundary(GameTestHelper helper) {
        PlayerAdaption data = new PlayerAdaption();
        DamageSources sources = helper.getLevel().damageSources();
        String concept = Concepts.type(AdaptionCategory.MAGIC);

        data.levels.put(concept, PlayerAdaption.MAX_LEVEL - 1);
        helper.assertFalse(AdaptionEvents.isMaxedImmunity(data, AdaptionCategory.MAGIC, sources.magic()),
                "level 7 must still be a reduction; immunity belongs to the completed adaptation");

        data.levels.put(concept, PlayerAdaption.MAX_LEVEL);
        helper.assertTrue(AdaptionEvents.isMaxedImmunity(data, AdaptionCategory.MAGIC, sources.magic()),
                "level 8 of Type_MAGIC must nullify a magic hit");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void thePanelSwitchTurnsMaxedImmunityOff(GameTestHelper helper) {
        PlayerAdaption data = new PlayerAdaption();
        DamageSources sources = helper.getLevel().damageSources();
        String concept = Concepts.type(AdaptionCategory.MAGIC);
        data.levels.put(concept, PlayerAdaption.MAX_LEVEL);
        DamageSource magic = sources.magic();

        helper.assertTrue(AdaptionEvents.isMaxedImmunity(data, AdaptionCategory.MAGIC, magic),
                "a maxed adaptation must stop its phenomenon while it is on");

        data.toggleEnabled(concept);
        helper.assertFalse(AdaptionEvents.isMaxedImmunity(data, AdaptionCategory.MAGIC, magic),
                "switching the adaptation off must also switch off its immunity, not just the"
                        + " reduction table");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void lavaIsNotFire(GameTestHelper helper) {
        PlayerAdaption data = new PlayerAdaption();
        DamageSources sources = helper.getLevel().damageSources();

        data.levels.put(Concepts.type(AdaptionCategory.FIRE), PlayerAdaption.MAX_LEVEL);
        helper.assertTrue(
                AdaptionEvents.isMaxedImmunity(data, AdaptionCategory.FIRE, sources.inFire()),
                "a maxed Type_FIRE must nullify ordinary burning");
        helper.assertFalse(
                AdaptionEvents.isMaxedImmunity(data, AdaptionCategory.FIRE, sources.lava()),
                "lava belongs to the one-time Env_Lava adaptation; Type_FIRE must never grant it");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void aMaxedContactStopsOnlyThatMob(GameTestHelper helper) {
        PlayerAdaption data = new PlayerAdaption();
        DamageSources sources = helper.getLevel().damageSources();
        Zombie zombie = new Zombie(helper.getLevel());
        Skeleton skeleton = new Skeleton(EntityType.SKELETON, helper.getLevel());

        data.levels.put(Concepts.contact("minecraft:zombie"), PlayerAdaption.MAX_LEVEL);

        DamageSource zombieHit = sources.mobAttack(zombie);
        helper.assertTrue(
                AdaptionEvents.isMaxedImmunity(data, AdaptionCategory.match(zombieHit), zombieHit),
                "a maxed Contact_minecraft:zombie must stop a zombie's melee hit");

        DamageSource skeletonHit = sources.mobAttack(skeleton);
        helper.assertFalse(
                AdaptionEvents.isMaxedImmunity(data, AdaptionCategory.match(skeletonHit), skeletonHit),
                "a maxed contact adaptation must not spill over to a different mob");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void environmentalCategoriesStayOneTime(GameTestHelper helper) {
        PlayerAdaption data = new PlayerAdaption();
        DamageSources sources = helper.getLevel().damageSources();

        // A legacy save could still carry a leveled Type_Fall key; it must not turn the one-time
        // fall adaptation into a leveled immunity just because the level happens to be eight.
        data.levels.put(Concepts.type(AdaptionCategory.FALL), PlayerAdaption.MAX_LEVEL);
        helper.assertFalse(
                AdaptionEvents.isMaxedImmunity(data, AdaptionCategory.FALL, sources.fall()),
                "fall is the one-time Env_Fall adaptation, not a leveled Type_Fall");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void killDamageIsNotAPhenomenon(GameTestHelper helper) {
        PlayerAdaption data = new PlayerAdaption();
        DamageSources sources = helper.getLevel().damageSources();

        data.levels.put(Concepts.type(AdaptionCategory.OTHER), PlayerAdaption.MAX_LEVEL);
        helper.assertFalse(
                AdaptionEvents.isMaxedImmunity(data, AdaptionCategory.OTHER, sources.genericKill()),
                "the /kill command bypasses invulnerability on purpose; an adaptation must not"
                        + " make an operator's kill fail");
        helper.succeed();
    }
}
