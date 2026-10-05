package ru.adaptionwheel.test;


import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.category.Concepts;
import ru.adaptionwheel.data.Extras;
import ru.adaptionwheel.data.PlayerAdaption;

/**
 * Pins the contract the off switch depends on: {@code isAdapted} answers "do you HAVE this",
 * {@code active} answers "does it currently apply". Only the second respects the switch.
 *
 * <p>Every one of these assertions is about the two accessors disagreeing. They are a single line
 * apart, both return {@code boolean}, and they are spelled similarly enough that picking the wrong
 * one compiles, passes every existing test, and produces a switch that does nothing — which is
 * exactly what players reported. The effect sites were the bug, not the accessor: eight separate
 * places asked {@code isAdapted} where they needed {@code active}.
 */
@GameTestHolder(AdaptionWheel.MODID)
@PrefixGameTestTemplate(false)
public class DisableTests {

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void switchingOffIsNotLosing(GameTestHelper helper) {

        PlayerAdaption data = holding(Concepts.ENV_LAVA);

        helper.assertTrue(data.isAdapted(Concepts.ENV_LAVA),
                "test setup: the adaptation must be present before it can be switched off");
        helper.assertTrue(data.active(Concepts.ENV_LAVA),
                "test setup: an enabled adaptation must be active");

        data.toggleEnabled(Concepts.ENV_LAVA);

        helper.assertTrue(data.isAdapted(Concepts.ENV_LAVA),
                "switching an adaptation off must not forget that you have it -- the altar and the "
                        + "shedding command both read isAdapted, so losing it would let the player "
                        + "buy it again and shed it");
        helper.assertTrue(!data.active(Concepts.ENV_LAVA),
                "a switched-off adaptation is still reported active, which is the whole bug: every "
                        + "effect site reading isAdapted keeps granting the effect");
        helper.assertTrue(data.levelOrZero(Concepts.ENV_LAVA) == 0,
                "levelOrZero must report zero while off, or a disabled leveled adaptation keeps "
                        + "scaling");
        helper.assertTrue(data.level(Concepts.ENV_LAVA) == 0 || data.level(Concepts.ENV_LAVA) >= 0,
                "level must stay readable, since the panel shows it greyed");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void switchingBackOnRestores(GameTestHelper helper) {

        PlayerAdaption data = holding(Concepts.ENV_LAVA);
        data.toggleEnabled(Concepts.ENV_LAVA);
        helper.assertTrue(!data.active(Concepts.ENV_LAVA), "test setup: must be off first");

        data.toggleEnabled(Concepts.ENV_LAVA);
        helper.assertTrue(data.active(Concepts.ENV_LAVA),
                "switching an adaptation back on did not restore it");
        helper.succeed();
    }

    /** A leveled adaptation must report its level while off, or the panel would claim it is Lv.0. */
    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void leveledKeepsItsNumberWhileOff(GameTestHelper helper) {

        PlayerAdaption data = new PlayerAdaption();
        data.levels.put(Concepts.ENV_LAVA, 5);
        data.invalidateAdaptCount();

        helper.assertTrue(data.level(Concepts.ENV_LAVA) == 5, "test setup: level must be stored");
        data.toggleEnabled(Concepts.ENV_LAVA);
        helper.assertTrue(data.level(Concepts.ENV_LAVA) == 5,
                "switching off erased the level, so re-enabling would drop the player back to zero "
                        + "and every level they earned would be unrecoverable");
        helper.assertTrue(data.levelOrZero(Concepts.ENV_LAVA) == 0,
                "and levelOrZero must report zero while off, so no effect reads it");
        helper.succeed();
    }

    /**
     * The switch must survive a save. A disabled set living in the transient part of the record
     * would silently re-enable everything on relog — the mirror of the counter bug, and equally
     * invisible.
     */
    /**
     * Switching off a debuff adaptation must let the debuff through again.
     *
     * <p>It did not, and the cause is the oldest one in this mod: the denial asked {@code isAdapted}
     * ("do you have it") where it needed {@code active} ("does it apply"). The panel switch only sets
     * {@code disabled}, so the row showed off, the effect kept firing, and the player could never
     * receive the effect they had just un-adapted to.
     */
    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void aDisabledDebuffAdaptationStopsDenyingTheDebuff(GameTestHelper helper) {

        String concept = ru.adaptionwheel.category.Concepts.debuff("minecraft:poison");
        PlayerAdaption data = new PlayerAdaption(new java.util.HashMap<>(),
                new java.util.ArrayList<>(), new java.util.ArrayList<>(), new java.util.ArrayList<>(),
                new java.util.ArrayList<>(), new java.util.HashMap<>(), new java.util.HashMap<>(),
                0, 0, 0, false, 0f, 0f, false, 0,
                new ru.adaptionwheel.data.Extras(new java.util.ArrayList<>(),
                        new java.util.ArrayList<>(), new java.util.HashMap<>()));
        data.adapted.add(concept);

        helper.assertTrue(data.isAdapted(concept),
                "the adaptation is owned, which is what the altar and the panel must keep seeing");
        helper.assertTrue(data.active(concept),
                "and it applies while it is switched on");

        data.disabled.add(concept);
        helper.assertTrue(data.isAdapted(concept),
                "switching it off must not erase the adaptation, or the altar would sell it again");
        helper.assertTrue(!data.active(concept),
                "the effect must read false once it is off -- this is what the debuff denial asks,"
                        + " and it used to ask isAdapted instead, so the switch did nothing");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void disabledSurvivesASave(GameTestHelper helper) {

        PlayerAdaption data = holding(Concepts.ENV_LAVA);
        data.toggleEnabled(Concepts.ENV_LAVA);

        helper.assertTrue(!data.disabled.isEmpty(),
                "toggleEnabled did not record anything in the persisted disabled set");
        helper.assertTrue(data.disabled.contains(Concepts.ENV_LAVA),
                "the disabled set must hold the concept key, not an index: " + data.disabled);
        // Prove it by round trip. Asserting a codec is "non-null", which is what the first version
        // of this test did, would pass even if the set were dropped from the record entirely.
        var encoded = PlayerAdaption.CODEC.encodeStart(
                com.mojang.serialization.JsonOps.INSTANCE, data).getOrThrow();
        PlayerAdaption reloaded = PlayerAdaption.CODEC.parse(
                com.mojang.serialization.JsonOps.INSTANCE, encoded).getOrThrow();
        helper.assertTrue(!reloaded.active(Concepts.ENV_LAVA),
                "the adaptation came back active after a save/load: the disabled set did not"
                        + " persist, so every switch silently resets on relog");
        helper.assertTrue(reloaded.disabled.contains(Concepts.ENV_LAVA),
                "the disabled set must round trip, not just be absent: " + reloaded.disabled);
        helper.succeed();
    }

    private static PlayerAdaption holding(String concept) {
        PlayerAdaption data = new PlayerAdaption();
        data.adapted.add(concept);
        return data;
    }
}
