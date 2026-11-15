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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.renderstate.AvatarRenderStateModifier;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.compat.WheelSlots;
import ru.adaptionwheel.config.AdaptionConfig;

import java.util.HashMap;
import java.util.Map;

@EventBusSubscriber(modid = AdaptionWheel.MODID, value = Dist.CLIENT)
public class WheelRenderer {

    private static final float MODEL_RADIUS_PX = 4.5f;
    private static final float PX_PER_BLOCK = 16.0f;
    private static final float BOB_AMPLITUDE = 0.06f;
    private static final float BOB_SPEED = 0.08f;

    /**
     * One completed adaptation advances the wheel by one spoke — an eighth of a turn. The wheel
     * does not spin on its own: it holds still between completions, and the bob is the only
     * continuous motion left. Ported from 1.21.1 to restore the detent/"click" behaviour.
     */
    private static final float STEP_RADIANS = (float) (Math.PI / 4.0);

    /** How long one spoke takes to click round, in ticks. */
    private static final float STEP_TICKS = 6.0f;

    /**
     * Ease-out-back constant: the click turns a little past the spoke and settles back, like a
     * detent. At 1.9 the overshoot is about 12% of a step (~5° on a 45° turn).
     */
    private static final float BACK_C1 = 1.9f;

    /**
     * A count change larger than this is a load/grant/reset rather than a real adaptation; it
     * snaps because animating dozens of steps would whirl the wheel. The mesh is eight-fold
     * symmetric, so the phase modulo 45° does not matter.
     */
    private static final int MAX_ANIMATED_STEPS = 8;

    private static final ContextKey<Boolean> WEARING_WHEEL =
            new ContextKey<>(Identifier.fromNamespaceAndPath(AdaptionWheel.MODID, "wearing_wheel"));

    private static final ContextKey<Integer> ADAPT_COUNT =
            new ContextKey<>(Identifier.fromNamespaceAndPath(AdaptionWheel.MODID, "adapt_count"));

    private static final ContextKey<Float> WORLD_TIME =
            new ContextKey<>(Identifier.fromNamespaceAndPath(AdaptionWheel.MODID, "world_time"));

    /** Stable per-entity key for the client-side click animation state. */
    private static final ContextKey<Integer> ENTITY_ID =
            new ContextKey<>(Identifier.fromNamespaceAndPath(AdaptionWheel.MODID, "wheel_entity_id"));

    private static final Map<Integer, SpinState> SPIN = new HashMap<>();

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
                    state.setRenderData(ENTITY_ID, player.getId());
                }
            }
        });
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        WEARING_CACHE.clear();
        SPIN.clear();
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
        Integer entityId = state.getRenderData(ENTITY_ID);
        int spinKey = entityId != null ? entityId : 0;
        float spinAngle = wheelAngle(spinKey, adaptCount, time);

        poseStack.pushPose();

        poseStack.translate(0, height + bob, 0);

        // No idle spin and no body-yaw follow: the wheel hangs in world space and only advances a
        // spoke when an adaptation completes. The entity yaw lives inside the renderer's own push,
        // so this cancels it back to world space (parity with 1.21.1 behaviour).
        poseStack.rotate(Axis.YP, (float) Math.toRadians(-state.bodyRot));
        poseStack.rotate(Axis.YP, spinAngle);

        poseStack.scale(scale, scale, scale);

        // 26.3 render-state path currently draws the fallback 25-cube model through vanilla model
        // submission. Full wheel_metal OBJ/shader + WheelLights parity from 1.21.1 is intentionally
        // deferred here: the 26.3 SubmitNodeCollector/render-pipeline/uniform flow differs
        // substantially and a partial port risks breaking the wheel entirely. FULL_BRIGHT keeps the
        // wheel readable until world-light sampling is ported safely.
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

    /**
     * Per-entity detent animation. The angle is a running total (symmetric mesh => phase modulo 45°
     * is invisible) so a wheel loaded at 300 adaptations does not whirl to catch up.
     */
    private static float wheelAngle(int entityId, int adaptCount, float time) {
        SpinState state = SPIN.computeIfAbsent(entityId, id -> new SpinState(adaptCount));

        if (state.count != adaptCount) {
            int delta = adaptCount - state.count;
            state.count = adaptCount;
            if (Math.abs(delta) <= MAX_ANIMATED_STEPS) {
                state.from = state.angle;
                state.to = state.angle + delta * STEP_RADIANS;
                state.start = time;
            } else {
                state.angle = 0.0F;
                state.from = 0.0F;
                state.to = 0.0F;
                state.start = time;
            }
        }

        float progress = (time - state.start) / STEP_TICKS;
        if (progress >= 1.0F) {
            state.angle = state.to;
        } else if (progress > 0.0F) {
            state.angle = state.from + (state.to - state.from) * easeOutBack(progress);
        }
        return state.angle;
    }

    /** Ease-out-back: ends at exactly 1, but crosses it first. */
    private static float easeOutBack(float t) {
        float u = t - 1.0F;
        return 1.0F + (BACK_C1 + 1.0F) * u * u * u + BACK_C1 * u * u;
    }

    private static final class SpinState {
        int count;
        float angle;
        float from;
        float to;
        float start;

        SpinState(int count) {
            this.count = count;
        }
    }
}
