package ru.adaptionwheel.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import org.slf4j.Logger;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.config.AdaptionConfig;
import ru.adaptionwheel.data.ModDataComponents;
import ru.adaptionwheel.item.ModItems;
import top.theillusivec4.curios.api.CuriosApi;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Optional;

@EventBusSubscriber(modid = AdaptionWheel.MODID,
        value = net.neoforged.api.distmarker.Dist.CLIENT)
public class WheelRenderer {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * The wheel's geometry, read from the OBJ shipped with the mod. It replaced the hand-written
     * 25-cube {@link DharmaChakraModel}, which is kept only as the fallback for a pack that hides
     * the model or a file that fails to parse — a wheel that silently disappears is worse than an
     * old one, and a wheel that silently changes shape is worse than both, so both paths are
     * logged.
     */
    private static final ResourceLocation WHEEL_MODEL = ResourceLocation.fromNamespaceAndPath(
            AdaptionWheel.MODID, "models/entity/dharma_chakra.obj");

    /** Null until a reload has read the OBJ, and again if reading it failed. */
    private static volatile ObjMesh wheelMesh;

    /**
     * One-shot, because "the OBJ path is not in use" has two causes that are otherwise
     * indistinguishable from the outside: the reload listener never ran (the event did not reach
     * this class) and the file failed to parse. The listener logs its own failure; this covers the
     * first case, which has no other evidence at all.
     */
    private static boolean fallbackLogged;

    /** The fallback model's radius, in model pixels. Only the old path uses it. */
    private static final float MODEL_RADIUS_PX = 4.5f;
    private static final float PX_PER_BLOCK = 16.0f;
    private static final float BOB_AMPLITUDE = 0.06f;
    private static final float BOB_SPEED = 0.08f;

    /**
     * One completed adaptation advances the wheel by one spoke — an eighth of a turn. The wheel
     * does <b>not</b> spin on its own: it holds still between completions, and the bob is the only
     * continuous motion left.
     */
    private static final float STEP_RADIANS = (float) (Math.PI / 4.0);

    /** How long one spoke takes to click round, in ticks. Short enough to read as a mechanism. */
    private static final float STEP_TICKS = 6.0f;

    /**
     * Ease-out-back constant: the click turns a little past the spoke and settles back, the way a
     * detent snaps into place. At 1.9 the overshoot is about 12% of a step — roughly five degrees
     * on a 45-degree turn — so it reads as a twitch rather than as the wheel missing its mark.
     */
    private static final float BACK_C1 = 1.9f;

    /**
     * A count change larger than this is not an adaptation — it is a wheel being loaded, granted
     * wholesale or reset — and animating it would whirl the wheel through dozens of turns. Those
     * snap instead, which is invisible: the model is eight-fold symmetric, so its phase modulo
     * 45 degrees does not matter.
     */
    private static final int MAX_ANIMATED_STEPS = 8;

    /** Alpha the wheel has always been drawn with; the old path keeps it verbatim. */
    private static final int TINT_ALPHA = 0xF3;

    /**
     * Light for the flat-shaded OBJ, in <b>world space</b> — above and slightly towards -X/-Z.
     * Vanilla bakes one brightness per cube face (up 1.0, down 0.5, sides 0.8/0.6); arbitrary
     * triangles have no face direction to look up, so the equivalent here is a fixed direction
     * dotted with the face normal.
     *
     * <p>It is deliberately not model space. The wheel spins, and a model-space light would carry
     * its own sun around with it, so the bright side would sweep across the beams once per turn and
     * the highlight would look painted on rather than lit. The pose's normal matrix is what turns
     * the face normal into world space; the camera rotation lives in the shader's model-view
     * matrix, not in this pose, so the result is world-aligned.
     */
    private static final Vector3f LIGHT = new Vector3f(-0.30f, 0.90f, -0.30f).normalize();

    /** Scratch for the transformed normal, so a wheel does not allocate 15k vectors a frame. */
    private static final Vector3f NORMAL_SCRATCH = new Vector3f();

    /**
     * Floor of the shade curve, so the underside reads as dark gold rather than as a hole.
     *
     * <p>It came down from 0.55 with the metal work. A high floor is what a matte object needs to
     * stay readable, and it is exactly what a metal must not have: polished gold is mostly dark
     * except where it catches the light, and at 0.55 the wheel looked like painted plastic with a
     * gold tint. The specular term below is what keeps the dark side from reading as a hole now.
     */
    private static final float AMBIENT = 0.32f;

