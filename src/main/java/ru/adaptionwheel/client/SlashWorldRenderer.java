package ru.adaptionwheel.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.FogType;
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
 * Draws every live slash after the translucent block pass, which is the only place water can be
 * kept from painting over it.
 *
 * <p>Vanilla renders entities before {@code RenderType.translucent}; water is translucent, so a
 * slash drawn from its entity renderer is always underneath the water surface. Moving the draw to
 * {@link RenderLevelStageEvent.Stage#AFTER_TRANSLUCENT_BLOCKS} puts it on the far side of that
 * pass. The stage still has the main depth buffer, so opaque terrain occludes the blade exactly as
 * before; only the water (which is not in the depth buffer used here) stops covering it.
 *
 * <p>When the camera or the blade is underwater the depth test is disabled for the draw. In fast
 * graphics water writes depth directly, so keeping depth on there would re-hide the very thing
 * this pass exists to reveal. The blade is a short-lived magical cut, not a solid object, and it
 * is allowed to read through the water it is cutting.
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
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
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
        boolean cameraUnderwater = camera.getFluidInCamera() == FogType.WATER;

        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof CursedSlashProjectile slash) {
                float age = slash.tickCount + partialTick;
                float fade = age < 24f ? 1f : Mth.clamp(1f - (age - 24f) / 12f, 0f, 1f);
                drawSlash(event, cameraPos, cameraUnderwater, viewProjection,
                        interpolated(slash, partialTick), slash.getDeltaMovement(), slash.getRoll(),
                        age, 6.0f, 0.22f, 0.13f, CURSED_BLADE, CURSED_GLOW, fade, slash.getId());
            } else if (entity instanceof SpatialRiftProjectile rift) {
                float age = rift.tickCount + partialTick;
                float lifeRatio = Mth.clamp(age / SpatialRiftProjectile.LIFETIME_TICKS, 0f, 1f);
                float fade = lifeRatio > 0.82f
                        ? Mth.clamp((1f - lifeRatio) / 0.18f, 0f, 1f) : 1f;
                drawSlash(event, cameraPos, cameraUnderwater, viewProjection,
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

    private static void drawSlash(RenderLevelStageEvent event, Vec3 cameraPos,
                                  boolean cameraUnderwater, Matrix4f viewProjection,
                                  Vec3 center, Vec3 motion, float roll, float age,
                                  float length, float glowHalf, float coreHalf,
                                  int[] blade, int[] glow, float fade, int seed) {
        Vec3 direction = motion.lengthSqr() > 1.0e-6 ? motion.normalize() : new Vec3(0, 0, 1);
        boolean submerged = event.getLevelRenderer() != null
                && Minecraft.getInstance().level != null
                && Minecraft.getInstance().level.getFluidState(
                        BlockPos.containing(center.x, center.y, center.z)).is(FluidTags.WATER);
        boolean noDepth = cameraUnderwater || submerged;

        PoseStack pose = new PoseStack();
        // Echoes first: they are the blade's own motion smear, and drawing them behind the live
        // blade keeps the live silhouette dominant.
        for (int i = 2; i >= 1; i--) {
            Vec3 echo = center.subtract(direction.scale(i * 0.75));
            float echoAge = Math.max(0f, age - i * 1.25f);
            float echoFade = fade * (i == 1 ? 0.26f : 0.11f);
            drawOne(pose, cameraPos, echo, direction, roll + i * 0.045f, echoAge,
                    length * 0.92f, glowHalf * 0.78f, coreHalf * 0.62f,
                    blade, glow, echoFade, viewProjection, noDepth, seed + i * 31);
        }

        drawOne(pose, cameraPos, center, direction, roll, age,
                length, glowHalf, coreHalf, blade, glow, fade,
                viewProjection, noDepth, seed);
    }

    private static void drawOne(PoseStack pose, Vec3 cameraPos, Vec3 center, Vec3 direction,
                                float roll, float age, float length, float glowHalf,
                                float coreHalf, int[] blade, int[] glow, float fade,
                                Matrix4f viewProjection, boolean noDepth, int seed) {
        pose.pushPose();
        pose.translate(center.x - cameraPos.x, center.y - cameraPos.y, center.z - cameraPos.z);
        FlyingSlashRenderer.render(pose, direction, roll, age, length, glowHalf, coreHalf,
                blade, glow, fade, viewProjection, !noDepth, seed);
        pose.popPose();
    }
}
