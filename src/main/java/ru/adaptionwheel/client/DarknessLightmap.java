package ru.adaptionwheel.client;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.category.Concepts;
import ru.adaptionwheel.config.AdaptionConfig;

/**
 * {@code Env_Darkness} as a lift of the light map, i.e. what the fullbright mods do.
 *
 * <p><b>Why not the gamma option.</b> That was tried first and did not work. The brightness
 * slider is bounded, {@code OptionInstance.set} rejects an out-of-range value and silently reverts
 * to the initial 0.5 (logging only "Illegal option value"), so a bounded lift is at the mercy of a
 * cap the mod does not control. It also clobbers a setting the player owns, which then has to be
 * remembered and restored.</p>
 *
 * <p><b>Why not the night vision effect.</b> That flickers, and the source of the flicker is
 * exact: {@code GameRenderer.getNightVisionScale} returns {@code 1.0} only while the effect is
 * <em>not</em> within 200 ticks of ending, and otherwise oscillates as
 * {@code 0.7 + sin(remaining * PI * 0.2) * 0.3} — a full swing between 0.4 and 1.0 once per window.
 * Any rolling top-up scheme still has a window to cross, so the wearer sees a periodic flash.</p>
 *
 * <p>This writes the light map directly, through {@code mixin/LightTextureMixin}, on the frame the
 * game already rebuilds it. There is no duration, no packet and no oscillation to schedule: the
 * result is a pure function of the light level vanilla computed, so it is steady by construction.</p>
 */
@EventBusSubscriber(modid = AdaptionWheel.MODID, value = Dist.CLIENT)
public final class DarknessLightmap {

    /** True while the lift is in effect. Read by the mixin on every lightmap pixel. */
    private static boolean active;
    /** Brightest channel the lift raises a pixel to, 0..1. */
    private static float floor;
    /** 255-scale form of {@link #floor}, precomputed because it is read per pixel. */
    private static int floorMax = 182;

    private DarknessLightmap() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        // Read the config and the mirror once a tick, not once per pixel: the light map is 16x16
        // and is rebuilt every client tick, so a config read per pixel would be 256 lookups a tick.
        boolean wanted = AdaptionConfig.DARKNESS_LIGHTMAP_ENABLED.get()
                && ClientAdaption.wearingWheel
                && ClientAdaption.ADAPTED.contains(Concepts.ENV_DARKNESS);
        if (!wanted) {
            active = false;
            return;
        }
        if (!active) {
            floor = AdaptionConfig.DARKNESS_LIGHTMAP_FLOOR.get().floatValue();
            floorMax = Math.max(0, Math.min(255, Math.round(floor * 255.0F)));
        }
        active = true;
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        // Nothing to put back any more — the player's own settings were never touched — but the
        // flag must not survive into the main menu, where there is no world to lift.
        active = false;
    }

    /**
     * Raises one lightmap pixel to {@link #floor} if it is darker, leaving anything already
     * brighter alone.
     *
     * <p>Copied from what vanilla's own night vision does to the same pixels — it normalises so the
     * brightest channel reaches the top of the range — with the target pulled down to
     * {@code floor} instead of 1.0 so the result is legible without being blown out. Scaling by the
     * brightest channel rather than writing a flat white keeps the relative shading, so a torch
     * still reads as brighter than the cave wall behind it, and the day/night cycle still
     * shows through in the sky half of the map.</p>
     */
    public static int lift(int argb) {
        if (!active || floorMax <= 0) {
            return argb;
        }
        int a = argb >>> 24 & 0xFF;
        int r = argb >> 16 & 0xFF;
        int g = argb >> 8 & 0xFF;
        int b = argb & 0xFF;
        int max = Math.max(r, Math.max(g, b));
        if (max >= floorMax) {
            return argb;
        }
        if (max == 0) {
            // Pure black carries no hue to preserve, so it becomes a flat neutral floor.
            return a << 24 | floorMax << 16 | floorMax << 8 | floorMax;
        }
        // Float, not integer multiply: a 0..255 * 0..255 product overflows an int and would wrap
        // to a dark or wrong colour on exactly the dimmest pixels this is meant to fix.
        float scale = (float) floorMax / max;
        int nr = Math.min(255, Math.round(r * scale));
        int ng = Math.min(255, Math.round(g * scale));
        int nb = Math.min(255, Math.round(b * scale));
        return a << 24 | nr << 16 | ng << 8 | nb;
    }

    /** True while the lift is in effect. Exposed for tests and for the config screen. */
    public static boolean isActive() {
        return active;
    }
}
