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

/**
 * Renders the 3D Dharma Chakra above the player's head.
 * The wheel is fixed relative to the world: it only follows the player's position,
 * cancels the body yaw applied by the living renderer, spins around the world Y axis
 * and bobs up and down. No other movement.
 */
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
        // Cheapest check first: this event fires for EVERY rendered living entity.
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
        // Wheel awakening, read client-side from the adaptation count that the 1 Hz sync already
        // carries -- so this needs no extra packet and cannot disagree with the server.
        int tier = tierFor(player);
        float wheelSize = (float) (double) AdaptionConfig.WHEEL_SIZE.get();
        float scale = wheelSize * PX_PER_BLOCK / MODEL_RADIUS_PX;
        // A later tier is a *bigger* wheel, up to +60%. The colour below does the rest of the work;
        // size alone is too easy to miss against a busy background.
        scale *= 1.0F + 0.12F * tier;
        int tint = ru.adaptionwheel.category.WheelTier.color(tier);

        // GameTime instead of tickCount: keeps the spin/bob phase continuous across respawn.
        float time = player.level().getGameTime() + partialTick;
        float bob = Mth.sin(time * BOB_SPEED) * BOB_AMPLITUDE;

        poseStack.pushPose();

        // 1. Position above the head, bobbing up and down
        poseStack.translate(0, height + bob, 0);

        // 2. Cancel the body yaw set up by the living renderer so the wheel stays
        //    fixed in world space regardless of how the player is turned
        float bodyYaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
        poseStack.mulPose(Axis.YP.rotation((float) Math.toRadians(-bodyYaw)));

        // 3. Continuous spin around the world Y axis
        poseStack.mulPose(Axis.YP.rotation(time * SPIN_RADS_PER_TICK));

        // 4. Scale to match configured wheel size
        poseStack.scale(scale, scale, scale);

        // 5. Render the 3D model with fullbright lighting
        VertexConsumer consumer = bufferSource.getBuffer(
                RenderType.entityCutoutNoCull(DharmaChakraModel.TEXTURE));
        rootPart.render(poseStack, consumer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                tintWithAlpha(tint, 0xF3));

        poseStack.popPose();
    }

    /**
     * The rendered player's wheel tier, read from the wheel itself.
     *
     * <p>Taken from the item's own {@code wheel_data} rather than from a per-player sync, so it is
     * correct for *every* player this client renders and needs no extra packet: the component
     * already travels with the stack, and this renderer can already read other players' Curios
     * slots (see {@link #isWearingWheel}). The local player short-circuits to the synced
     * adaptation count, which is the same number and cheaper.</p>
     *
     * <p>Deliberately not memoised: it is a component read on an already-cached slot scan, and a
     * stale tier would be far more visible than the cost.</p>
     */
    private static int tierFor(Player player) {
        if (player.isLocalPlayer()) {
            return ru.adaptionwheel.category.WheelTier.forCount(ClientAdaption.adaptedCount);
        }
        // Two Curios shapes to get right, and both have bitten: getCuriosInventory returns an
        // Optional so the handler lookup is a flatMap, and findFirstCurio returns a
        // SlotResult record (not a bare ItemStack) that the stack is read out of.
        return CuriosApi.getCuriosInventory(player)
                .flatMap(handler -> handler.findFirstCurio(ModItems.MAHORAGA_WHEEL.get()))
                .map(top.theillusivec4.curios.api.SlotResult::stack)
                .map(stack -> stack.get(ModDataComponents.WHEEL_DATA.get()))
                .map(ru.adaptionwheel.data.WheelData::adaptCount)
                .map(ru.adaptionwheel.category.WheelTier::forCount)
                .orElse(0);
    }

    /** Packs a tier colour into the ARGB the model render expects, keeping the model alpha. */
    private static int tintWithAlpha(int rgb, int alpha) {
        return (alpha & 0xFF) << 24 | (rgb & 0x00FFFFFF);
    }

    /**
     * Short-TTL memo for the Curios slot scan of OTHER players: this runs once per
     * rendered frame per player, so a 10-tick cache removes nearly all of the
     * inventory walks without visibly lagging behind equipment changes.
     */
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
