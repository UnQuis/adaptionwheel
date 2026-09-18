package ru.adaptionwheel.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.entity.ClientAvatarEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.renderstate.AvatarRenderStateModifier;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.compat.WheelSlots;
import ru.adaptionwheel.config.AdaptionConfig;

/**
 * Renders the 3D Dharma Chakra above the player's head.
 * The wheel is fixed relative to the world: it only follows the player's position,
 * cancels the body yaw applied by the living renderer, spins around the world Y axis
 * and bobs up and down. No other movement.
 *
 * <p>26.x entity rendering is state-based: the entity is no longer available during
 * rendering, so the "wearing" flag and the world time are sampled into the player's
 * {@link AvatarRenderState} by a render-state modifier and read back in
 * {@link RenderLivingEvent.Post}.</p>
 */
@EventBusSubscriber(modid = AdaptionWheel.MODID,
        value = net.neoforged.api.distmarker.Dist.CLIENT)
public class WheelRenderer {

    private static final float MODEL_RADIUS_PX = 4.5f;
    private static final float PX_PER_BLOCK = 16.0f;
    private static final float SPIN_RADS_PER_TICK = 0.05f;
    private static final float BOB_AMPLITUDE = 0.06f;
    private static final float BOB_SPEED = 0.08f;

    /** Set on the player's render state when the wheel should be drawn. */
    private static final ContextKey<Boolean> WEARING_WHEEL =
            new ContextKey<>(Identifier.fromNamespaceAndPath(AdaptionWheel.MODID, "wearing_wheel"));
    /** Continuous world time (game time + partial tick) for the spin/bob phase. */
    private static final ContextKey<Float> WORLD_TIME =
            new ContextKey<>(Identifier.fromNamespaceAndPath(AdaptionWheel.MODID, "world_time"));

    @SubscribeEvent
    public static void registerRenderStateModifiers(RegisterRenderStateModifiersEvent event) {
        event.registerAvatarEntityModifier(new AvatarRenderStateModifier() {
            @Override
            public <T extends Avatar & ClientAvatarEntity> void accept(T avatar, AvatarRenderState state) {
                if (!(avatar instanceof Player player)) {
                    return;
                }
                boolean wearing = AdaptionConfig.WHEEL_ABOVE_HEAD.get() && isWearingWheel(player);
                state.setRenderData(WEARING_WHEEL, wearing ? Boolean.TRUE : null);
                if (wearing) {
                    // GameTime instead of tickCount: keeps the spin/bob phase continuous across respawn.
                    state.setRenderData(WORLD_TIME, player.level().getGameTime() + state.partialTick);
                }
            }
        });
    }

    @SubscribeEvent
    public static void render(RenderLivingEvent.Post<?, ?, ?> event) {
        LivingEntityRenderState state = event.getRenderState();
        // Cheapest check first: this event fires for EVERY rendered living entity.
        if (!(state instanceof AvatarRenderState) || !Boolean.TRUE.equals(state.getRenderData(WEARING_WHEEL))) {
            return;
        }
        Float storedTime = state.getRenderData(WORLD_TIME);
        float time = storedTime != null ? storedTime : state.ageInTicks;

        ModelPart rootPart = DharmaChakraModel.getBakedRoot();
        PoseStack poseStack = event.getPoseStack();
        SubmitNodeCollector collector = event.getSubmitNodeCollector();

        float height = state.boundingBoxHeight + 0.45f;
        float wheelSize = (float) (double) AdaptionConfig.WHEEL_SIZE.get();
        float scale = wheelSize * PX_PER_BLOCK / MODEL_RADIUS_PX;
        float bob = Mth.sin(time * BOB_SPEED) * BOB_AMPLITUDE;

        poseStack.pushPose();

        // 1. Position above the head, bobbing up and down
        poseStack.translate(0, height + bob, 0);

        // 2. Cancel the body yaw set up by the living renderer so the wheel stays
        //    fixed in world space regardless of how the player is turned
        poseStack.rotate(Axis.YP, (float) Math.toRadians(-state.bodyRot));

        // 3. Continuous spin around the world Y axis
        poseStack.rotate(Axis.YP, time * SPIN_RADS_PER_TICK);

        // 4. Scale to match configured wheel size
        poseStack.scale(scale, scale, scale);

        // 5. Submit the 3D model with fullbright lighting
        collector.submitModelPart(rootPart, poseStack,
                RenderTypes.entityCutout(DharmaChakraModel.TEXTURE),
                LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null, 0xF3FFFFFF);

        poseStack.popPose();
    }

    /**
     * Short-TTL memo for the slot scan of OTHER players: this runs once per
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
        boolean wearing = WheelSlots.isWorn(player);
        WEARING_CACHE.put(player.getUUID(), new long[]{now, wearing ? 1 : 0});
        return wearing;
    }
}
