package ru.adaptionwheel.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.ClientAvatarEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.context.ContextKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.renderstate.AvatarRenderStateModifier;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import org.slf4j.Logger;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.category.Concepts;
import ru.adaptionwheel.compat.WheelSlots;
import ru.adaptionwheel.config.AdaptionConfig;
import ru.adaptionwheel.data.ModDataComponents;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@EventBusSubscriber(modid = AdaptionWheel.MODID, value = Dist.CLIENT)
public class WheelRenderer {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Identifier WHEEL_MODEL = Identifier.fromNamespaceAndPath(
            AdaptionWheel.MODID, "models/entity/dharma_chakra.obj");

    private static volatile ObjMesh wheelMesh;
    private static boolean fallbackLogged;

    private static final float MODEL_RADIUS_PX = 4.5f;
    private static final float PX_PER_BLOCK = 16.0f;
    private static final float BOB_AMPLITUDE = 0.06f;
    private static final float BOB_SPEED = 0.08f;

    private static final float STEP_RADIANS = (float) (Math.PI / 4.0);
    private static final float STEP_TICKS = 6.0f;
    private static final float BACK_C1 = 1.9f;
    private static final int MAX_ANIMATED_STEPS = 8;

    private static final int WHEEL_GROWTH_REFERENCE = 60;
    private static final float MAX_GROWTH = 1.55F;

    private static final ContextKey<Boolean> WEARING_WHEEL =
            new ContextKey<>(Identifier.fromNamespaceAndPath(AdaptionWheel.MODID, "wearing_wheel"));
    private static final ContextKey<Integer> ADAPT_COUNT =
            new ContextKey<>(Identifier.fromNamespaceAndPath(AdaptionWheel.MODID, "adapt_count"));
    private static final ContextKey<Float> WORLD_TIME =
            new ContextKey<>(Identifier.fromNamespaceAndPath(AdaptionWheel.MODID, "world_time"));
    private static final ContextKey<Integer> ENTITY_ID =
            new ContextKey<>(Identifier.fromNamespaceAndPath(AdaptionWheel.MODID, "wheel_entity_id"));
    private static final ContextKey<Integer> BLOCK_LIGHT =
            new ContextKey<>(Identifier.fromNamespaceAndPath(AdaptionWheel.MODID, "wheel_block_light"));
    private static final ContextKey<Integer> SKY_LIGHT =
            new ContextKey<>(Identifier.fromNamespaceAndPath(AdaptionWheel.MODID, "wheel_sky_light"));
    private static final ContextKey<Integer> POS_X =
            new ContextKey<>(Identifier.fromNamespaceAndPath(AdaptionWheel.MODID, "wheel_x"));
    private static final ContextKey<Integer> POS_Y =
            new ContextKey<>(Identifier.fromNamespaceAndPath(AdaptionWheel.MODID, "wheel_y"));
    private static final ContextKey<Integer> POS_Z =
            new ContextKey<>(Identifier.fromNamespaceAndPath(AdaptionWheel.MODID, "wheel_z"));

    private static final Map<Integer, SpinState> SPIN = new HashMap<>();
    private static final Map<java.util.UUID, long[]> WEARING_CACHE = new HashMap<>();
    private static final long CACHE_TTL_TICKS = 10;

    // CPU-lit helpers (ported/adapted from 1.21.1)
    private static final Vector3f NORMAL_SCRATCH = new Vector3f();
    private static final Vector3f LIGHT_SCRATCH = new Vector3f();
    private static final Vector3f VIEW_SCRATCH = new Vector3f();
    private static final Vector3f HALF_SCRATCH = new Vector3f();
    private static final float METAL_AMBIENT = 0.18f;
    private static final float SPEC_STRENGTH = 0.55f;
    private static final float SPEC_POWER = 14.0f;
    private static final float RIM_STRENGTH = 0.45f;
    private static final float RIM_POWER = 3.0f;
    private static final float RIM_R = 1.00f;
    private static final float RIM_G = 0.85f;
    private static final float RIM_B = 0.35f;

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
                if (!wearing) {
                    return;
                }
                state.setRenderData(WORLD_TIME, player.level().getGameTime() + state.partialTick);
                state.setRenderData(ADAPT_COUNT, adaptCountFor(player));
                state.setRenderData(ENTITY_ID, player.getId());

