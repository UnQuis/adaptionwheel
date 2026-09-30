package ru.adaptionwheel.test;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import ru.adaptionwheel.entity.DiscipleEntity;

/**
 * The Disciple's scaling and its adapting.
 *
 * <p>Both are the mob's entire reason to exist, and both are pure arithmetic, so this is the whole
 * of what can be pinned here. A world is not needed and, more to the point, a world cannot reach
 * them: the scaling only happens on a living entity that has found a player, and a gametest cannot
 * make a player while Curios is installed.</p>
 */
@GameTestHolder(ru.adaptionwheel.AdaptionWheel.MODID)
@PrefixGameTestTemplate(false)
public class DiscipleTests {

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void scalingIsMonotonicAndBounded(GameTestHelper helper) {
        double lastHealth = 0.0D;
        double lastDamage = 0.0D;
        for (int count = 0; count <= 500; count++) {
            int scaled = DiscipleEntity.scaledAdaptations(count);
            helper.assertTrue(scaled >= 0 && scaled <= DiscipleEntity.SCALING_CAP,
                    "scaling escaped its bounds at " + count + " adaptations: " + scaled);
            helper.assertTrue(DiscipleEntity.healthFor(count) >= lastHealth,
                    "a Disciple got weaker going from " + (count - 1) + " to " + count);
            helper.assertTrue(DiscipleEntity.damageFor(count) >= lastDamage,
                    "a Disciple hit weaker going from " + (count - 1) + " to " + count);
            lastHealth = DiscipleEntity.healthFor(count);
            lastDamage = DiscipleEntity.damageFor(count);
        }
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void aFreshPlayerFacesAnOrdinaryMob(GameTestHelper helper) {
        // With nothing adapted the Disciple has to be a normal, unremarkable zombie-strength mob,
        // or it is a wall to a new player rather than a fight.
        helper.assertTrue(DiscipleEntity.healthFor(0) <= 25.0D,
                "an unadapted player must not meet a boss, got " + DiscipleEntity.healthFor(0));
        helper.assertTrue(DiscipleEntity.damageFor(0) <= 5.0D,
                "an unadapted player must be able to survive a hit, got "
                        + DiscipleEntity.damageFor(0));
        helper.assertTrue(DiscipleEntity.armourFor(0) == 0.0D,
                "an unadapted player must not meet a wall, got " + DiscipleEntity.armourFor(0));
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void theCapStopsTheFightBecomingImpossible(GameTestHelper helper) {
        // Past the cap the numbers must not move at all: the wheel goes far beyond any cap, and an
        // uncapped Disciple at five hundred adaptations is a mob nothing can scratch.
        double capped = DiscipleEntity.healthFor(DiscipleEntity.SCALING_CAP);
        helper.assertTrue(DiscipleEntity.healthFor(DiscipleEntity.SCALING_CAP + 1) == capped,
                "health kept scaling past the cap");
        helper.assertTrue(DiscipleEntity.healthFor(100000) == capped,
                "an absurd adaptation count must clamp to the same value as the cap");
        helper.assertTrue(capped > DiscipleEntity.healthFor(0),
                "the cap must still be a real increase over an unadapted player");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void learningIsCumulativeButBounded(GameTestHelper helper) {
        // Hit it repeatedly with the same type and it must get better at ignoring that type...
        float resistance = 0.0F;
        for (int hit = 0; hit < 50; hit++) {
            resistance = DiscipleEntity.nextResistance(resistance, true);
            helper.assertTrue(resistance >= 0.0F && resistance <= 1.0F,
                    "resistance left the legal range on hit " + hit + ": " + resistance);
        }
        // ...but never past the cap, and a new damage type starts the conversation over. Without
        // the reset the Disciple would end the fight immune to everything after ten seconds.
        helper.assertTrue(resistance < 1.0F,
                "it must still take some damage after fifty hits, resistance was " + resistance);
        float fresh = DiscipleEntity.nextResistance(resistance, false);
        helper.assertTrue(fresh < resistance,
                "a new damage type must reset the resistance, got " + fresh + " from " + resistance);
        helper.assertTrue(fresh > 0.0F,
                "even a new damage type is partly shrugged off, or there is no adapting at all");
        helper.succeed();
    }
}
