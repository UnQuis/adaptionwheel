package ru.adaptionwheel.test;

import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.category.Concepts;
import ru.adaptionwheel.data.PlayerAdaption;
import ru.adaptionwheel.network.AdaptionSyncPayload;
import ru.adaptionwheel.server.DiveAbility;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pins the Dive gate and the one list the HUD draws from.
 *
 * <p>Two reports met here. The ability card appeared for a player who could not use the ability, and
 * the only way to get that wrong is for two different pieces of code to decide who has it: the
 * client used to re-derive availability from its own lagging mirror of the adaptation sets, while
 * the server checked the attachment. {@link DiveAbility#unlocked} is now the single definition, and
 * the value travels to the client inside the sync payload instead of being recomputed there.
 *
 * <p>The second half — that only {@code active} counts, not {@code isAdapted} — is the off switch.
 * A switched-off fall adaptation must close the gate exactly as a missing one does, or the panel
 * shows an ability the player has explicitly turned off.
 */
@GameTestHolder(AdaptionWheel.MODID)
@PrefixGameTestTemplate(false)
public class DiveGateTests {

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void nothingAdaptedMeansNoDive(GameTestHelper helper) {
        PlayerAdaption data = new PlayerAdaption();
        helper.assertTrue(!DiveAbility.unlocked(data),
                "a wheel with no fall and no flight adaptation must not unlock Dive");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void eitherOfTheTwoAdaptationsIsEnough(GameTestHelper helper) {
        PlayerAdaption fall = new PlayerAdaption();
        fall.adapted.add(Concepts.ENV_FALL);
        helper.assertTrue(DiveAbility.unlocked(fall), "Env_FallDamage alone must unlock Dive");

        PlayerAdaption flight = new PlayerAdaption();
        flight.adapted.add(Concepts.MUTATION_FLIGHT);
        helper.assertTrue(DiveAbility.unlocked(flight), "Mutation_Flight alone must unlock Dive");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void switchingTheAdaptationOffClosesTheGate(GameTestHelper helper) {
        PlayerAdaption data = new PlayerAdaption();
        data.adapted.add(Concepts.ENV_FALL);
        helper.assertTrue(DiveAbility.unlocked(data), "test setup: the gate must be open first");

        data.toggleEnabled(Concepts.ENV_FALL);

        helper.assertTrue(data.isAdapted(Concepts.ENV_FALL),
                "switching off must not forget the adaptation itself");
        helper.assertTrue(!DiveAbility.unlocked(data),
                "a switched-off fall adaptation still unlocked Dive, which is the ability card "
                        + "appearing for an adaptation the player turned off");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void theAbilityListSurvivesTheSyncCodec(GameTestHelper helper) {
        AdaptionSyncPayload original = new AdaptionSyncPayload(
                true, false, 3, 0, 0, 0f,
                new ArrayList<>(), new HashMap<>(), new ArrayList<>(), new ArrayList<>(),
                new HashMap<>(), 100, false, false, 0, 0, 0, 0,
                new ArrayList<>(List.of(Concepts.MUTATION_FLIGHT)),
                new ArrayList<>(List.of(DiveAbility.ID)));

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        AdaptionSyncPayload.STREAM_CODEC.encode(buf, original);
        AdaptionSyncPayload decoded = AdaptionSyncPayload.STREAM_CODEC.decode(buf);

        helper.assertTrue(decoded.abilities().equals(List.of(DiveAbility.ID)),
                "the synced ability list did not survive the codec: " + decoded.abilities());
        helper.assertTrue(decoded.disabled().equals(List.of(Concepts.MUTATION_FLIGHT)),
                "the disabled list did not survive alongside it: " + decoded.disabled());
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void anEmptyAbilityListIsAnEmptyHud(GameTestHelper helper) {
        Map<String, Integer> levels = new HashMap<>();
        AdaptionSyncPayload decoded = new AdaptionSyncPayload(
                true, false, 0, 0, 0, 0f,
                new ArrayList<>(), levels, new ArrayList<>(), new ArrayList<>(),
                new HashMap<>(), 100, false, false, 0, 0, 0, 0,
                new ArrayList<>(), new ArrayList<>());

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        AdaptionSyncPayload.STREAM_CODEC.encode(buf, decoded);
        AdaptionSyncPayload roundTripped = AdaptionSyncPayload.STREAM_CODEC.decode(buf);

        helper.assertTrue(roundTripped.abilities().isEmpty(),
                "an empty ability list came back non-empty, which would draw a card the server never "
                        + "unlocked");
        helper.succeed();
    }
}
