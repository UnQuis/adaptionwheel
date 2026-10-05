package ru.adaptionwheel.test;

import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import java.util.HashMap;
import java.util.List;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.data.Extras;
import ru.adaptionwheel.data.PlayerAdaption;

/**
 * Pins that earned progress survives a save/load cycle.
 *
 * <p>Reported as "Netherite fist 20/166 blocks, log out, log back in, 0/166". The cause was not a
 * lost save: the counter was never in one. Both fists counted into static UUID-keyed maps that
 * {@code forget()} clears on logout, so the number was correct for exactly one session and then
 * gone — while the level it was counting toward persisted, which is what makes it read as data loss
 * rather than as a resettable counter.
 *
 * <p>The distinction being pinned is that a derived value the player can only earn by mining 166
 * blocks is not "derived" in the sense that justifies throwing it away.
 */
@GameTestHolder(AdaptionWheel.MODID)
@PrefixGameTestTemplate(false)
public class ProgressPersistenceTests {

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void progressSurvivesACodecRoundTrip(GameTestHelper helper) {

        PlayerAdaption data = empty();
        data.progress.put(Extras.miningKey(4), 20);
        data.progress.put(Extras.punchingKey(2), 3);

        var encoded = PlayerAdaption.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
        PlayerAdaption reloaded = PlayerAdaption.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();

        helper.assertTrue(reloaded.progress.getOrDefault(Extras.miningKey(4), -1) == 20,
                "the mining fist's block count did not survive a save/load: "
                        + reloaded.progress + ". It must persist, because the level it counts toward"
                        + " persists -- a counter that resets while its target does not reads as"
                        + " data loss.");
        helper.assertTrue(reloaded.progress.getOrDefault(Extras.punchingKey(2), -1) == 3,
                "the punching fist's kill count did not survive a save/load: " + reloaded.progress);
        helper.succeed();
    }

    /** Both fists count against different keys, so one must never overwrite the other. */
    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void theTwoFistsCountSeparately(GameTestHelper helper) {

        helper.assertTrue(!Extras.miningKey(0).equals(Extras.punchingKey(0)),
                "the breaking and punching fists share a progress key at stage 0, so mining a block"
                        + " would advance the punch and vice versa");
        helper.assertTrue(!Extras.miningKey(1).equals(Extras.miningKey(0)),
                "mining tiers share a progress key");
        helper.assertTrue(!Extras.punchingKey(1).equals(Extras.punchingKey(0)),
                "punching stages share a progress key");
        helper.succeed();
    }

    /**
     * A save written before this field existed must still load.
     *
     * <p>Checked on {@link Extras#CODEC} rather than the whole attachment: optionality lives here,
     * and a hand-written literal for the full record is brittle in a way that fails for the wrong
     * reason -- an earlier attempt at exactly this test reported "No key adapted" for a missing
     * unrelated field, which says nothing about the field under test.
     */
    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void anOldSaveLoadsWithNoProgress(GameTestHelper helper) {

        DataResult<Extras> parsed = Extras.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"disabled\":[],\"cache\":[]}"));
        helper.assertTrue(parsed.result().isPresent(),
                "extras written before the progress field existed must still parse -- the field is"
                        + " optionalFieldOf precisely so old worlds keep loading. Got: " + parsed);
        helper.assertTrue(parsed.result().get().progress().isEmpty(),
                "an old save produced progress out of nothing: "
                        + parsed.result().get().progress()
                        + ", which would hand out a level nobody mined for");
        helper.succeed();
    }

    /** reset() must clear progress, or a wiped wheel would still be one block from a level. */
    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void resetClearsProgress(GameTestHelper helper) {

        PlayerAdaption data = empty();
        data.progress.put(Extras.miningKey(2), 17);
        data.reset();
        helper.assertTrue(data.progress.isEmpty(),
                "reset() left " + data.progress + " behind, so a wiped wheel would still be one"
                        + " block away from a level");
        helper.succeed();
    }

    private static PlayerAdaption empty() {
        return new PlayerAdaption(new HashMap<>(), List.of(), List.of(), List.of(), List.of(),
                new HashMap<>(), new HashMap<>(), 0, 0, 0, false, 0f, 0f, false, 0, Extras.EMPTY);
    }
}
