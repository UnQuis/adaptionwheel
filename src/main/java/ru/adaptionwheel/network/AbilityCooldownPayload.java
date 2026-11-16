package ru.adaptionwheel.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import ru.adaptionwheel.AdaptionWheel;

/**
 * Server -&gt; owner: an ability just went on cooldown.
 */
public record AbilityCooldownPayload(String id, int ticks) implements CustomPacketPayload {

    public static final Type<AbilityCooldownPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(AdaptionWheel.MODID, "ability_cooldown"));

    public static final StreamCodec<FriendlyByteBuf, AbilityCooldownPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, AbilityCooldownPayload::id,
                    ByteBufCodecs.VAR_INT, AbilityCooldownPayload::ticks,
                    AbilityCooldownPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void send(ServerPlayer player, String id, int ticks) {
        if (ticks <= 0) {
            return;
        }
        PacketDistributor.sendToPlayer(player, new AbilityCooldownPayload(id, ticks));
    }

    public static void apply(AbilityCooldownPayload payload) {
        ru.adaptionwheel.client.ClientCooldowns.apply(payload);
    }
}
