package ru.adaptionwheel.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.category.Concepts;
import ru.adaptionwheel.config.AdaptionConfig;

/**
 * {@code Env_Darkness} as a brightness lift, applied client-side by raising the gamma option.
 *
 * <p>Replaces granting the night vision status effect, which was wrong in a way that could not be
 * tuned away. The effect has a duration, so it has to be refreshed as it runs down, and the
 * refresh means the screen visibly blinks out and back once per window — the wearer sees a
 * periodic flash in a cave, and the last seconds before each top-up are the worst. Gamma has no
 * duration and no packet, so it is simply on or off. {@code LightTexture.tick()} rebuilds the
 * light map every client tick, so a change lands on the very next frame.</p>
 *
 * <p>Also the reason the effect stopped being used: night vision shows a potion icon and swirl
 * unless it is marked ambient, and it fights with the player's own night vision potion.</p>
 *
 * <p>The player's own brightness setting is remembered the first time it is overridden and put
 * back when the adaptation is gone, on logout, and on client shutdown. Without that, quitting
 * with the adaptation active would leave {@code gamma=1.0} in {@code options.txt} for good.</p>
 */
@EventBusSubscriber(modid = AdaptionWheel.MODID, value = Dist.CLIENT)
public final class DarknessGamma {

    private static boolean active;
    private static Double playerGamma;

    private DarknessGamma() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!AdaptionConfig.DARKNESS_GAMMA_ENABLED.get()) {
            restore();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            restore();
            return;
        }
        // ClientAdaption mirrors the server's adapted set, so the decision needs no packets and
        // cannot disagree with the server.
        if (ClientAdaption.ADAPTED.contains(Concepts.ENV_DARKNESS)) {
            apply(mc.options, AdaptionConfig.DARKNESS_GAMMA.get());
        } else {
            restore(mc.options);
        }
    }

    /**
     * Leaving a world always goes through a disconnect, so this covers quitting from in-game too.
     * NeoForge 21.1 has no client-stopping event; without this the override would survive into
     * the main menu, and {@code options.txt} would keep gamma 1.0 for good.
     */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        restore();
    }

    private static void apply(Options options, double target) {
        if (active && options.gamma().get() == target) {
            return;
        }
        if (!active) {
            // Remember the player's setting once, so putting it back later restores what they
            // actually chose rather than a hardcoded default.
            playerGamma = options.gamma().get();
            active = true;
        }
        options.gamma().set(target);
    }

    private static void restore(Options options) {
        if (active && playerGamma != null) {
            options.gamma().set(playerGamma);
        }
        active = false;
        playerGamma = null;
    }

    private static void restore() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options != null) {
            restore(mc.options);
        } else {
            active = false;
            playerGamma = null;
        }
    }

    /** True while the gamma override is in effect; used by tests and the config screen. */
    public static boolean isActive() {
        return active;
    }
}
