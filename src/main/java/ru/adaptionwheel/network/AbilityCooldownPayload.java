package ru.adaptionwheel.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import ru.adaptionwheel.AdaptionWheel;

/**
 * Server -&gt; owner: an ability just went on cooldown.
 *
 * <p>The cooldown lives on the server, so without this the HUD card would claim the ability is
 * ready while every press is refused — the exact shape of bug this mod keeps writing down. The
 * packet carries the id and the length rather than an absolute time, because the two sides do not
 * share a clock.
 */
public record AbilityCooldownPayload(String id, int ticks) implements CustomPacketPayload {

    public static final Type<AbilityCooldownPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AdaptionWheel.MODID, "ability_cooldown"));

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
