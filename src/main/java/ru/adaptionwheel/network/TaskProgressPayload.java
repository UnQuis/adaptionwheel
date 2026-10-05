package ru.adaptionwheel.network;

import java.util.List;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.client.ClientAdaption;
import ru.adaptionwheel.data.AdaptionTask;

/**
 * The running analyses and their remaining ticks, sent while any are running.
 *
 * <p>This exists because the full {@link AdaptionSyncPayload} is 1 Hz and the client had no way to
 * see a task's timer change sooner. The client already extrapolates the smooth countdown between
 * syncs ({@code ClientAdaption.taskProgress} subtracts the ticks elapsed since the last one), so the
 * countdown itself was never the problem — the problem was that a task's timer also moves
 * <i>discontinuously</i>, and extrapolation cannot invent a jump it was never told about:
 *
 * <ul>
 *   <li>{@code startOrAccelerate}'s accelerate branch does {@code existing.timer -= accelerationTicks}
 *       and nothing else, while the branch above it (starting a task) calls the sync immediately.
 *       That asymmetry is the whole bug: take damage, the server moves the bar, the client keeps
 *       extrapolating from the stale timer, and the correction only lands on the next 1 Hz sync — so
 *       the bar visibly reacts to the damage a moment late and then catches up in a jump.
 *   <li>A task completing is the same shape: the row lingers until the next sync.
 * </ul>
 *
 * <p>So the tasks are pushed on a short cadence <i>only while at least one is running</i>, which
 * makes every discrete change visible within {@value #PUSH_EVERY_TICKS} ticks and costs nothing when
 * the player is not analysing anything. This is deliberately the same shape as
 * {@link FistProgressPayload}: an immediate small payload for the thing that must feel immediate, with
 * the 1 Hz full sync left in place as the recovery path.
 *
 * <p>It carries the existence counters too, and that is not padding: {@code existenceProgress} is
 * extrapolated from the same clock, so advancing that clock without them would make the boss bar
 * jump backwards every time this arrives.
 */
public record TaskProgressPayload(List<AdaptionTask> tasks,
                                  java.util.Map<String, Integer> existenceProgress)
        implements CustomPacketPayload {

    /** 5 Hz. Fast enough that no discrete change reads as late, and a handful of bytes. */
    public static final int PUSH_EVERY_TICKS = 4;

    public static final Type<TaskProgressPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(AdaptionWheel.MODID, "task_progress"));

    public static final StreamCodec<FriendlyByteBuf, TaskProgressPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.tasks().size());
                for (AdaptionTask task : payload.tasks()) {
                    buf.writeUtf(task.concept, 256);
                    buf.writeVarInt(task.timer);
                    buf.writeVarInt(task.maxTimer);
                }
                buf.writeVarInt(payload.existenceProgress().size());
                payload.existenceProgress().forEach((boss, ticks) -> {
                    buf.writeUtf(boss, 256);
                    buf.writeVarInt(ticks);
                });
            },
            buf -> {
                int taskCount = buf.readVarInt();
                List<AdaptionTask> tasks = new java.util.ArrayList<>(taskCount);
                for (int i = 0; i < taskCount; i++) {
                    tasks.add(new AdaptionTask(buf.readUtf(256), buf.readVarInt(), buf.readVarInt()));
                }
                int bossCount = buf.readVarInt();
                java.util.Map<String, Integer> existence = new java.util.HashMap<>(bossCount);
                for (int i = 0; i < bossCount; i++) {
                    existence.put(buf.readUtf(256), buf.readVarInt());
                }
                return new TaskProgressPayload(tasks, existence);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void send(ServerPlayer player, List<AdaptionTask> tasks,
                            java.util.Map<String, Integer> existenceProgress) {
        PacketDistributor.sendToPlayer(player,
                new TaskProgressPayload(List.copyOf(tasks), java.util.Map.copyOf(existenceProgress)));
    }

    public static void apply(TaskProgressPayload payload) {
        ClientAdaption.applyTaskProgress(payload.tasks(), payload.existenceProgress());
    }
}