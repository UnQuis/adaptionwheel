package ru.adaptionwheel.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;

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
     * Scratch vectors reused every corner so a wheel does not allocate ~15k vectors a frame.
     */
    private static final Vector3f NORMAL_SCRATCH = new Vector3f();
    private static final Vector3f LIGHT_SCRATCH = new Vector3f();
    private static final Vector3f VIEW_SCRATCH = new Vector3f();
    private static final Vector3f HALF_SCRATCH = new Vector3f();

    /**
     * Metal lighting constants. The key difference from "plastic gold" is that a metal's diffuse
     * term is dark and contrasty (polished gold is black in the shade, not middle-grey) while the
     * specular term is a separate additive highlight in the colour of the sky / light source —
     * NOT a multiplier on the material. That is why the earlier `bright = 1 + spec` read as
     * glossy plastic: it lifted everything evenly instead of adding a tight bright fleck.
     *
     * SPEC_POWER of 48 is a narrower lobe than the previous 16 — gives a sharp pinprick glint
     * rather than a wide glossy sheen. SPEC_STRENGTH is the peak intensity of that fleck.
     * No ambient headlamp underground: in pure torchlight the glint follows the sun-halfvector
     * dimmed down, which reads as torchlight catching edges rather than as even plastic shine.
     */
    private static final float METAL_AMBIENT = 0.18f;
    private static final float SPEC_STRENGTH = 0.95f;
    private static final float SPEC_POWER = 48.0f;

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

        // Real light from the world: sky (sun/moon) + block (torches, glowstone, etc.) at the
        // entity's position, so the wheel responds to night, caves, torches and beacons the way
        // every other entity in the game does.
        // Packed light coordinates, 4 bits block | 4 bits sky in each short. The block term is
        // measured at the entity's feet the way every living renderer samples it; the sky term is
        // derived from the level-dependent magic value (0..1) so darkness/bad-omen/darkness effects
        // darken the wheel the same way they darken the entity.
        int blockI = entity.level().getMaxLocalRawBrightness(entity.blockPosition());
        int skyI = (int)(entity.getLightLevelDependentMagicValue() * 15.0f);
        int packedLight = LightTexture.pack(blockI, skyI);

        poseStack.pushPose();

        poseStack.translate(0, height + bob, 0);

        // No idle spin and no body-yaw follow: the wheel hangs in world space and only advances a
        // spoke when an adaptation completes. The pose at RenderLivingEvent.Post has already been
        // popped back to the dispatcher's translate — the entity's own yaw lives inside
        // LivingEntityRenderer's push — so there is nothing to cancel here. The old -bodyYaw turn
        // only looked harmless because the wheel was spinning anyway; without the spin it would
        // read as the wheel turning whenever the player turned.
        float spinAngle = wheelAngle(player, adaptCount, time);
        poseStack.mulPose(Axis.YP.rotation(spinAngle));

        poseStack.scale(scale, scale, scale);

        if (mesh != null) {
            drawMesh(mesh, poseStack, bufferSource, tint, packedLight, entity, height + bob, spinAngle);
        } else {
            if (!fallbackLogged) {
                fallbackLogged = true;
                LOGGER.warn("[{}] No wheel mesh is loaded; drawing the old 25-cube model. If {} exists, "
                        + "the reload listener did not run", AdaptionWheel.MODID, WHEEL_MODEL);
            }
            drawFallback(poseStack, bufferSource, tint, packedLight);
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
     * <p>Two things multiply into the vertex colour: the artist's material (gold ring/spheres,
     * bronze beams — baked into the mesh as per-vertex colours at export time) and the adaptation
     * tint (grey at nothing adapted, warming to gold as the wheel fills out). The texture
     * ({@code dharma_chakra.png}) is a neutral brushed-steel grain that adds micro-detail only —
     * a gold sprite under a gold vertex colour would be gold squared.
     *
     * <p>Base lighting is the vanilla entity pipeline (diffuse from the sun/moon, sky/block
     * lightmap from torches and night, fog). On top of that a small Blinn-Phong specular boost is
     * multiplied into the vertex colour — using the real sun direction from the client level and
     * the camera's view direction — so polished gold glints under torchlight and at the sun the
     * way metal should. Per-corner normals are real: smooth on the re-authored spheres, flat on
     * the faceted torus and bronze beams.
     */
    private static void drawMesh(ObjMesh mesh, PoseStack poseStack, MultiBufferSource buffers, int tint, int packedLight, LivingEntity entity, float wheelCenterY, float spinAngle) {
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(DharmaChakraModel.TEXTURE));
        PoseStack.Pose pose = poseStack.last();

        float red = ((tint >> 16) & 0xFF) / 255.0F;
        float green = ((tint >> 8) & 0xFF) / 255.0F;
        float blue = (tint & 0xFF) / 255.0F;
        float alpha = (TINT_ALPHA & 0xFF) / 255.0F;

        Matrix3f normalMatrix = pose.normal();

        // Sun/moon direction in world space. At night we flip to the moon; underground we still
        // use the world sun direction (the lightmap takes care of darkening) so the highlight
        // does not invent an extra light above the player's head.
        ClientLevel level = (ClientLevel) entity.level();
        float celestialAngle = level.getSunAngle(1.0f);
        float sunHoriz = Mth.cos(celestialAngle);
        float sunY = Mth.sin(celestialAngle);
        float sunZ = -sunHoriz;
        if (sunY < -0.1f) {
            sunZ = sunHoriz;
            sunY = -sunY;
        }
        float wLightX = 0.0f, wLightY = Math.max(sunY, 0.20f), wLightZ = sunZ;

        // View direction from wheel centre to camera, in world space.
        Vec3 eye = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        float wViewX = (float)(eye.x - entity.getX());
        float wViewY = (float)(eye.y - entity.getY() - wheelCenterY);
        float wViewZ = (float)(eye.z - entity.getZ());
        if (wViewX*wViewX + wViewY*wViewY + wViewZ*wViewZ < 1.0e-6f) {
            wViewX = 0.0f; wViewY = 0.0f; wViewZ = 1.0f;
        }

        // The pose stack has rotated the mesh by +spinAngle around Y, and normalMatrix carries
        // that rotation into the normals we feed the shader. That means every normal coming out
        // of the mesh has been turned by +spinAngle, but the world light/view vectors have not.
        // Rotate them by -spinAngle around Y to bring them into the same frame as the normals,
        // otherwise N.L and the half-vector are computed between vectors in different bases and
        // the highlight sweeps to the wrong side of the wheel.
        float cos = Mth.cos(-spinAngle);
        float sin = Mth.sin(-spinAngle);
        LIGHT_SCRATCH.set(
                wLightX * cos - wLightZ * sin,
                wLightY,
                wLightX * sin + wLightZ * cos).normalize();
        float vLen = (float)Math.sqrt(wViewX*wViewX + wViewY*wViewY + wViewZ*wViewZ);
        VIEW_SCRATCH.set(
                (wViewX * cos - wViewZ * sin) / vLen,
                wViewY / vLen,
                (wViewX * sin + wViewZ * cos) / vLen);

        // Half vector for Blinn-Phong, now in the rotated frame.
        HALF_SCRATCH.set(LIGHT_SCRATCH).add(VIEW_SCRATCH).normalize();

        // Extract the 0..1 sky amount from the packed light so we can dim both diffuse and
        // specular when the sky is not contributing (caves, deep night). Block light is added
        // to the diffuse term directly: torches tint the wheel warm the way they tint every
        // entity in the game.
        int blockI = LightTexture.block(packedLight);
        int skyI = LightTexture.sky(packedLight);
        float skyAmount = skyI / 15.0f;
        float blockAmount = blockI / 15.0f;
        // Block light is warm orange (torch colour ~(1.0, 0.65, 0.33)); its contribution is
        // ambient-ish because the engine has already composited every nearby source into one
        // brightness value and we have no direction to attach a highlight to.
        float blockWarmR = 1.00f, blockWarmG = 0.65f, blockWarmB = 0.33f;

        float[] corners = mesh.quadCorners();
        for (int i = 0; i < corners.length; i += ObjMesh.STRIDE * 4) {
            for (int corner = 0; corner < 4; corner++) {
                int o = i + corner * ObjMesh.STRIDE;
                float mr = corners[o + 8];
                float mg = corners[o + 9];
                float mb = corners[o + 10];

                // Per-corner normal in world/pose space.
                NORMAL_SCRATCH.set(corners[o + 5], corners[o + 6], corners[o + 7]).mul(normalMatrix);
                if (NORMAL_SCRATCH.lengthSquared() < 1.0e-6F) {
                    NORMAL_SCRATCH.set(0.0F, 1.0F, 0.0F);
                } else {
                    NORMAL_SCRATCH.normalize();
                }

                // --- DIFFUSE: low ambient floor + N·L sun term, weighted by how much sky is
                // visible, plus a warm block-light ambient. Dark in shade = metal, not plastic.
                float nDotL = Math.max(0.0f, NORMAL_SCRATCH.dot(LIGHT_SCRATCH));
                float skyDiffuse = METAL_AMBIENT + (1.0f - METAL_AMBIENT) * nDotL;
                skyDiffuse *= skyAmount;
                float blockDiffuse = METAL_AMBIENT * blockAmount;

                // Per-channel diffuse: material * tint * (skyDiffuse neutral + blockDiffuse warm).
                float baseR = mr * red * skyDiffuse + mr * blockWarmR * blockDiffuse;
                float baseG = mg * green * skyDiffuse + mg * blockWarmG * blockDiffuse;
                float baseB = mb * blue * skyDiffuse + mb * blockWarmB * blockDiffuse;

                // --- SPECULAR: tight Blinn-Phong fleck, additive, in the colour of the dominant
                // light (sky white by day, moon blue-grey at night, torch warm underground).
                // This is the critical difference from plastic: specular is NOT multiplying the
                // diffuse term and tinting everything brighter; it is a separate bright fleck.
                float nDotH = Math.max(0.0f, NORMAL_SCRATCH.dot(HALF_SCRATCH));
                float spec = SPEC_STRENGTH * (float)Math.pow(nDotH, SPEC_POWER);
                spec *= Math.max(skyAmount, blockAmount * 0.85f); // glint dies with no light
                // Specular tints: sun = warm white, moon = cool blue, torch = same orange as diffuse
                float specR, specG, specB;
                if (blockAmount > skyAmount + 0.15f) {
                    specR = blockWarmR; specG = blockWarmG; specB = blockWarmB;
                } else if (sunY < 0.25f) {
                    // Night: pale moonlight
                    specR = 0.70f; specG = 0.78f; specB = 1.00f;
                } else {
                    specR = 1.00f; specG = 0.95f; specB = 0.82f;
                }
                float cr = Math.min(1.0f, baseR + spec * specR);
                float cg = Math.min(1.0f, baseG + spec * specG);
                float cb = Math.min(1.0f, baseB + spec * specB);

                consumer.addVertex(pose, corners[o], corners[o + 1], corners[o + 2])
                        .setColor(cr, cg, cb, alpha)
                        .setUv(corners[o + 3], corners[o + 4])
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(packedLight)
                        .setNormal(NORMAL_SCRATCH.x(), NORMAL_SCRATCH.y(), NORMAL_SCRATCH.z());
            }
        }
    }

    private static void drawFallback(PoseStack poseStack, MultiBufferSource buffers, int tint, int packedLight) {
        ModelPart rootPart = DharmaChakraModel.getBakedRoot();
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(DharmaChakraModel.TEXTURE));
        rootPart.render(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY,
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