                float height = state.boundingBoxHeight + 0.45f;
                BlockPos lightPos = BlockPos.containing(player.getX(), player.getY() + height, player.getZ());
                state.setRenderData(BLOCK_LIGHT, player.level().getBrightness(LightLayer.BLOCK, lightPos));
                state.setRenderData(SKY_LIGHT, player.level().getBrightness(LightLayer.SKY, lightPos));
                state.setRenderData(POS_X, Mth.floor(player.getX() * 64.0));
                state.setRenderData(POS_Y, Mth.floor((player.getY() + height) * 64.0));
                state.setRenderData(POS_Z, Mth.floor(player.getZ() * 64.0));
            }
        });
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        WEARING_CACHE.clear();
        SPIN.clear();
        WheelLights.clear();
    }

    @SubscribeEvent
    public static void render(RenderLivingEvent.Post<?, ?, ?> event) {
        LivingEntityRenderState state = event.getRenderState();
        if (!(state instanceof AvatarRenderState) || !Boolean.TRUE.equals(state.getRenderData(WEARING_WHEEL))) {
            return;
        }

        Float storedTime = state.getRenderData(WORLD_TIME);
        float time = storedTime != null ? storedTime : state.ageInTicks;
        Integer storedCount = state.getRenderData(ADAPT_COUNT);
        int adaptCount = storedCount != null ? storedCount : 0;
        Integer entityIdObj = state.getRenderData(ENTITY_ID);
        int spinKey = entityIdObj != null ? entityIdObj : 0;

        PoseStack poseStack = event.getPoseStack();
        SubmitNodeCollector collector = event.getSubmitNodeCollector();

        float height = state.boundingBoxHeight + 0.45f;
        float wheelSize = (float) (double) AdaptionConfig.WHEEL_SIZE.get();
        float growth = Math.min(1.0F + 0.55F * (adaptCount / (float) WHEEL_GROWTH_REFERENCE), MAX_GROWTH);
        int tint = tintFor(adaptCount);
        float bob = Mth.sin(time * BOB_SPEED) * BOB_AMPLITUDE;
        float spinAngle = wheelAngle(spinKey, adaptCount, time);

        poseStack.pushPose();
        poseStack.translate(0, height + bob, 0);
        poseStack.rotate(Axis.YP, (float) Math.toRadians(-state.bodyRot));
        poseStack.rotate(Axis.YP, spinAngle);

        ObjMesh mesh = wheelMesh;
        if (mesh != null) {
            float scale = (wheelSize / mesh.radius()) * growth;
            poseStack.scale(scale, scale, scale);
            drawMesh(mesh, poseStack, collector, adaptCount, tint, state);
        } else {
            if (!fallbackLogged) {
                fallbackLogged = true;
                LOGGER.warn("[{}] No wheel mesh is loaded; drawing the fallback 25-cube model. If {} exists, "
                        + "the mesh reload listener did not run or the file failed to parse", AdaptionWheel.MODID, WHEEL_MODEL);
            }
            ModelPart rootPart = DharmaChakraModel.getBakedRoot();
            float scale = wheelSize * PX_PER_BLOCK / MODEL_RADIUS_PX * growth;
            poseStack.scale(scale, scale, scale);
            collector.submitModelPart(rootPart, poseStack,
                    RenderTypes.entityCutout(DharmaChakraModel.TEXTURE),
                    LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null, 0xF3000000 | tint);
        }

        poseStack.popPose();
    }

    private static void drawMesh(ObjMesh mesh, PoseStack poseStack, SubmitNodeCollector collector,
                                 int adaptCount, int tintRgb, LivingEntityRenderState state) {
        RenderType renderType = ru.adaptionwheel.client.render.ModRenderTypes.wheelMetal();
        float[] corners = mesh.quadCorners();

        int blockI = orDefault(state.getRenderData(BLOCK_LIGHT), 0);
        int skyI = orDefault(state.getRenderData(SKY_LIGHT), 15);
        int packedLight = LightCoordsUtil.pack(blockI, skyI);

        float tr = ((tintRgb >> 16) & 0xFF) / 255.0F;
        float tg = ((tintRgb >> 8) & 0xFF) / 255.0F;
        float tb = (tintRgb & 0xFF) / 255.0F;

        collector.submitCustomGeometry(poseStack, renderType, (pose, consumer) -> {
            for (int i = 0; i < corners.length; i += ObjMesh.STRIDE * 4) {
                for (int corner = 0; corner < 4; corner++) {
                    int o = i + corner * ObjMesh.STRIDE;
                    float x = corners[o], y = corners[o + 1], z = corners[o + 2];
                    float u = corners[o + 3], v = corners[o + 4];
                    float nx = corners[o + 5], ny = corners[o + 6], nz = corners[o + 7];
                    // Mesh vertex colors carry material tint from the source OBJ; apply adaptation tint.
                    float vr = Mth.clamp(corners[o + 8] * tr, 0.0f, 1.0f);
                    float vg = Mth.clamp(corners[o + 9] * tg, 0.0f, 1.0f);
                    float vb = Mth.clamp(corners[o + 10] * tb, 0.0f, 1.0f);
                    consumer.addVertex(pose, x, y, z)
                            .setColor(vr, vg, vb, 1.0f)
                            .setUv(u, v)
                            .setOverlay(OverlayTexture.NO_OVERLAY)
                            .setLight(packedLight)
                            .setNormal(nx, ny, nz);
                }
            }
        });
    }

    private static int orDefault(Integer value, int def) {
        return value != null ? value : def;
    }

    private static int adaptCountFor(Player player) {
        if (player.isLocalPlayer()) {
            return ClientAdaption.adaptedCount;
        }
        return WheelSlots.findWorn(player)
                .map(stack -> stack.get(ModDataComponents.WHEEL_DATA.get()))
                .map(ru.adaptionwheel.data.WheelData::adaptCount)
                .orElse(0);
    }

    private static int tintFor(int adaptCount) {
        float t = Math.min(1.0F, adaptCount / (float) WHEEL_GROWTH_REFERENCE);
        int r = (int) (0x4A + (0xFF - 0x4A) * t);
        int g = (int) (0x4A + (0xD7 - 0x4A) * t);
        int b = (int) (0x4A + (0x5C - 0x4A) * t);
        return r << 16 | g << 8 | b;
    }

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

    private static float easeOutBack(float t) {
        float u = t - 1.0F;
        return 1.0F + (BACK_C1 + 1.0F) * u * u * u + BACK_C1 * u * u;
    }

    public static final class MeshReloadListener extends SimplePreparableReloadListener<ObjMesh> {
        @Override
        protected ObjMesh prepare(ResourceManager resources, ProfilerFiller profiler) {
            Optional<Resource> resource = resources.getResource(WHEEL_MODEL);
            if (resource.isEmpty()) {
                LOGGER.warn("[{}] Wheel model {} is not in the resource manager; using fallback cube model",
                        AdaptionWheel.MODID, WHEEL_MODEL);
                return null;
            }
            try (BufferedReader reader = resource.get().openAsReader()) {
                ObjMesh mesh = ObjMesh.parse(reader);
                LOGGER.info("[{}] Wheel model {}: {} faces -> {} triangles, radius {} blocks",
                        AdaptionWheel.MODID, WHEEL_MODEL, mesh.faceCount(), mesh.triangleCount(), mesh.radius());
                return mesh;
            } catch (IOException | RuntimeException e) {
                LOGGER.error("[{}] Could not read wheel model {}; using fallback cube model",
                        AdaptionWheel.MODID, WHEEL_MODEL, e);
                return null;
            }
        }

        @Override
        protected void apply(ObjMesh mesh, ResourceManager resources, ProfilerFiller profiler) {
            wheelMesh = mesh;
            fallbackLogged = false;
        }
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
