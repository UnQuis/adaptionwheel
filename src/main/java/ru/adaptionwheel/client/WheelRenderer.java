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

@EventBusSubscriber(modid = AdaptionWheel.MODID,
        value = net.neoforged.api.distmarker.Dist.CLIENT)
public class WheelRenderer {

    private static final float MODEL_RADIUS_PX = 4.5f;
    private static final float PX_PER_BLOCK = 16.0f;
    private static final float SPIN_RADS_PER_TICK = 0.05f;
    private static final float BOB_AMPLITUDE = 0.06f;
    private static final float BOB_SPEED = 0.08f;

    private static final ContextKey<Boolean> WEARING_WHEEL =
            new ContextKey<>(Identifier.fromNamespaceAndPath(AdaptionWheel.MODID, "wearing_wheel"));

    private static final ContextKey<Integer> ADAPT_COUNT =
            new ContextKey<>(Identifier.fromNamespaceAndPath(AdaptionWheel.MODID, "adapt_count"));

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

                    state.setRenderData(WORLD_TIME, player.level().getGameTime() + state.partialTick);
                    state.setRenderData(ADAPT_COUNT, adaptCountFor(player));
                }
            }
        });
    }

    @SubscribeEvent
    public static void render(RenderLivingEvent.Post<?, ?, ?> event) {
        LivingEntityRenderState state = event.getRenderState();

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

        // Growth is a smooth function of the adaptation count rather than a step per named tier:
        // the wheel visibly fills out as the player adapts, with no thresholds to cross and nothing
        // to be told about. Capped so a very late wheel cannot grow off the screen.
        Integer storedCount = state.getRenderData(ADAPT_COUNT);
        int adaptCount = storedCount != null ? storedCount : 0;
        float growth = 1.0F + 0.55F * (adaptCount / (float) WHEEL_GROWTH_REFERENCE);
        scale *= Math.min(growth, MAX_GROWTH);
        int tint = tintFor(adaptCount);

        float bob = Mth.sin(time * BOB_SPEED) * BOB_AMPLITUDE;

        poseStack.pushPose();

        poseStack.translate(0, height + bob, 0);

        poseStack.rotate(Axis.YP, (float) Math.toRadians(-state.bodyRot));

        poseStack.rotate(Axis.YP, time * SPIN_RADS_PER_TICK);

        poseStack.scale(scale, scale, scale);

        collector.submitModelPart(rootPart, poseStack,
                RenderTypes.entityCutout(DharmaChakraModel.TEXTURE),
                LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null, 0xF3000000 | tint);

        poseStack.popPose();
    }

    /** Adaptations at which the wheel has visibly filled out. */
    private static final int WHEEL_GROWTH_REFERENCE = 60;
    /** Upper bound on growth, so a very late wheel stays on screen. */
    private static final float MAX_GROWTH = 1.55F;

    /**
     * The wearer's adaptation count, read the same way for every player a client draws.
     *
     * <p>Goes through {@link WheelSlots#findWorn} rather than Curios directly, because this branch
     * can run without Curios: the wheel then lives in the offhand or the inventory, and reaching for
     * the Curios API unconditionally would draw every remote player's wheel at its base size.
     */
    private static int adaptCountFor(Player player) {
        if (player.isLocalPlayer()) {
            return ClientAdaption.adaptedCount;
        }
        return WheelSlots.findWorn(player)
                .map(stack -> stack.get(ru.adaptionwheel.data.ModDataComponents.WHEEL_DATA.get()))
                .map(ru.adaptionwheel.data.WheelData::adaptCount)
                .orElse(0);
    }

    /** Cool grey at nothing adapted, warming towards gold as the wheel fills out. */
    private static int tintFor(int adaptCount) {
        float t = Math.min(1.0F, adaptCount / (float) WHEEL_GROWTH_REFERENCE);
        int r = (int) (0x4A + (0xFF - 0x4A) * t);
        int g = (int) (0x4A + (0xD7 - 0x4A) * t);
        int b = (int) (0x4A + (0x5C - 0x4A) * t);
        return 0xFF000000 | r << 16 | g << 8 | b;
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
        boolean wearing = WheelSlots.isWorn(player);
        WEARING_CACHE.put(player.getUUID(), new long[]{now, wearing ? 1 : 0});
        return wearing;
    }
}
