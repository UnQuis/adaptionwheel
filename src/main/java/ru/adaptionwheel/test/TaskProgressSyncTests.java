package ru.adaptionwheel.test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.data.AdaptionTask;
import ru.adaptionwheel.data.Extras;
import ru.adaptionwheel.data.PlayerAdaption;
import ru.adaptionwheel.network.TaskProgressPayload;

/**
 * Pins that a running analysis can be moved faster than the 1 Hz full sync, which is the difference
 * between a progress bar that reacts to being hit and one that reacts a beat afterwards.
 *
 * <p>The countdown itself was never the problem: the client extrapolates it between syncs, so a bar
 * whose timer only ticks down already moves smoothly. The problem was the jumps. Accelerating an
 * existing task subtracts from its timer without telling anyone, while the branch above it -- the one
 * that starts a task -- syncs immediately. That asymmetry is the bug: the server moves the bar, the
 * client keeps extrapolating from a timer it was never told about, and the correction only arrives on
 * the next sync, so the visible response to damage is late and then arrives as a jump.
 *
 * <p>So the push is a separate, small packet on its own cadence, exactly like the fist's per-break
 * push. What matters here is that it exists, that it is cheap, and that it does not disturb the
 * adversity clock — which is smoothed from a different packet and would jump backwards if the two
 * shared one.
 */
@GameTestHolder(AdaptionWheel.MODID)
@PrefixGameTestTemplate(false)
public class TaskProgressSyncTests {

    /** 5 Hz: fast enough that nothing reads as late, and it only runs while something is analysed. */
    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void pushesOftenEnoughToNotReadAsLate(GameTestHelper helper) {

        helper.assertTrue(TaskProgressPayload.PUSH_EVERY_TICKS <= 5,
                "at " + TaskProgressPayload.PUSH_EVERY_TICKS + " ticks between pushes the bar can sit"
                        + " unchanged for a fifth of a second, which is the lag this replaces");
        helper.assertTrue(TaskProgressPayload.PUSH_EVERY_TICKS > 1,
                "pushing every tick would make the extrapolation pointless and cost a packet per frame"
                        + " of game time for nothing");
        helper.succeed();
    }

    /**
     * The payload has to survive the round trip it actually makes.
     *
     * <p>It is written by hand, and the concept keys are namespaced, so a missing null guard on the
     * read side would surface as a decode failure that drops the packet — which would look exactly
     * like the bug it is meant to fix.
     */
    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void payloadRoundTripsNamespacedKeys(GameTestHelper helper) {

        net.minecraft.network.FriendlyByteBuf buf =
                new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            List<AdaptionTask> tasks = List.of(
                    new AdaptionTask("Contact_minecraft:zombie", 37, 100),
                    new AdaptionTask("Existence_draconicevolution:draconic_guardian", 5, 20));
            java.util.Map<String, Integer> bosses = new HashMap<>();
            bosses.put("minecraft:wither", 240);

            TaskProgressPayload.STREAM_CODEC.encode(buf, new TaskProgressPayload(tasks, bosses));
            TaskProgressPayload decoded = TaskProgressPayload.STREAM_CODEC.decode(buf);

            helper.assertTrue(decoded.tasks().size() == 2,
                    "both tasks must survive, got " + decoded.tasks().size());
            helper.assertTrue(decoded.tasks().get(0).concept.equals("Contact_minecraft:zombie"),
                    "a namespaced concept key must survive verbatim, got "
                            + decoded.tasks().get(0).concept);
            helper.assertTrue(decoded.tasks().get(0).timer == 37,
                    "the timer must survive, got " + decoded.tasks().get(0).timer);
            helper.assertTrue(decoded.tasks().get(0).maxTimer == 100,
                    "maxTimer must survive, got " + decoded.tasks().get(0).maxTimer);
            helper.assertTrue(decoded.tasks().get(1).timer == 5 && decoded.tasks().get(1).maxTimer == 20,
                    "a second task must survive with its own timers");
            helper.assertTrue(decoded.existenceProgress().getOrDefault("minecraft:wither", 0) == 240,
                    "the boss counters ride along because they are extrapolated from the same clock,"
                            + " and advancing that clock without them would make the boss bar jump"
                            + " backwards; got " + decoded.existenceProgress());
        } finally {
            buf.release();
        }
        helper.succeed();
    }

    /**
     * An empty push must decode to empty, not to a phantom task.
     *
     * <p>The bar walks whatever is in the task list, so a decode that produced one bogus entry would
     * put a row on the HUD that no analysis owns.
     */
    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void anEmptyPushCarriesNothing(GameTestHelper helper) {

        net.minecraft.network.FriendlyByteBuf buf =
                new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            TaskProgressPayload.STREAM_CODEC.encode(buf,
                    new TaskProgressPayload(List.of(), java.util.Map.of()));
            TaskProgressPayload decoded = TaskProgressPayload.STREAM_CODEC.decode(buf);
            helper.assertTrue(decoded.tasks().isEmpty(),
                    "an empty push produced " + decoded.tasks().size() + " tasks");
            helper.assertTrue(decoded.existenceProgress().isEmpty(),
                    "an empty push produced " + decoded.existenceProgress().size() + " boss counters");
        } finally {
            buf.release();
        }
        helper.succeed();
    }

    /** Mutable on purpose, matching how the attachment is built at runtime. */
    private static PlayerAdaption mutable() {
        return new PlayerAdaption(new HashMap<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>(), new HashMap<>(), new HashMap<>(), 0, 0, 0, false, 0f, 0f, false, 0,
                Extras.EMPTY);
    }
}