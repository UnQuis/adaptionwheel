package ru.adaptionwheel.api.events;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import ru.adaptionwheel.data.PlayerAdaption;

/**
 * Fired on the NeoForge GAME event bus whenever the wheel completes an
 * adaptation for a wearer: a leveled concept reaching a new level
 * ({@code level} >= 1), or any one-time adaptation/mutation/existence grant
 * ({@code level} == -1).
 *
 * <p>This is an informational notification; it cannot be canceled. Third-party
 * mods can use it to react — e.g. award advancements, trigger custom effects,
 * or track statistics.</p>
 */
public class AdaptationCompleteEvent extends Event {

    private final ServerPlayer player;
    private final String concept;
    private final int level;

    public AdaptationCompleteEvent(ServerPlayer player, String concept, int level) {
        this.player = player;
        this.concept = concept;
        this.level = level;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    /** The completed concept key, e.g. {@code "Type_FIRE"} or {@code "Move_SoulSand"}. */
    public String getConcept() {
        return concept;
    }

    /** New level (1..8) for leveled concepts, or -1 for one-time adaptations. */
    public int getLevel() {
        return level;
    }

    /** Convenience: full runtime adaptation data of the wearer. */
    public PlayerAdaption getData() {
        return player.getData(ru.adaptionwheel.data.AttachmentTypes.ADAPTION);
    }

    /** Posts the event on the game bus. Internal use. */
    public static void post(ServerPlayer player, String concept, int level) {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new AdaptationCompleteEvent(player, concept, level));
    }
}
