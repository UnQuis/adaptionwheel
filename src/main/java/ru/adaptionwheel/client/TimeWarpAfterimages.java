package ru.adaptionwheel.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.client.slashcut.CapturaCorpo;
import ru.adaptionwheel.client.slashcut.Corpo;
import ru.adaptionwheel.client.slashcut.Metades;

import java.util.ArrayList;
import java.util.List;

/**
 * Frozen-time residue: while a Time Warp zone is active, every slowed mob leaves a short trail of
 * translucent copies of its captured model. The copies are display-only and are never sent back.
 */
@EventBusSubscriber(modid = AdaptionWheel.MODID, value = Dist.CLIENT)
public final class TimeWarpAfterimages {

    private static final int LIFE_TICKS = 12;
    private static final int SAMPLE_TICKS = 2;
    private static final int MAX_GHOSTS = 96;
    private static final int TINT = 0x7FD4FF;

    private static final class Ghost {
        final Corpo corpo;
        int age;

        Ghost(Corpo corpo) {
            this.corpo = corpo;
        }
    }

    private static final List<Ghost> GHOSTS = new ArrayList<>();
    private static int sample;

    private TimeWarpAfterimages() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!TimeWarpClient.isActive()) {
            GHOSTS.clear();
            sample = 0;
            return;
        }
        for (int i = GHOSTS.size() - 1; i >= 0; i--) {
            Ghost ghost = GHOSTS.get(i);
            if (++ghost.age > LIFE_TICKS) {
                GHOSTS.remove(i);
            }
        }
        if (++sample % SAMPLE_TICKS != 0) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity) || !TimeWarpClient.afeta(entity)) {
                continue;
            }
            Corpo corpo = CapturaCorpo.cached(entity.getId());
            if (corpo != null && corpo.quadCount() > 0) {
                GHOSTS.add(new Ghost(corpo));
            }
        }
        while (GHOSTS.size() > MAX_GHOSTS) {
            GHOSTS.remove(0);
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || GHOSTS.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        PoseStack.Pose view = event.getPoseStack().last();
        for (Ghost ghost : GHOSTS) {
            float fade = 1f - ghost.age / (float) LIFE_TICKS;
            int alpha = (int) (0.45f * fade * 255f);
            VertexConsumer buffer = buffers.getBuffer(RenderType.entityTranslucent(ghost.corpo.texture()));
            Metades.desenharCorpo(buffer, view, ghost.corpo, camera, Vec3.ZERO, TINT, alpha);
        }
        buffers.endBatch();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        GHOSTS.clear();
        sample = 0;
    }
}
