package ru.adaptionwheel.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.data.AdaptionTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record AdaptionSyncPayload(
        boolean wearingWheel,
        boolean adversityActive,
        int adaptedCount,
        int adversityTimer,
        int adversityCooldownTimer,
        float wheelRotation,
        List<AdaptionTask> tasks,
        Map<String, Integer> levels,
        List<String> adapted,
        List<String> history,
        Map<String, Integer> existenceProgress,
        int existenceThreshold,
        boolean instabreakActive,
        int fistProgressDone,
        int fistProgressTotal
) implements CustomPacketPayload {

    public static final Type<AdaptionSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AdaptionWheel.MODID, "adaption_sync"));

    public static final StreamCodec<FriendlyByteBuf, AdaptionSyncPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBoolean(p.wearingWheel);
                buf.writeBoolean(p.adversityActive);
                buf.writeVarInt(p.adaptedCount);
                buf.writeVarInt(p.adversityTimer);
                buf.writeVarInt(p.adversityCooldownTimer);
                buf.writeFloat(p.wheelRotation);
                buf.writeVarInt(p.tasks.size());
                for (AdaptionTask task : p.tasks) {
                    buf.writeUtf(task.concept);
                    buf.writeVarInt(task.timer);
                    buf.writeVarInt(task.maxTimer);
                }
                buf.writeVarInt(p.levels.size());
                p.levels.forEach((key, value) -> {
                    buf.writeUtf(key);
                    buf.writeVarInt(value);
                });
                buf.writeVarInt(p.adapted.size());
                for (String concept : p.adapted) {
                    buf.writeUtf(concept);
                }
                buf.writeVarInt(p.history.size());
                for (String entry : p.history) {
                    buf.writeUtf(entry);
                }

                buf.writeVarInt(p.existenceProgress.size());
                p.existenceProgress.forEach((key, value) -> {
                    buf.writeUtf(key);
                    buf.writeVarInt(value);
                });
                buf.writeVarInt(p.existenceThreshold);
                buf.writeBoolean(p.instabreakActive);
                buf.writeVarInt(p.fistProgressDone);
                buf.writeVarInt(p.fistProgressTotal);
            },
            buf -> {
                boolean wearing = buf.readBoolean();
                boolean adversity = buf.readBoolean();
                int count = buf.readVarInt();
                int advTimer = buf.readVarInt();
                int advCooldown = buf.readVarInt();
                float rotation = buf.readFloat();
                List<AdaptionTask> tasks = new ArrayList<>();
                int taskCount = buf.readVarInt();
                for (int i = 0; i < taskCount; i++) {
                    tasks.add(new AdaptionTask(buf.readUtf(), buf.readVarInt(), buf.readVarInt()));
                }
                Map<String, Integer> levels = new HashMap<>();
                int levelCount = buf.readVarInt();
                for (int i = 0; i < levelCount; i++) {
                    levels.put(buf.readUtf(), buf.readVarInt());
                }
                List<String> adapted = new ArrayList<>();
                int adaptedListSize = buf.readVarInt();
                for (int i = 0; i < adaptedListSize; i++) {
                    adapted.add(buf.readUtf());
                }
                List<String> history = new ArrayList<>();
                int historyCount = buf.readVarInt();
                for (int i = 0; i < historyCount; i++) {
                    history.add(buf.readUtf());
                }
                Map<String, Integer> existenceProgress = new HashMap<>();
                int epCount = buf.readVarInt();
                for (int i = 0; i < epCount; i++) {
                    existenceProgress.put(buf.readUtf(), buf.readVarInt());
                }
                int existenceThreshold = buf.readVarInt();
                boolean instabreakActive = buf.readBoolean();
                int fistProgressDone = buf.readVarInt();
                int fistProgressTotal = buf.readVarInt();
                return new AdaptionSyncPayload(wearing, adversity, count, advTimer, advCooldown,
                        rotation, tasks, levels, adapted, history, existenceProgress, existenceThreshold,
                        instabreakActive, fistProgressDone, fistProgressTotal);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void sendTo(ServerPlayer player, AdaptionSyncPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }
}
