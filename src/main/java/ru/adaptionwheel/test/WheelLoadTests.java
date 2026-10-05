package ru.adaptionwheel.test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.category.CombatFistTiers;
import ru.adaptionwheel.data.AdaptionTask;
import ru.adaptionwheel.data.Extras;
import ru.adaptionwheel.data.LegacyConcepts;
import ru.adaptionwheel.data.PlayerAdaption;
import ru.adaptionwheel.data.WheelData;

/**
 * Pins that a wheel's own data can be read back in and merged, which is the whole of what equipping
 * does -- and which used to throw.
 *
 * <p>{@code loadInto} used to begin with {@code LegacyConcepts.migrate(levels, adapted, history)},
 * which renames keys <i>in place</i>. A {@code WheelData} read off a Data Component is not required
 * to hold mutable collections, so the unconditional {@code levels.remove("Fist_Copper")} threw
 * {@code UnsupportedOperationException} out of {@code ImmutableMap.remove} on the equip tick and
 * killed the world. The player saw a crash the moment they put the wheel on, and because the wheel's
 * adaptations only ever live on the item until the periodic save runs, the crash also lost them.
 *
 * <p>The call was redundant anyway: everything it merged lands in {@code data}, which is migrated at
 * the end of {@code loadInto}. So the tests below check both halves -- that loading does not throw
 * for any wheel the game can produce, and that the migration still happens where it is supposed to.
 */
@GameTestHolder(AdaptionWheel.MODID)
@PrefixGameTestTemplate(false)
public class WheelLoadTests {

    /** The exact crash: a wheel read back through its codec, then equipped. */
    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void aDecodedWheelCanBeLoaded(GameTestHelper helper) {

        WheelData saved = WheelData.fromPlayer(populated());
        // Exactly what happens when the world loads a wheel from disk.
        var encoded = WheelData.CODEC.encodeStart(NbtOps.INSTANCE, saved)
                .getOrThrow(msg -> new IllegalStateException(msg));
        WheelData decoded = WheelData.CODEC.parse(NbtOps.INSTANCE, encoded)
                .getOrThrow(msg -> new IllegalStateException(msg));

        PlayerAdaption data = mutable();
        String thrown = null;
        try {
            decoded.loadInto(data);
        } catch (RuntimeException e) {
            thrown = e.getClass().getSimpleName() + ": " + e.getMessage();
        }
        helper.assertTrue(thrown == null,
                "equipping a wheel threw, which killed the world: " + thrown);

        helper.assertTrue(data.levels.getOrDefault("Type_Fire", 0) == 5,
                "the wheel's levels must reach the player, got " + data.levels);
        helper.assertTrue(data.adapted.contains("Env_Lava"),
                "one-time adaptations must reach the player, got " + data.adapted);
        helper.succeed();
    }

    /**
     * {@link WheelData#EMPTY} is stored on the stack by the wipe paths, and it is built from
     * {@code Map.of()} / {@code List.of()}, so it is immutable by construction. It is the one wheel
     * the mod creates that is <i>known</i> to be unmodifiable, so it is the one that has to work.
     */
    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void theEmptyWheelCanBeLoaded(GameTestHelper helper) {

        PlayerAdaption data = mutable();
        String thrown = null;
        try {
            WheelData.EMPTY.loadInto(data);
        } catch (RuntimeException e) {
            thrown = e.getClass().getSimpleName() + ": " + e.getMessage();
        }
        helper.assertTrue(thrown == null,
                "the empty wheel is immutable by construction and loadInto must not mutate it: "
                        + thrown);
        helper.succeed();
    }

    /**
     * Loading must not overwrite what the player already had.
     *
     * <p>A replace rather than a merge is what silently destroyed an adaptation granted while the
     * wheel was off: it lived only in the attachment, the equip transition wiped the attachment and
     * copied the item's data over it, and the grant was gone with nothing logged.
     */
    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void loadingTakesTheHigherLevelAndKeepsUnrelatedGrants(GameTestHelper helper) {

        PlayerAdaption data = mutable();
        data.levels.put("Type_Fire", 7);
        data.adapted.add("Type_Starve");
        data.progress.put("punch:0", 3);

        WheelData wd = WheelData.fromPlayer(populated());
        wd.loadInto(data);

        helper.assertTrue(data.levels.get("Type_Fire") == 7,
                "a hand-over must not walk a concept backwards: the item had 5, the player had 7,"
                        + " got " + data.levels.get("Type_Fire"));
        helper.assertTrue(data.adapted.contains("Type_Starve"),
                "an adaptation granted while the wheel was off must survive the equip");
        helper.assertTrue(data.progress.getOrDefault("punch:0", 0) == 3,
                "the fist's kill counter must survive the equip, it is persisted progress");
        helper.succeed();
    }

    /**
     * The migration still has to happen -- on the player's own data, where the merge landed it.
     *
     * <p>This is the half that would have broken silently if the removed line had been the only
     * thing doing it: a legacy {@code Fist_Copper} key must arrive at the player already renamed,
     * because the merge copies keys verbatim.
     */
    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void legacyKeysAreRenamedOnTheWayIn(GameTestHelper helper) {

        Map<String, Integer> levels = new HashMap<>();
        levels.put("Fist_Copper", 3);
        levels.put("Combat_FistDamage", 4);

        PlayerAdaption data = mutable();
        data.levels.putAll(levels);
        data.adapted.add("Debuff_poison");
        LegacyConcepts.migrate(data);

        helper.assertTrue(!data.levels.containsKey("Fist_Copper"),
                "there is no copper tier; the key must not survive the migration");
        helper.assertTrue(data.levels.getOrDefault(ru.adaptionwheel.category.FistTiers.concept(2), 0) == 3,
                "copper folds into iron, got " + data.levels);
        helper.assertTrue(!data.levels.containsKey("Combat_FistDamage"),
                "the flat punching fist is now a five-stage ladder, got " + data.levels);
        helper.assertTrue(data.levels.getOrDefault(CombatFistTiers.concept(0), 0) == 4,
                "the flat punching fist folds into the first stage, got " + data.levels);
        helper.succeed();
    }

    /** Mutable on purpose: {@code loadInto} and {@code migrate} both write into the player data. */
    private static PlayerAdaption mutable() {
        return new PlayerAdaption(new HashMap<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>(), new HashMap<>(), new HashMap<>(), 0, 0, 0, false, 0f, 0f, false, 0,
                Extras.EMPTY);
    }

    private static PlayerAdaption populated() {
        PlayerAdaption data = mutable();
        data.levels.put("Type_Fire", 5);
        data.adapted.add("Env_Lava");
        data.existenceAdapted.add("Existence_minecraft:wither");
        data.killCounts.put("Drop_NPC_minecraft:zombie", 12);
        data.history.add("first");
        data.tasks.add(new AdaptionTask("Env_Drowning", 40, 100));
        return data;
    }
}