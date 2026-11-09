package ru.adaptionwheel.config;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ru.adaptionwheel.AdaptionWheel;

/**
 * One-time corrections for values already written into a config file.
 *
 * <p>This exists because <b>changing a default is not a migration</b>. NeoForge's config tracker
 * writes the default only into a file that lacks a value, and keeps whatever is already stored, so
 * an install that generated its file while a now-wrong default was current carries that wrong value
 * forward forever and is entirely unaffected by any later edit to the default in code.
 *
 * <p>{@code lv8IFramesTicks} is the case in point. It shipped as 40 ticks and was corrected to 10
 * when it was found that a window at or past a melee attack interval re-arms before it lapses, which
 * makes the Lv8 defence capstone permanent immunity to that damage category rather than a reduction.
 * Every install that wrote its config in between kept granting exactly the behaviour that had been
 * reported as a bug, with nothing in the log to explain it. The config was not wrong; it was simply
 * older than the fix.
 *
 * <p>Runs on {@link ServerStartedEvent} rather than a config event, and is guarded so it happens
 * <b>exactly once</b>. Both are deliberate:
 *
 * <ul>
 *   <li>the guard is a stored flag rather than "is the value 40", because 40 is a value the config
 *       comment explicitly offers a player who wants the original's exact two seconds. Rewriting it
 *       on every start would take that choice away permanently;
 *   <li>server start rather than config load, so nothing here depends on when in the config
 *       lifecycle it runs.
 * </ul>
 */
@EventBusSubscriber(modid = AdaptionWheel.MODID)
public final class ConfigMigration {

    private static final Logger LOGGER = LoggerFactory.getLogger("adaptionwheel/config_migration");

    private ConfigMigration() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        // AltarOfferings.onServerStarted also runs on this event and reads the config, so by the
        // time either of us is called the spec is definitely loaded and registered.
        var value = AdaptionConfig.DEFENSE_LV8_IFRAMES_TICKS;
        var done = AdaptionConfig.LV8_IFRAMES_MIGRATION_DONE;
        if (value == null || done == null || done.get()) {
            migrateFullImmunityOff();
            return;
        }
        int stored = value.get();
        int corrected = AdaptionConfig.migrateLegacyLv8IFrames(stored);
        done.set(true);
        if (corrected == stored) {
            done.save();
            migrateFullImmunityOff();
            return;
        }
        value.set(corrected);
        value.save();
        done.save();
        LOGGER.warn(
                "lv8IFramesTicks was {}, the value shipped before it was found to outlast a melee"
                        + " attack interval. Corrected to {} in your config file. Set it yourself if"
                        + " you want a different window, or 0 to disable the Lv8 i-frames entirely.",
                stored, corrected);
        migrateFullImmunityOff();
    }

    /**
     * Turns off the maxed-immunity capstone once for configs written while it was the default.
     *
     * <p>It shipped on and had to be withdrawn: a completed {@code Type_MOB} made the wearer
     * absolutely immune to every mob, which in turn froze every {@code Contact_<mob>} ladder --
     * the very analysis that is supposed to be learned by being hit. The always-on exceptions
     * ({@code Contact_}, {@code PROJECTILE}, {@code OTHER}) live in code, so this only needs to
     * correct the stored value; a player who wants the old behaviour can set it back to true and
     * the flag keeps this from touching it again.
     */
    private static void migrateFullImmunityOff() {
        var immunity = AdaptionConfig.FULL_IMMUNITY_AT_MAX;
        var done = AdaptionConfig.FULL_IMMUNITY_MIGRATION_DONE;
        if (immunity == null || done == null || done.get() || !immunity.get()) {
            return;
        }
        immunity.set(false);
        immunity.save();
        done.set(true);
        done.save();
        LOGGER.warn(
                "fullImmunityAtMax was true, the value shipped before a maxed Mob Attacks was found"
                        + " to make the wearer absolutely immune to every mob, which froze the"
                        + " Contact_<mob> ladders. Corrected to false in your config file: level 8"
                        + " now ends at the reduction table (60 %) plus the short Lv8 i-frames. Set"
                        + " fullImmunityAtMax back to true yourself if you want the old capstone.");
    }
}