    /**
     * Brushed-metal highlight: Blinn-Phong, one value per face, added on top of the diffuse shade.
     *
     * <p>This is the part that makes the wheel read as metal rather than as a gold-coloured solid.
     * A diffuse term alone cannot: it says which way a surface faces, never that it is <em>shiny</em>.
     * The highlight is computed in world space from the face normal and the direction to the camera,
     * so it sweeps around the ring as the wheel clicks round a spoke and stays put when the player
     * turns their head — which is what a real highlight does, and the opposite of what a model-space
     * light would do.
     *
     * <p>The exponent was measured on the real mesh rather than guessed: over the shipped geometry,
     * {@code pow(dot, 8)} puts a strong glint on 6% of the faces and {@code pow(dot, 26)} on 1.8%,
     * and at 26 the highlight mostly falls <em>between</em> facets — a sphere here is 16 segments,
     * so neighbouring faces are 12 degrees apart and a narrow lobe lands in the gap. At 10 it reads
     * as a few sparkles along the beams and the spheres, which is what polished metal does at this
     * size.
     */
    private static final float SPEC_STRENGTH = 0.90f;
    private static final float SPEC_POWER = 10.0f;

    /** Half-vector for {@link #SPEC_STRENGTH}; rebuilt per face, never allocated. */
    private static final Vector3f HALF_SCRATCH = new Vector3f();

    /** Scratch for the eye-to-wheel direction, likewise never allocated. */
    private static final Vector3f VIEW_SCRATCH = new Vector3f();

