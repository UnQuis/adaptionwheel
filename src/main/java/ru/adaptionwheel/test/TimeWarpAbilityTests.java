package ru.adaptionwheel.test;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameRules;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.ability.ActiveAbilities;
import ru.adaptionwheel.data.PlayerAdaption;
import ru.adaptionwheel.server.AbilityDispatch;
import ru.adaptionwheel.server.TimeWarpAbility;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;

@GameTestHolder(AdaptionWheel.MODID)
@PrefixGameTestTemplate(false)
public class TimeWarpAbilityTests {

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void theAbilityIsRegisteredAndUnlockedByDefault(GameTestHelper helper) {
        boolean found = false;
        for (var ability : ActiveAbilities.all()) {
            if (TimeWarpAbility.ID.equals(ability.id())) {
                found = true;
            }
        }
        helper.assertTrue(found, "time_warp must be a selectable active ability");
        helper.assertTrue(AbilityDispatch.isUnlocked(TimeWarpAbility.ID, new PlayerAdaption()),
                "the first version is unlocked by default; the adaptation gate replaces only this body");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void clockDecimationAndPartialStayInBand(GameTestHelper helper) {
        helper.assertTrue(TimeWarpAbility.advanceClock(0L, 0.2f) == 200L,
                "0.2x means 200 logical milliseconds per server tick");
        helper.assertTrue(TimeWarpAbility.slowTick(1000L), "crossing a whole second is a slow tick");
        helper.assertTrue(!TimeWarpAbility.slowTick(999L), "999 has not crossed it yet");

        long clock = 0L;
        for (int i = 0; i < 2000; i++) {
            clock = TimeWarpAbility.advanceClock(clock, 0.2f);
            for (float p = 0f; p <= 1.001f; p += 0.25f) {
                float value = TimeWarpAbility.partial(clock, 0.2f, p);
                helper.assertTrue(value >= 0f && value <= 1f,
                        "partialTick must stay in [0,1] for every logical clock and frame fraction");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void ownerAndEntitiesOutsideTheSphereAreNotDecimated(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        UUID owner = UUID.randomUUID();
        BlockPos centre = new BlockPos(0, 100, 0);

        Entity outside = EntityType.COW.create(level);
        outside.setPos(80.0, 100.0, 0.0);
        Entity inside = EntityType.COW.create(level);
        inside.setPos(2.0, 100.0, 0.0);

        long endsAt = level.getGameTime() + 2000L;
        TimeWarpAbility.activate(owner, level, centre, 4f, 0.2f, endsAt);
        // Drive one logical second so tickLento is true.
        TimeWarpAbility.onServerTick(new ServerTickEvent.Pre(() -> true, level.getServer()));
        for (int i = 0; i < 4; i++) {
            TimeWarpAbility.onServerTick(new ServerTickEvent.Pre(() -> true, level.getServer()));
        }

        helper.assertTrue(!TimeWarpAbility.pular(outside), "an entity outside the radius keeps every tick");
        helper.assertTrue(TimeWarpAbility.estadoEnvio(outside) == 0, "and gets no send-changes state");
        helper.assertTrue(!TimeWarpAbility.pular(ownerEntity(helper, owner)),
                "the owner is never decimated");
        helper.assertTrue(TimeWarpAbility.pular(inside), "an entity in the sphere loses the slow tick");
        TimeWarpAbility.clear(owner);
        helper.succeed();
    }

    /** A plain entity carrying the owner's UUID: no mock login, so Curios stays out of the test. */
    private static Entity ownerEntity(GameTestHelper helper, UUID owner) {
        Entity entity = EntityType.COW.create(helper.getLevel());
        entity.setUUID(owner);
        entity.setPos(1.0, 100.0, 0.0);
        return entity;
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void clearForgetsTheZoneAndItsCooldownState(GameTestHelper helper) throws Exception {
        ServerLevel level = helper.getLevel();
        UUID owner = UUID.randomUUID();
        TimeWarpAbility.activate(owner, level, new BlockPos(0, 100, 0), 8f, 0.2f,
                level.getGameTime() + 2000L);
        helper.assertTrue(TimeWarpAbility.isActive(owner), "activation installs a zone");

        Field ready = TimeWarpAbility.class.getDeclaredField("READY_AT");
        ready.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<UUID, Long> readyAt = (Map<UUID, Long>) ready.get(null);
        readyAt.put(owner, level.getGameTime() + 100L);
        helper.assertTrue(TimeWarpAbility.isOnCooldown(owner, level.getGameTime()),
                "a second start inside the cooldown is refused");

        TimeWarpAbility.clear(owner);
        helper.assertTrue(!TimeWarpAbility.isActive(owner), "clear removes the zone");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void daylightIsCompensatedFractionally(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT);
        level.setDayTime(0L);

        UUID owner = UUID.randomUUID();
        TimeWarpAbility.activate(owner, level, new BlockPos(0, 100, 0), 64f, 0.2f,
                level.getGameTime() + 2000L);
        for (int i = 0; i < 10; i++) {
            TimeWarpAbility.onServerTick(new ServerTickEvent.Pre(() -> true, level.getServer()));
            // Vanilla's own dayTime increment for this tick.
            if (level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)) {
                level.setDayTime(level.getDayTime() + 1L);
            }
        }
        helper.assertTrue(level.getDayTime() == 2L,
                "ten ticks at 0.2 must advance dayTime by 2, not 0 (skipped) or 10 (full speed); got "
                        + level.getDayTime());
        TimeWarpAbility.clear(owner);
        level.setDayTime(0L);
        helper.succeed();
    }
}
