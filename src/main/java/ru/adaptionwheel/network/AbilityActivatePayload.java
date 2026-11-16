package ru.adaptionwheel.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.server.AbilityDispatch;

/**
 * Client -&gt; server: the universal ability key was pressed.
 *
 * <p>No ability id is sent. One key drives the single slot, and the server decides which ability
 * that currently is from the player's persisted selection — otherwise a stale client mirror could
 * activate the wrong one. All gates (wheel worn, adaptation present and enabled, cooldown) stay in
 * the server handler.
 */
public record AbilityActivatePayload() implements CustomPacketPayload {

    public static final AbilityActivatePayload INSTANCE = new AbilityActivatePayload();

    public static final Type<AbilityActivatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AdaptionWheel.MODID, "ability_activate"));

    public static final StreamCodec<FriendlyByteBuf, AbilityActivatePayload> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void send() {
        PacketDistributor.sendToServer(INSTANCE);
    }

    public static void handle(AbilityActivatePayload payload, ServerPlayer player) {
        AbilityDispatch.activate(player);
    }
}
