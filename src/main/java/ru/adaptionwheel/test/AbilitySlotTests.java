package ru.adaptionwheel.test;

import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.data.PlayerAdaption;
import ru.adaptionwheel.network.AdaptionSyncPayload;
import ru.adaptionwheel.server.AbilityDispatch;
import ru.adaptionwheel.server.DiveAbility;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Pins the single ability slot.
 *
 * <p>The selection used to be client-local state and the R key was wired straight to Dive. That
 * cannot survive a second ability: the HUD would advertise one thing while the server ran another,
 * and the pick would be lost on every rejoin. The chosen id now lives in the attachment, travels in
 * the sync payload and is validated by the same predicate that decides what the player may use.
 */
@GameTestHolder(AdaptionWheel.MODID)
@PrefixGameTestTemplate(false)
public class AbilitySlotTests {

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void theChosenAbilitySurvivesASave(GameTestHelper helper) {
        PlayerAdaption data = new PlayerAdaption();
        data.selectAbility(DiveAbility.ID);

        var encoded = PlayerAdaption.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, data)
                .getOrThrow();
        PlayerAdaption reloaded = PlayerAdaption.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, encoded)
                .getOrThrow();

        helper.assertTrue(DiveAbility.ID.equals(reloaded.selectedAbility()),
                "the chosen ability was lost on a save/load: the HUD would fall back to a different"
                        + " ability than the one the player picked");
        helper.succeed();
    }

    /** An old save has no selected_ability key; it must come back empty, never as a random id. */
    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void anOldSaveHasNoSelection(GameTestHelper helper) {
        var parsed = PlayerAdaption.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                JsonParser.parseString("{\"levels\":{},\"adapted\":[],\"tasks\":[],\"history\":[],"
                        + "\"existence\":[],\"healingTimer\":0,\"adversityCooldown\":0,"
                        + "\"adversityTimer\":0,\"adversityActive\":false,\"targetRotation\":0,"
                        + "\"wheelRotation\":0}"));
        helper.assertTrue(parsed.result().isPresent(), "an old attachment must still parse");
        helper.assertTrue(parsed.result().get().selectedAbility().isEmpty(),
                "a save written before the slot existed must come back with no selection, not"
                        + " with an id that happens to match an ability");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void wipesClearTheSlotButUnequipsKeepIt(GameTestHelper helper) {
        PlayerAdaption data = new PlayerAdaption();
        data.selectAbility(DiveAbility.ID);
        data.resetForUnequip();
        helper.assertTrue(DiveAbility.ID.equals(data.selectedAbility()),
                "taking the wheel off must not forget which ability the player chose");

        data.reset();
        helper.assertTrue(data.selectedAbility().isEmpty(),
                "an explicit reset must clear the slot, or a wiped wheel still knows an ability");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void theSelectionSurvivesTheSyncCodec(GameTestHelper helper) {
        AdaptionSyncPayload original = new AdaptionSyncPayload(
                true, false, 0, 0, 0, 0f,
                new ArrayList<>(), new HashMap<>(), new ArrayList<>(), new ArrayList<>(),
                new HashMap<>(), 100, false, false, 0, 0, 0, 0,
                new ArrayList<>(List.of(DiveAbility.ID)), new ArrayList<>(List.of(DiveAbility.ID)),
                DiveAbility.ID);

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        AdaptionSyncPayload.STREAM_CODEC.encode(buf, original);
        AdaptionSyncPayload decoded = AdaptionSyncPayload.STREAM_CODEC.decode(buf);

        helper.assertTrue(DiveAbility.ID.equals(decoded.selectedAbility()),
                "the selected ability did not survive the sync: the HUD would draw Dive's card"
                        + " while the key activated something else");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void anUnknownIdNeverWinsTheSlot(GameTestHelper helper) {
        PlayerAdaption data = new PlayerAdaption();
        data.selectAbility("not_a_registered_ability");
        helper.assertTrue(!AbilityDispatch.isUnlocked("not_a_registered_ability", data),
                "a client-sent ability id that is not registered must never validate");
        String resolved = AbilityDispatch.resolveSelected(data);
        helper.assertTrue(resolved != null && !resolved.equals("not_a_registered_ability"),
                "an unknown id must never become the fallback; Time Warp is default-unlocked, "
                        + "so a real ability resolving here is expected");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void thePersistedChoiceWinsOverTheFallback(GameTestHelper helper) {
        String id = "aw_test_slot_ability";
        AtomicInteger activations = new AtomicInteger();
        AbilityDispatch.register(id, player -> activations.incrementAndGet(), data -> true);

        PlayerAdaption data = new PlayerAdaption();
        data.selectAbility(id);
        String resolved = AbilityDispatch.resolveSelected(data);
        helper.assertTrue(id.equals(resolved),
                "the persisted selection was ignored: " + resolved);
        helper.succeed();
    }
}
