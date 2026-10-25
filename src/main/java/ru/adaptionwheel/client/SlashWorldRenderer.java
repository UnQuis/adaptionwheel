package ru.adaptionwheel.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.entity.CursedSlashProjectile;
import ru.adaptionwheel.entity.SpatialRiftProjectile;

/**
 * Draws every live slash between entities and the translucent block pass, which is the only place
 * both occlusion and water behave.
 *
 * <p>The stage has moved twice, and the two mistakes are worth keeping:
 * <ul>
 *   <li>Drawn from the entity renderer, water painted over it — the blade was invisible the moment
 *   it crossed a surface, which is most of the time in the one place a player looks at their own
 *   slashes.</li>
 *   <li>Drawn after the translucent pass with the depth test switched off while the camera or the
 *   blade was underwater, water stopped covering it — and so did the terrain, so a slash below the
 *   surface was painted on top of the sea. That is what "slashes render above the water" was.</li>
 * </ul>
 *
 * <p>{@link RenderLevelStageEvent.Stage#AFTER_ENTITIES} sits before water is drawn and after
 * opaque terrain, so the depth test stays on for free: terrain still occludes the blade, nothing
 * water-related is in the depth buffer yet (which is also why fast graphics cannot interfere), and
 * the translucent pass then tints the blade with the water it is cutting through instead of being
 * erased by it.
 */
@EventBusSubscriber(modid = AdaptionWheel.MODID, value = Dist.CLIENT)
public final class SlashWorldRenderer {

    private static final int[] CURSED_BLADE = {18, 24, 34};
    private static final int[] CURSED_GLOW = {74, 222, 255};
    private static final int[] RIFT_BLADE = {24, 10, 38};
    private static final int[] RIFT_GLOW = {188, 82, 255};

    private SlashWorldRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !SlashShaderFX.ready()) {
            return;
        }

        Camera camera = event.getCamera();
        Vec3 cameraPos = camera.getPosition();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Matrix4f viewProjection = new Matrix4f(event.getProjectionMatrix())
                .mul(event.getModelViewMatrix());

        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof CursedSlashProjectile slash) {
                float age = slash.tickCount + partialTick;
                float fade = age < 24f ? 1f : Mth.clamp(1f - (age - 24f) / 12f, 0f, 1f);
                drawSlash(cameraPos, viewProjection,
                        interpolated(slash, partialTick), slash.getDeltaMovement(), slash.getRoll(),
                        age, 6.0f, 0.22f, 0.13f, CURSED_BLADE, CURSED_GLOW, fade, slash.getId());
            } else if (entity instanceof SpatialRiftProjectile rift) {
                float age = rift.tickCount + partialTick;
                float lifeRatio = Mth.clamp(age / SpatialRiftProjectile.LIFETIME_TICKS, 0f, 1f);
                float fade = lifeRatio > 0.82f
                        ? Mth.clamp((1f - lifeRatio) / 0.18f, 0f, 1f) : 1f;
                drawSlash(cameraPos, viewProjection,
                        interpolated(rift, partialTick), rift.getDeltaMovement(), rift.getRoll(),
                        age, 9.0f, 0.33f, 0.20f, RIFT_BLADE, RIFT_GLOW, fade, rift.getId());
            }
        }
    }

    private static Vec3 interpolated(Entity entity, float partialTick) {
        return new Vec3(
                Mth.lerp(partialTick, entity.xOld, entity.getX()),
                Mth.lerp(partialTick, entity.yOld, entity.getY()),
                Mth.lerp(partialTick, entity.zOld, entity.getZ()));
    }

    private static void drawSlash(Vec3 cameraPos, Matrix4f viewProjection,
                                  Vec3 center, Vec3 motion, float roll, float age,
                                  float length, float glowHalf, float coreHalf,
                                  int[] blade, int[] glow, float fade, int seed) {
        Vec3 direction = motion.lengthSqr() > 1.0e-6 ? motion.normalize() : new Vec3(0, 0, 1);

        PoseStack pose = new PoseStack();
        // Echoes first: they are the blade's own motion smear, and drawing them behind the live
        // blade keeps the live silhouette dominant.
        for (int i = 2; i >= 1; i--) {
            Vec3 echo = center.subtract(direction.scale(i * 0.75));
            float echoAge = Math.max(0f, age - i * 1.25f);
            float echoFade = fade * (i == 1 ? 0.26f : 0.11f);
            drawOne(pose, cameraPos, echo, direction, roll + i * 0.045f, echoAge,
                    length * 0.92f, glowHalf * 0.78f, coreHalf * 0.62f,
                    blade, glow, echoFade, viewProjection, seed + i * 31);
        }

        drawOne(pose, cameraPos, center, direction, roll, age,
                length, glowHalf, coreHalf, blade, glow, fade,
                viewProjection, seed);
    }

    private static void drawOne(PoseStack pose, Vec3 cameraPos, Vec3 center, Vec3 direction,
                                float roll, float age, float length, float glowHalf,
                                float coreHalf, int[] blade, int[] glow, float fade,
                                Matrix4f viewProjection, int seed) {
        pose.pushPose();
        pose.translate(center.x - cameraPos.x, center.y - cameraPos.y, center.z - cameraPos.z);
        FlyingSlashRenderer.render(pose, direction, roll, age, length, glowHalf, coreHalf,
                blade, glow, fade, viewProjection, true, seed);
        pose.popPose();
    }
}
