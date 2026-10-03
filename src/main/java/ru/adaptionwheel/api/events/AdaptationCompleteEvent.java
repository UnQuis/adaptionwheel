package ru.adaptionwheel.api.events;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import ru.adaptionwheel.data.PlayerAdaption;

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

    public String getConcept() {
        return concept;
    }

    public int getLevel() {
        return level;
    }

    public PlayerAdaption getData() {
        return player.getData(ru.adaptionwheel.data.AttachmentTypes.ADAPTION);
    }

    public static void post(ServerPlayer player, String concept, int level) {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new AdaptationCompleteEvent(player, concept, level));
    }
}
