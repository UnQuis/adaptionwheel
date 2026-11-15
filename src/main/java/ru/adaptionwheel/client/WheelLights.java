package ru.adaptionwheel.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * The glowing blocks near a wheel, so its metal has something to reflect besides the sky.
 */
final class WheelLights {

    static final int MAX = 8;

    private static final int RADIUS = 8;
    private static final double RADIUS_SQ = (double) RADIUS * RADIUS;
    private static final int REFRESH_TICKS = 4;
    private static final int MAX_CACHED = 24;

    record Source(float x, float y, float z, float red, float green, float blue,
                  float radius, float intensity, double score) {
    }

    private record Cached(long tick, Source[] sources) {
    }

    private static final Map<Long, Cached> CACHE = new HashMap<>();
    private static final Map<Block, float[]> COLOURS = new HashMap<>();

    private WheelLights() {
    }

    static Source[] around(ClientLevel level, double centreX, double centreY, double centreZ) {
        BlockPos centre = BlockPos.containing(centreX, centreY, centreZ);
        long key = centre.asLong();
        long now = level.getGameTime();

        Cached cached = CACHE.get(key);
        if (cached != null && now - cached.tick() < REFRESH_TICKS) {
            return cached.sources();
        }

        Source[] sources = scan(level, centre, centreX, centreY, centreZ);
        occlude(level, centreX, centreY, centreZ, sources);
        CACHE.put(key, new Cached(now, sources));
        if (CACHE.size() > MAX_CACHED) {
            CACHE.entrySet().removeIf(entry -> now - entry.getValue().tick() > REFRESH_TICKS * 8L);
        }
        return sources;
    }

    static void clear() {
        CACHE.clear();
        COLOURS.clear();
    }

    private static Source[] scan(ClientLevel level, BlockPos centre,
                                 double centreX, double centreY, double centreZ) {
        Source[] best = new Source[MAX];
        int count = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dy = -RADIUS; dy <= RADIUS; dy++) {
                for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                    cursor.set(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz);
                    BlockState state = level.getBlockState(cursor);
                    int emission = state.getLightEmission();
                    if (emission <= 0) {
                        continue;
                    }

                    double x = cursor.getX() + 0.5;
                    double y = cursor.getY() + 0.5;
                    double z = cursor.getZ() + 0.5;
                    double ox = x - centreX;
                    double oy = y - centreY;
                    double oz = z - centreZ;
                    double distanceSq = ox * ox + oy * oy + oz * oz;
                    if (distanceSq > RADIUS_SQ) {
                        continue;
                    }

                    double score = emission / (1.0 + distanceSq);
                    if (count == MAX && score <= best[count - 1].score()) {
                        continue;
                    }

                    float[] colour = colourOf(state.getBlock());
                    Source source = new Source((float) x, (float) y, (float) z,
                            colour[0], colour[1], colour[2], colour[3],
                            emission / 15.0F, score);

                    int at = count < MAX ? count : MAX - 1;
                    while (at > 0 && best[at - 1].score() < score) {
                        best[at] = best[at - 1];
                        at--;
                    }
                    best[at] = source;
                    if (count < MAX) {
                        count++;
                    }
                }
            }
        }

        return count == MAX ? best : Arrays.copyOf(best, count);
    }

    private static void occlude(ClientLevel level, double centreX, double centreY, double centreZ,
                                Source[] sources) {
        Player viewer = Minecraft.getInstance().player;
        if (viewer == null) {
            return;
        }
        Vec3 from = new Vec3(centreX, centreY, centreZ);
        for (int i = 0; i < sources.length; i++) {
            Source source = sources[i];
            Vec3 to = new Vec3(source.x(), source.y(), source.z());
            double distance = from.distanceTo(to);
            BlockHitResult hit = level.clip(new ClipContext(from, to,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, viewer));
            if (hit.getType() != HitResult.Type.MISS
                    && from.distanceTo(hit.getLocation()) < distance - 0.6) {
                sources[i] = new Source(source.x(), source.y(), source.z(), source.red(),
                        source.green(), source.blue(), source.radius(), 0.0F, source.score());
            }
        }
    }

    private static float[] colourOf(Block block) {
        return COLOURS.computeIfAbsent(block, b -> {
            String path = BuiltInRegistries.BLOCK.getKey(b).getPath();
            if (path.contains("soul")) {
                return new float[]{0.42F, 0.72F, 1.00F, 0.13F};
            }
            if (path.contains("redstone")) {
                return new float[]{0.95F, 0.22F, 0.15F, 0.12F};
            }
            if (path.contains("sea_lantern") || path.contains("prismarine")) {
                return new float[]{0.55F, 0.95F, 0.92F, 0.15F};
            }
            if (path.contains("end_rod")) {
                return new float[]{0.88F, 0.84F, 1.00F, 0.12F};
            }
            if (path.contains("lava") || path.contains("magma") || path.contains("fire")) {
                return new float[]{1.00F, 0.42F, 0.12F, 0.22F};
            }
            if (path.contains("shroomlight")) {
                return new float[]{1.00F, 0.62F, 0.30F, 0.18F};
            }
            if (path.contains("glowstone") || path.contains("lantern") || path.contains("torch")) {
                return new float[]{1.00F, 0.82F, 0.52F, 0.13F};
            }
            return new float[]{1.00F, 0.78F, 0.48F, 0.14F};
        });
    }
}
