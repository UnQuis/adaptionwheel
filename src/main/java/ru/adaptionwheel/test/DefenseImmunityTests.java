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
import ru.adaptionwheel.data.LegacyConcepts;
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
 *   <li>Leveled concepts live in {@code levels} and are read with {@code levelOrZero()}; one-time
 *       concepts live in {@code adapted} and are read with {@code active()}. Fire is a one-time
 *       {@code Env_Fire} adaptation, so its immunity must be asked for with {@code active()}, while
 *       a {@code Type_*} immunity is asked for with {@code levelOrZero()}. Mixing the two is how a
 *       completed adaptation silently keeps taking damage -- the {@code active(Type_FIRE)} arm was
 *       correct when fire was one-time and only looked like the accessor trap after the concept was
 *       renamed.</li>
 *   <li>Lava is not fire. {@code Env_Fire} is trained by fire ticks and fireballs, {@code Env_Lava}
 *       by standing in lava, and adapting to one must not grant the other.</li>
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
    public static void fireIsAOneTimeEnvironmentAdaptation(GameTestHelper helper) {
        helper.assertTrue(Concepts.type(AdaptionCategory.FIRE).equals(Concepts.ENV_FIRE),
                "the fire concept must be Env_Fire; Type_Fire would promise an eight-level ladder"
                        + " that the one-time adaptation does not have");
        helper.assertTrue(Concepts.isOneTime(Concepts.ENV_FIRE),
                "fire is completed once, like the other Env_ adaptations");
        helper.assertFalse(Concepts.isLevelBased(Concepts.ENV_FIRE),
                "a one-time adaptation must not be read as leveled");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void fireImmunityComesFromTheOneTimeConcept(GameTestHelper helper) {
        PlayerAdaption data = new PlayerAdaption();
        DamageSources sources = helper.getLevel().damageSources();
        DamageSource burning = sources.inFire();
        DamageSource lava = sources.lava();

        helper.assertFalse(AdaptionEvents.hasFullImmunity(data, AdaptionCategory.FIRE, burning),
                "a wheel that has not adapted to fire must still burn");

        data.adapted.add(Concepts.ENV_FIRE);
        helper.assertTrue(AdaptionEvents.hasFullImmunity(data, AdaptionCategory.FIRE, burning),
                "Env_Fire is one-time and makes ordinary fire harmless");
        helper.assertFalse(AdaptionEvents.hasFullImmunity(data, AdaptionCategory.FIRE, lava),
                "Env_Fire must not grant lava immunity; lava belongs to Env_Lava");

        data.toggleEnabled(Concepts.ENV_FIRE);
        helper.assertFalse(AdaptionEvents.hasFullImmunity(data, AdaptionCategory.FIRE, burning),
                "switching Env_Fire off must switch its immunity off too");

        data.toggleEnabled(Concepts.ENV_FIRE);
        data.adapted.add(Concepts.ENV_LAVA);
        helper.assertTrue(AdaptionEvents.hasFullImmunity(data, AdaptionCategory.FIRE, lava),
                "Env_Lava is the only thing that answers lava damage");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void aLevelOnTheFireKeyIsNotAnAdaptation(GameTestHelper helper) {
        PlayerAdaption data = new PlayerAdaption();
        DamageSources sources = helper.getLevel().damageSources();

        // The old Type_FIRE key lived in `levels`. After the rename nothing may read a level as a
        // fire immunity: only `adapted` counts.
        data.levels.put(Concepts.ENV_FIRE, PlayerAdaption.MAX_LEVEL);
        helper.assertFalse(
                AdaptionEvents.hasFullImmunity(data, AdaptionCategory.FIRE, sources.inFire()),
                "a stray level on Env_Fire must not stand in for the one-time grant");
        helper.assertFalse(
                AdaptionEvents.isMaxedImmunity(data, AdaptionCategory.FIRE, sources.inFire()),
                "fire is not a leveled phenomenon, so maxed immunity must not claim it");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void legacyTypeFireBecomesEnvFire(GameTestHelper helper) {
        PlayerAdaption data = new PlayerAdaption();
        data.levels.put("Type_FIRE", 3);
        data.adapted.add("Type_FIRE");
        data.history.add("Type_FIRE");
        data.disabled.add("Type_FIRE");

        LegacyConcepts.migrate(data);

        helper.assertTrue(data.adapted.contains(Concepts.ENV_FIRE),
                "a save carrying Type_FIRE must keep the earned fire adaptation under Env_Fire");
        helper.assertFalse(data.adapted.contains("Type_FIRE"),
                "the old key must not survive next to the new one");
        helper.assertFalse(data.levels.containsKey("Type_FIRE"),
                "the leveled key must be gone, or the Type_ prefix fallback would treat it as a ladder");
        helper.assertFalse(data.history.contains("Type_FIRE"),
                "history is displayed, so it must carry the new name");
        helper.assertTrue(data.disabled.contains(Concepts.ENV_FIRE),
                "a player who switched fire off must keep it off after the rename");
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
