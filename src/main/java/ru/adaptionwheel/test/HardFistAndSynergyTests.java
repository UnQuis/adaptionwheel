package ru.adaptionwheel.test;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.category.AdaptionCategory;
import ru.adaptionwheel.category.CombatFistTiers;
import ru.adaptionwheel.category.Concepts;
import ru.adaptionwheel.category.FistTiers;
import ru.adaptionwheel.category.Synergies;
import ru.adaptionwheel.config.AdaptionConfig;
import ru.adaptionwheel.data.PlayerAdaption;
import ru.adaptionwheel.server.HardFist;
import ru.adaptionwheel.server.SynergyEffects;

@GameTestHolder(AdaptionWheel.MODID)
@PrefixGameTestTemplate(false)
public class HardFistAndSynergyTests {

    private static PlayerAdaption empty() {
        return new PlayerAdaption();
    }

    private static PlayerAdaption withLevel(String concept, int level) {
        PlayerAdaption data = empty();
        data.levels.put(concept, level);
        data.invalidateAdaptCount();
        return data;
    }

    private static PlayerAdaption withLevels(String first, int firstLevel, String second, int secondLevel) {
        PlayerAdaption data = empty();
        data.levels.put(first, firstLevel);
        data.levels.put(second, secondLevel);
        data.invalidateAdaptCount();
        return data;
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void anUntrainedFistDealsNoBonus(GameTestHelper helper) {

        helper.assertTrue(HardFist.bonus(empty()) == 0f,
                "a wheel with no fist level adds nothing to a punch");
        helper.assertTrue(HardFist.bonus(withLevel(Concepts.type(AdaptionCategory.FIRE), 8)) == 0f,
                "an unrelated adaptation does not make the fist train itself");
        helper.succeed();
    }

    private static float expectedFist(int level, int adaptCount) {
        float base = (float) (AdaptionConfig.FIST_DAMAGE_BASE.get()
                + AdaptionConfig.FIST_DAMAGE_PER_LEVEL.get() * level);
        return base * (1f + adaptCount * AdaptionConfig.FIST_DAMAGE_PER_ADAPTATION.get().floatValue());
    }

    private static float expectedFist(int tier, int level, int adaptCount) {
        return expectedFist(level, adaptCount) * AdaptionConfig.fistDamageTierMultiplier(tier);
    }

    private static PlayerAdaption withFistLevel(int tier, int level) {
        return withLevel(CombatFistTiers.concept(tier), level);
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void theFistGrowsWithEveryLevel(GameTestHelper helper) {

        float previous = 0f;
        for (int level = 1; level <= PlayerAdaption.MAX_LEVEL; level++) {
            float bonus = HardFist.bonus(withFistLevel(0, level));
            helper.assertTrue(bonus > previous,
                    "level " + level + " (" + bonus + ") must be stronger than level "
                            + (level - 1) + " (" + previous + ")");
            previous = bonus;
        }

        helper.assertTrue(Math.abs(previous - expectedFist(0, PlayerAdaption.MAX_LEVEL, 1)) < 0.0001f,
                "a maxed wooden fist on a wheel that knows only itself pays "
                        + expectedFist(0, PlayerAdaption.MAX_LEVEL, 1) + ", which is not " + previous);
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void theFistAlsoScalesWithHowMuchTheWheelKnows(GameTestHelper helper) {

        String fist = CombatFistTiers.concept(0);
        PlayerAdaption knowsNothing = withLevel(fist, 4);
        PlayerAdaption knowsFifty = withLevel(fist, 4);
        for (int i = 0; i < 50; i++) {
            knowsFifty.adapted.add("Filler_" + i);
        }
        knowsFifty.invalidateAdaptCount();

        float bare = HardFist.bonus(knowsNothing);
        float broad = HardFist.bonus(knowsFifty);

        helper.assertTrue(Math.abs(bare - expectedFist(0, 4, knowsNothing.getAdaptCount())) < 0.0001f,
                "a level-4 fist on a wheel that knows " + knowsNothing.getAdaptCount()
                        + " adaptations pays " + expectedFist(0, 4, knowsNothing.getAdaptCount())
                        + ", which is not " + bare);
        helper.assertTrue(Math.abs(broad - expectedFist(0, 4, knowsFifty.getAdaptCount())) < 0.0001f,
                "the same level-4 fist on a wheel that knows " + knowsFifty.getAdaptCount()
                        + " adaptations pays " + expectedFist(0, 4, knowsFifty.getAdaptCount())
                        + ", which is not " + broad);
        helper.assertTrue(broad > bare, "and it must actually be stronger, " + broad + " vs " + bare);
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void aFistThatIsSwitchedOffStopsPaying(GameTestHelper helper) {

        PlayerAdaption data = withFistLevel(0, 8);
        helper.assertTrue(HardFist.bonus(data) > 0f, "the fist pays while it is on");

        data.toggleEnabled(CombatFistTiers.concept(0));
        helper.assertTrue(data.level(CombatFistTiers.concept(0)) == 8,
                "switching it off forgets nothing");
        helper.assertTrue(HardFist.bonus(data) == 0f,
                "but a fist that is switched off must add no damage at all");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void synergyStrengthIsNeverBelowItsFloor(GameTestHelper helper) {

        PlayerAdaption emptyWheel = empty();
        for (Synergies.Synergy synergy : Synergies.ALL) {
            double strength = SynergyEffects.strengthOf(emptyWheel, synergy);
            helper.assertTrue(strength >= 1.0,
                    synergy.id() + " fell to " + strength + ", below its 1.0 floor");
        }

        PlayerAdaption broad = empty();
        for (int i = 0; i < 200; i++) {
            broad.adapted.add("Filler_" + i);
        }
        broad.invalidateAdaptCount();
        for (Synergies.Synergy synergy : Synergies.ALL) {
            double strength = SynergyEffects.strengthOf(broad, synergy);
            helper.assertTrue(strength <= 4.0 + 0.0001,
                    synergy.id() + " rose to " + strength + ", above its 4.0 ceiling");
        }
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void aSynergyGrowsWithTheLevelsBehindIt(GameTestHelper helper) {

        String fire = Concepts.type(AdaptionCategory.FIRE);
        double barely = SynergyEffects.strengthOf(withLevel(fire, 1), Synergies.ASHWALKER);
        double halfway = SynergyEffects.strengthOf(withLevel(fire, 4), Synergies.ASHWALKER);
        double maxed = SynergyEffects.strengthOf(withLevel(fire, PlayerAdaption.MAX_LEVEL),
                Synergies.ASHWALKER);

        helper.assertTrue(barely < halfway && halfway < maxed,
                "Ashwalker must scale with its fire level: " + barely + ", " + halfway + ", " + maxed);
        helper.assertTrue(Math.abs(maxed - 4.0) < 1e-9,
                "a maxed requirement is worth the full 1.0 + 3.0 * 1.0 = 4.0, not " + maxed);
        helper.assertTrue(Math.abs(halfway - 2.5) < 1e-9,
                "half a level ladder is half strength, 2.5, not " + halfway);
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void aMaxedRequirementDoesNotDoubleCount(GameTestHelper helper) {

        String fist = FistTiers.concept(2);
        String thermal = Concepts.MUTATION_THERMAL;
        PlayerAdaption withoutFist = withLevels(thermal, PlayerAdaption.MAX_LEVEL, fist, 0);
        PlayerAdaption withFist = withLevels(thermal, PlayerAdaption.MAX_LEVEL, fist,
                PlayerAdaption.MAX_LEVEL);

        helper.assertTrue(Synergies.satisfied(withFist, Synergies.GOLIATH),
                "a maxed fist plus Thermal Mastery lights Goliath");
        helper.assertTrue(SynergyEffects.strengthOf(withFist, Synergies.GOLIATH)
                        == SynergyEffects.strengthOf(withoutFist, Synergies.GOLIATH),
                "a MAXED requirement gates the synergy without adding strength of its own, so raising "
                        + fist + " must not also make Goliath hit harder");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void aFamilyRequirementGatesWithoutWeakening(GameTestHelper helper) {

        String voidEnv = Concepts.ENV_VOID;
        PlayerAdaption alone = withLevel(voidEnv, PlayerAdaption.MAX_LEVEL);
        PlayerAdaption withBoss = withLevels(voidEnv, PlayerAdaption.MAX_LEVEL,
                Concepts.existence("minecraft:wither"), PlayerAdaption.MAX_LEVEL);

        helper.assertTrue(Synergies.satisfied(withBoss, Synergies.UNMAKER),
                "one boss satisfies the Existence_ family");
        helper.assertTrue(SynergyEffects.strengthOf(withBoss, Synergies.UNMAKER)
                        == SynergyEffects.strengthOf(alone, Synergies.UNMAKER),
                "the family member is a gate, not a level, so it must not dilute the average either");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void oneTimeMutationsAddNoStrengthOfTheirOwn(GameTestHelper helper) {

        String fire = Concepts.type(AdaptionCategory.FIRE);
        PlayerAdaption fireOnly = withLevel(fire, PlayerAdaption.MAX_LEVEL);
        PlayerAdaption fireAndThermal = withLevel(fire, PlayerAdaption.MAX_LEVEL);
        fireAndThermal.adapted.add(Concepts.MUTATION_THERMAL);
        fireAndThermal.invalidateAdaptCount();

        helper.assertTrue(Synergies.satisfied(fireAndThermal, Synergies.ASHWALKER),
                "fire plus Thermal Mastery lights Ashwalker");
        helper.assertTrue(SynergyEffects.strengthOf(fireAndThermal, Synergies.ASHWALKER)
                        == SynergyEffects.strengthOf(fireOnly, Synergies.ASHWALKER),
                "a one-time mutation has no level to average in, so it must not raise the strength");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void aSynergyWithNothingToAverageFallsBackToTheWheelBreadth(GameTestHelper helper) {

        String labor = Concepts.MINE_LABOR;
        String fistMutation = Concepts.MUTATION_FIST;
        PlayerAdaption narrow = empty();
        narrow.levels.put(labor, PlayerAdaption.MAX_LEVEL);
        narrow.adapted.add(fistMutation);
        narrow.invalidateAdaptCount();

        PlayerAdaption broad = empty();
        broad.levels.put(labor, PlayerAdaption.MAX_LEVEL);
        broad.adapted.add(fistMutation);
        for (int i = 0; i < 60; i++) {
            broad.adapted.add("Filler_" + i);
        }
        broad.invalidateAdaptCount();

        helper.assertTrue(Synergies.satisfied(narrow, Synergies.ASTRAL_MINE),
                "a maxed Mine_Labor plus the fist lights Astral Mine");

        double narrowStrength = SynergyEffects.strengthOf(narrow, Synergies.ASTRAL_MINE);
        double narrowExpected = 1.0 + 3.0 * (narrow.getAdaptCount() / 60.0);
        helper.assertTrue(Math.abs(narrowStrength - narrowExpected) < 1e-9,
                "with every requirement a gate rather than a level, the strength falls back to how much "
                        + "the wheel knows: " + narrow.getAdaptCount() + " adaptations is "
                        + narrowExpected + ", not " + narrowStrength);

        PlayerAdaption padded = empty();
        padded.adapted.add(fistMutation);
        padded.adapted.add(labor);
        for (int i = 0; i < 10; i++) {
            padded.adapted.add("Filler_" + i);
        }
        padded.invalidateAdaptCount();
        helper.assertTrue(SynergyEffects.strengthOf(padded, Synergies.ASTRAL_MINE)
                        > narrowStrength,
                "and ten unrelated adaptations elsewhere on the wheel still make it stronger, because "
                        + "the fallback counts the whole wheel rather than the synergy's own members");

        helper.assertTrue(Math.abs(SynergyEffects.strengthOf(broad, Synergies.ASTRAL_MINE) - 4.0) < 1e-9,
                "and 60 adaptations elsewhere on the wheel reach the 4.0 ceiling");
        helper.succeed();
    }
}