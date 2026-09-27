package ru.adaptionwheel.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.category.Concepts;
import ru.adaptionwheel.config.AdaptionConfig;

/**
 * {@code Env_Darkness} as a lift of the lightmap, i.e. what the fullbright mods do.
 *
 * <p><b>Why not the gamma option.</b> That was tried first on the 1.21.1 branch and did not work.
 * The brightness slider is bounded, and {@code OptionInstance.set} rejects an out-of-range value
 * and silently reverts to the initial 0.5, logging only "Illegal option value" — so a bounded lift
 * is at the mercy of a cap the mod does not control. It also clobbers a setting the player owns,
 * which then has to be remembered and restored.</p>
 *
 * <p><b>Why not the night vision effect.</b> That flickers, and the source of the flicker is
 * exact: {@code GameRenderer.getNightVisionScale} returns 1.0 only while the effect is
 * <em>not</em> within 200 ticks of ending, and otherwise oscillates as
 * {@code 0.7 + sin(remaining * PI * 0.2) * 0.3} — a full swing between 0.4 and 1.0 once per
 * window. Any rolling top-up scheme still has a window to cross, so the wearer sees a flash.</p>
 *
 * <p>This drives the lightmap on the frame the game already rebuilds it. There is no duration, no
 * packet and no oscillation to schedule: the result is a pure function of what the renderer
 * computed, so it is steady by construction.</p>
 */
@EventBusSubscriber(modid = AdaptionWheel.MODID, value = Dist.CLIENT)
public final class DarknessLightmap {

    /** True while the lift is in effect. */
    private static boolean active;
    /** Target brightness for the darkest areas, 0..1. */
    private static float floor;

    private DarknessLightmap() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        // Read the config and the mirror once a tick, not once per rebuild.
        boolean wanted = AdaptionConfig.DARKNESS_LIGHTMAP_ENABLED.get()
                && ClientAdaption.wearingWheel
                && ClientAdaption.ADAPTED.contains(Concepts.ENV_DARKNESS);
        if (!wanted) {
            active = false;
            return;
        }
        if (!active) {
            floor = AdaptionConfig.DARKNESS_LIGHTMAP_FLOOR.get().floatValue();
        }
        active = true;
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        // The player's own settings were never touched, but the flag must not survive into the
        // main menu, where there is no world to lift.
        active = false;
    }

    /**
     * The lightmap intensity for 26.3's GPU shader, or 0 for off.
     *
     * <p>26.3 moved the lightmap maths onto the GPU: {@code LightTexture} became {@code Lightmap}
     * plus {@code LightmapRenderStateExtractor}, the lightmap is a {@code GpuTexture} rather than
     * a {@code NativeImage}, and the arithmetic lives in
     * {@code assets/minecraft/shaders/core/lightmap.fsh}. There are no CPU-side per-pixel colours
     * to rewrite, so {@code mixin/LightmapRenderStateExtractorMixin} feeds this into
     * {@code nightVisionEffectIntensity} instead. The shader then does exactly what vanilla night
     * vision does — scale the lightmap toward {@code nightVisionColor}, which is white — so the
     * day/night cycle and the relative shading between a torch and the wall behind it survive, and
     * a target below 1.0 keeps the result from being blown out.</p>
     */
    public static float intensity() {
        return active ? floor : 0.0F;
    }

    /** True while the lift is in effect. Exposed for tests and the config screen. */
    public static boolean isActive() {
        return active;
    }
}
