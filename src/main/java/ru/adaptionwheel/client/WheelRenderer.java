package ru.adaptionwheel.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.config.AdaptionConfig;
import ru.adaptionwheel.data.ModDataComponents;
import ru.adaptionwheel.item.ModItems;
import top.theillusivec4.curios.api.CuriosApi;

@EventBusSubscriber(modid = AdaptionWheel.MODID,
        value = net.neoforged.api.distmarker.Dist.CLIENT)
public class WheelRenderer {

    private static final float MODEL_RADIUS_PX = 4.5f;
    private static final float PX_PER_BLOCK = 16.0f;
    private static final float SPIN_RADS_PER_TICK = 0.05f;
    private static final float BOB_AMPLITUDE = 0.06f;
    private static final float BOB_SPEED = 0.08f;

    @SubscribeEvent
    public static void render(RenderLivingEvent.Post<?, ?> event) {
        LivingEntity entity = event.getEntity();

        if (!(entity instanceof Player player)) {
            return;
        }
        if (!AdaptionConfig.WHEEL_ABOVE_HEAD.get() || !isWearingWheel(player)) {
            return;
        }

        ModelPart rootPart = DharmaChakraModel.getBakedRoot();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource bufferSource = event.getMultiBufferSource();

        float partialTick = event.getPartialTick();
        float height = entity.getBbHeight() + 0.45f;

        int tier = tierFor(player);
        float wheelSize = (float) (double) AdaptionConfig.WHEEL_SIZE.get();
        float scale = wheelSize * PX_PER_BLOCK / MODEL_RADIUS_PX;

        scale *= 1.0F + 0.12F * tier;
        int tint = ru.adaptionwheel.category.WheelTier.color(tier);

        float time = player.level().getGameTime() + partialTick;
        float bob = Mth.sin(time * BOB_SPEED) * BOB_AMPLITUDE;

        poseStack.pushPose();

        poseStack.translate(0, height + bob, 0);

        float bodyYaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
        poseStack.mulPose(Axis.YP.rotation((float) Math.toRadians(-bodyYaw)));

        poseStack.mulPose(Axis.YP.rotation(time * SPIN_RADS_PER_TICK));

        poseStack.scale(scale, scale, scale);

        VertexConsumer consumer = bufferSource.getBuffer(
                RenderType.entityCutoutNoCull(DharmaChakraModel.TEXTURE));
        rootPart.render(poseStack, consumer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                tintWithAlpha(tint, 0xF3));

        poseStack.popPose();
    }

    private static int tierFor(Player player) {
        if (player.isLocalPlayer()) {
            return ru.adaptionwheel.category.WheelTier.forCount(ClientAdaption.adaptedCount);
        }

        return CuriosApi.getCuriosInventory(player)
                .flatMap(handler -> handler.findFirstCurio(ModItems.MAHORAGA_WHEEL.get()))
                .map(top.theillusivec4.curios.api.SlotResult::stack)
                .map(stack -> stack.get(ModDataComponents.WHEEL_DATA.get()))
                .map(ru.adaptionwheel.data.WheelData::adaptCount)
                .map(ru.adaptionwheel.category.WheelTier::forCount)
                .orElse(0);
    }

    private static int tintWithAlpha(int rgb, int alpha) {
        return (alpha & 0xFF) << 24 | (rgb & 0x00FFFFFF);
    }

    private static final java.util.Map<java.util.UUID, long[]> WEARING_CACHE = new java.util.HashMap<>();
    private static final long CACHE_TTL_TICKS = 10;

    private static boolean isWearingWheel(Player player) {
        if (player.isLocalPlayer()) {
            return ClientAdaption.wearingWheel;
        }
        long now = player.level().getGameTime();
        long[] cached = WEARING_CACHE.get(player.getUUID());
        if (cached != null && now - cached[0] < CACHE_TTL_TICKS) {
            return cached[1] != 0;
        }
        boolean wearing = CuriosApi.getCuriosInventory(player)
                .map(handler -> handler.isEquipped(ModItems.MAHORAGA_WHEEL.get()))
                .orElse(false);
        WEARING_CACHE.put(player.getUUID(), new long[]{now, wearing ? 1 : 0});
        return wearing;
    }
}