    @SubscribeEvent
    public static void registerReloadListener(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new WheelMeshReloadListener());
    }

    /**
     * Both caches are keyed by UUID and describe one session. A new world must not inherit the
     * previous one's adaptation count, or the first wheel seen would click round to a stale number
     * the moment it appeared.
     */
    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        WEARING_CACHE.clear();
        SPIN.clear();
    }

    @SubscribeEvent
    public static void render(RenderLivingEvent.Post<?, ?> event) {
        LivingEntity entity = event.getEntity();

        if (!(entity instanceof Player player)) {
            return;
        }
        if (!AdaptionConfig.WHEEL_ABOVE_HEAD.get() || !isWearingWheel(player)) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource bufferSource = event.getMultiBufferSource();

        float partialTick = event.getPartialTick();
        float height = entity.getBbHeight() + 0.45f;

        float wheelSize = (float) (double) AdaptionConfig.WHEEL_SIZE.get();
        ObjMesh mesh = wheelMesh;

        // Both models are sized by their own radius, so `wheelModelScale` keeps meaning "the
        // wheel's radius in blocks" whichever one is drawing. The old model is authored in model
        // pixels, which is why that path still converts through PX_PER_BLOCK.
        float scale = mesh != null
                ? wheelSize / mesh.radius()
                : wheelSize * PX_PER_BLOCK / MODEL_RADIUS_PX;

        // Growth is a smooth function of the adaptation count rather than a step per named tier:
        // the wheel visibly fills out as the player adapts, with no thresholds to cross and
        // nothing to be told about. Capped so a very late wheel cannot grow off the screen.
        int adaptCount = adaptCountFor(player);
        float growth = 1.0F + 0.55F * (adaptCount / (float) WHEEL_GROWTH_REFERENCE);
        scale *= Math.min(growth, MAX_GROWTH);
        int tint = tintFor(adaptCount);

        float time = player.level().getGameTime() + partialTick;
        float bob = Mth.sin(time * BOB_SPEED) * BOB_AMPLITUDE;

        // One direction for the whole wheel, not one per face. The wheel is 1.3 blocks wide and the
        // camera is at least a couple of blocks away, so the parallax across it is a few degrees —
        // far below what a highlight of this size can show, and it saves a vector per corner.
        Vector3f viewDir = viewDirection(entity, height + bob);

        poseStack.pushPose();

        poseStack.translate(0, height + bob, 0);

        // No idle spin and no body-yaw follow: the wheel hangs in world space and only advances a
        // spoke when an adaptation completes. The pose at RenderLivingEvent.Post has already been
        // popped back to the dispatcher's translate — the entity's own yaw lives inside
        // LivingEntityRenderer's push — so there is nothing to cancel here. The old -bodyYaw turn
        // only looked harmless because the wheel was spinning anyway; without the spin it would
        // read as the wheel turning whenever the player turned.
        poseStack.mulPose(Axis.YP.rotation(wheelAngle(player, adaptCount, time)));

        poseStack.scale(scale, scale, scale);

        if (mesh != null) {
            drawMesh(mesh, poseStack, bufferSource, tint, viewDir);
        } else {
            if (!fallbackLogged) {
                fallbackLogged = true;
                LOGGER.warn("[{}] No wheel mesh is loaded; drawing the old 25-cube model. If {} exists, "
                        + "the reload listener did not run", AdaptionWheel.MODID, WHEEL_MODEL);
            }
            drawFallback(poseStack, bufferSource, tint);
        }

        poseStack.popPose();
    }

    /**
     * The wheel does not spin on its own: it clicks round one spoke — an eighth of a turn — when an
     * adaptation completes, and holds still in between. The step is deliberately snappy: an
     * ease-out-back curve turns the wheel slightly past the spoke and settles it back, like a
     * detent, instead of gliding to a stop. The bob is the only continuous motion left.
     *
     * <p>The angle is kept as a running total rather than derived from the count, because the model
     * is eight-fold symmetric — only the step matters, so a wheel loaded at 300 adaptations has no
     * phase to spin up to, and a hand-over cannot make it whirl.
     */
    private static float wheelAngle(Player player, int adaptCount, float time) {
        SpinState state = SPIN.computeIfAbsent(player.getUUID(), id -> new SpinState(adaptCount));

        if (state.count != adaptCount) {
            int delta = adaptCount - state.count;
            state.count = adaptCount;
            if (Math.abs(delta) <= MAX_ANIMATED_STEPS) {
                state.from = state.angle;
                state.to = state.angle + delta * STEP_RADIANS;
                state.start = time;
            } else {
                // A wheel being loaded, granted wholesale or reset: snap to zero. The phase is
                // invisible thanks to the symmetry, and animating 300 steps would whirl for a
                // minute.
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

    /**
     * Per-player click animation. {@code angle} is the angle actually drawn this frame, which is
     * what a second completion arriving mid-click has to continue from.
     */
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

    /**
     * Pushes the OBJ's triangle soup into the entity vertex format.
     *
     * <p>Three things multiply into one vertex colour, because the entity shader gives exactly that
     * one channel and no custom shader is wanted: the artist's material colour (gold ring, bronze
     * beams, carried in the mesh as vertex colours), the adaptation tint (grey at nothing adapted,
     * warming to gold), and the shading. The texture is a neutral brushed steel that only adds
     * grain — a gold sprite under a gold vertex colour would have been gold squared, and the
     * material colours are the part that came out of Blender.
     */
    private static void drawMesh(ObjMesh mesh, PoseStack poseStack, MultiBufferSource buffers, int tint, Vector3f viewDir) {
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(DharmaChakraModel.TEXTURE));
        PoseStack.Pose pose = poseStack.last();

        float red = ((tint >> 16) & 0xFF) / 255.0F;
        float green = ((tint >> 8) & 0xFF) / 255.0F;
        float blue = (tint & 0xFF) / 255.0F;
        float alpha = (TINT_ALPHA & 0xFF) / 255.0F;

        // The half-vector between the light and the eye is the same for every face of this wheel,
        // because both are fixed for the frame; only the normal moves.
        HALF_SCRATCH.set(LIGHT).add(viewDir).normalize();

        // Four corners per triangle, not three: the render type's mode is QUADS, and three vertices
        // per triangle would be grouped across triangle boundaries into slivers that leave holes in
        // the mesh. See ObjMesh.quadCorners() — this is the one place the packing matters.
        Matrix3f normalMatrix = pose.normal();
        float[] corners = mesh.quadCorners();
        for (int i = 0; i < corners.length; i += ObjMesh.STRIDE * 4) {
            float nx = corners[i + 5];
            float ny = corners[i + 6];
            float nz = corners[i + 7];

            // One normal for all four corners: every triangle is flat-shaded, so its corners share
            // the face normal, and the world-space shade is therefore constant across the quad.
            NORMAL_SCRATCH.set(nx, ny, nz).mul(normalMatrix).normalize();
            float shade = AMBIENT + (1.0F - AMBIENT) * Math.max(0.0F, NORMAL_SCRATCH.dot(LIGHT));
            float facing = Math.max(0.0F, NORMAL_SCRATCH.dot(HALF_SCRATCH));
            float light = shade + SPEC_STRENGTH * (float) Math.pow(facing, SPEC_POWER);

            for (int corner = 0; corner < 4; corner++) {
                int o = i + corner * ObjMesh.STRIDE;
                // The artist's material, carried in the mesh: gold for the ring and the spheres,
                // bronze for the beams. Multiplied by the tint, so the wheel still starts grey and
                // warms to gold as it fills out — the metal decides the hue, the adaptation count
                // decides how much of it there is.
                float mr = corners[o + 8];
                float mg = corners[o + 9];
                float mb = corners[o + 10];
                consumer.addVertex(pose, corners[o], corners[o + 1], corners[o + 2])
                        .setColor(mr * red * light, mg * green * light, mb * blue * light, alpha)
                        .setUv(corners[o + 3], corners[o + 4])
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(LightTexture.FULL_BRIGHT)
                        .setNormal(NORMAL_SCRATCH.x(), NORMAL_SCRATCH.y(), NORMAL_SCRATCH.z());
            }
        }
    }

    /**
     * Unit vector from the wheel's centre towards the camera, in world space.
     *
     * <p>Read off the main camera rather than derived from the pose: at {@code RenderLivingEvent.Post}
     * the pose holds the entity's position and its own rotations, and the camera's orientation lives
     * in the shader's model-view matrix, which is not reachable from here at all. The camera's world
     * position is, and a highlight only needs the direction between two points.
     */
    private static Vector3f viewDirection(LivingEntity entity, float height) {
        Vec3 eye = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        VIEW_SCRATCH.set(
                (float) (eye.x - entity.getX()),
                (float) (eye.y - entity.getY() - height),
                (float) (eye.z - entity.getZ()));
        if (VIEW_SCRATCH.lengthSquared() < 1.0e-6F) {
            // The camera is inside the wheel: no meaningful direction, and normalizing would hand
            // the draw loop a NaN that takes the whole mesh off the screen.
            return VIEW_SCRATCH.set(0.0F, 0.0F, 1.0F);
        }
        return VIEW_SCRATCH.normalize();
    }

    private static void drawFallback(PoseStack poseStack, MultiBufferSource buffers, int tint) {
        ModelPart rootPart = DharmaChakraModel.getBakedRoot();
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(DharmaChakraModel.TEXTURE));
        rootPart.render(poseStack, consumer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                tintWithAlpha(tint, TINT_ALPHA));
    }

    /**
     * Reads the OBJ during the resource reload rather than on the first frame a wheel is seen: the
     * file is 260 KB and 8.4k lines, and a parse in the middle of a render tick is a visible hitch
     * for something that can be done behind the loading screen. It also means a resource pack that
     * replaces the model takes effect on F3+T like every other asset.
     */
    private static final class WheelMeshReloadListener extends SimplePreparableReloadListener<ObjMesh> {

        @Override
        protected ObjMesh prepare(ResourceManager resources, ProfilerFiller profiler) {
            Optional<Resource> resource = resources.getResource(WHEEL_MODEL);
            if (resource.isEmpty()) {
                LOGGER.error("[{}] Wheel model {} is not in the resource manager; the wheel above the head "
                        + "will use the old 25-cube model", AdaptionWheel.MODID, WHEEL_MODEL);
                return null;
            }

            try (BufferedReader reader = resource.get().openAsReader()) {
                ObjMesh mesh = ObjMesh.parse(reader);
                LOGGER.info("[{}] Wheel model {}: {} faces -> {} triangles, radius {} blocks",
                        AdaptionWheel.MODID, WHEEL_MODEL, mesh.faceCount(), mesh.triangleCount(), mesh.radius());
                return mesh;
            } catch (IOException | RuntimeException e) {
                LOGGER.error("[{}] Could not read wheel model {}; the wheel above the head will use the old "
                        + "25-cube model", AdaptionWheel.MODID, WHEEL_MODEL, e);
                return null;
            }
        }

        @Override
        protected void apply(ObjMesh mesh, ResourceManager resources, ProfilerFiller profiler) {
            wheelMesh = mesh;
        }
    }

    /** The wearer's adaptation count, read the same way for every player a client draws. */
    private static int adaptCountFor(Player player) {
        if (player.isLocalPlayer()) {
            return ClientAdaption.adaptedCount;
        }

        return CuriosApi.getCuriosInventory(player)
                .flatMap(handler -> handler.findFirstCurio(ModItems.MAHORAGA_WHEEL.get()))
                .map(top.theillusivec4.curios.api.SlotResult::stack)
                .map(stack -> stack.get(ModDataComponents.WHEEL_DATA.get()))
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

    private static int tintWithAlpha(int rgb, int alpha) {
        return (alpha & 0xFF) << 24 | (rgb & 0x00FFFFFF);
    }

    /** Adaptations at which the wheel has visibly filled out. */
    private static final int WHEEL_GROWTH_REFERENCE = 60;
    /** Upper bound on growth, so a very late wheel stays on screen. */
    private static final float MAX_GROWTH = 1.55F;

    private static final java.util.Map<java.util.UUID, long[]> WEARING_CACHE = new java.util.HashMap<>();
    private static final long CACHE_TTL_TICKS = 10;

    /** Per-player click state; see {@link #wheelAngle}. */
    private static final java.util.Map<java.util.UUID, SpinState> SPIN = new java.util.HashMap<>();

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
