package ru.adaptionwheel.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
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
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.slf4j.Logger;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.category.Concepts;
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
     * SPEC_POWER was 48 while the wheel was polished gold — a sharp pinprick glint. The re-authored
     * wheel is <b>corroded</b>: its own material carries a 4K roughness map of pitted, scratched
     * metal, and a mirror-tight lobe on that surface reads as chrome bolted onto rust. 14 is a
     * broad sheen that matches a rough metal, and the strength came down with it so the sheen
     * brightens the surface instead of covering it.
     *
     * <p>RIM is the other half of the authored material: it ends in an emissive Fresnel outline
     * (Layer Weight → ramp 0.32..0.78 → strength 12, colour 1.0/0.85/0.35), which is what draws
     * the bright gold edge around every sphere and beam in the reference render. An emission
     * cannot be expressed through a vertex colour — the entity shader multiplies it with the
     * texture — so the rim is approximated as a Fresnel term added to that colour. It is dimmed
     * with the light around it (with a floor, because the authored material glows in the dark
     * too), and at grazing angles it brightens the silhouette to a pale gold rather than to white.
     *
     * <p>No ambient headlamp underground: in pure torchlight the glint follows the sun-halfvector
     * dimmed down, which reads as torchlight catching edges rather than as even plastic shine.
     */
    private static final float METAL_AMBIENT = 0.18f;
    private static final float SPEC_STRENGTH = 0.55f;
    private static final float SPEC_POWER = 14.0f;

    /** Fresnel rim, see the block above. {@code RIM_POWER} shapes the falloff towards the edge. */
    private static final float RIM_STRENGTH = 0.45f;
    private static final float RIM_POWER = 3.0f;
    private static final float RIM_R = 1.00f;
    private static final float RIM_G = 0.85f;
    private static final float RIM_B = 0.35f;

    /**
     * The wheel's own shader: Cook-Torrance metal against an analytic environment, so the wheel
     * actually <b>reflects</b> something. It replaces the per-corner Blinn-Phong baked into the
     * vertex colour — that model could only add a highlight, because a vertex colour multiplies the
     * texture and can never carry a reflected image.
     *
     * <p>The environment is evaluated per pixel from the reflected ray rather than sampled from the
     * framebuffer: the wheel is drawn in the middle of the world pass, where the target it would
     * read is the one it is writing, and that read is undefined. A sky gradient, a sun disc, a
     * bright horizon band and two studio strips are enough to make the metal read as metal, and
     * they follow the level's own sky colour and sun angle.
     */
    private static final ResourceLocation WHEEL_SHADER = ResourceLocation.fromNamespaceAndPath(
            AdaptionWheel.MODID, "wheel_metal");

    /**
     * The reflected light sources, as individual uniforms.
     *
     * <p><b>Eight separate vec4s and not a {@code vec4[8]} array.</b> A shader uniform declared in
     * the JSON is uploaded by {@code Uniform.uploadAsFloat}, which picks its {@code glUniform}
     * variant from the declared {@code count} and accepts only 1..4 — a {@code count} of 32 for an
     * array is rejected at load ("count value is not in the range of 1 to 4") and the whole shader
     * fails with it. So the array is flattened into named uniforms and the fragment stage sums
     * eight calls instead of looping. Ugly, and the only thing that works through this API.
     */
    private static final String[] LIGHT_POS_UNIFORMS = new String[WheelLights.MAX];
    private static final String[] LIGHT_COL_UNIFORMS = new String[WheelLights.MAX];

    static {
        for (int i = 0; i < WheelLights.MAX; i++) {
            LIGHT_POS_UNIFORMS[i] = "WheelLightPos" + i;
            LIGHT_COL_UNIFORMS[i] = "WheelLightCol" + i;
        }
    }

    /** Null until {@link #registerShaders} has compiled it; the old CPU-lit path covers that. */
    private static volatile ShaderInstance wheelShader;

    /** One-shot, because a shader that failed to compile is otherwise silent. */
    private static boolean shaderMissingLogged;

    @SubscribeEvent
    public static void registerReloadListener(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new WheelMeshReloadListener());
    }

    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(), WHEEL_SHADER, DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL),
                    instance -> wheelShader = instance);
        } catch (IOException e) {
            // A wheel drawn with the old lighting model is worse than a reflecting one and much
            // better than none: the fallback below is what keeps a broken driver from deleting the
            // wheel from the game entirely.
            LOGGER.error("[{}] Could not compile the wheel metal shader {}; the wheel will use the "
                    + "old per-vertex lighting", AdaptionWheel.MODID, WHEEL_SHADER, e);
        }
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
        WheelLights.clear();
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

        // Real light from the world, sampled the way vanilla samples an entity's: the block and the
        // sky LAYER, packed 4 bits each, at the wheel's own block rather than at the feet — the
        // wheel is the surface being lit, and a torch above the player's head is exactly the torch
        // its metal reflects.
        //
        // The sky term must be the sky layer and not `getLightLevelDependentMagicValue()`, which is
        // what it used to be. That value is the COMBINED block+sky brightness through the gamma
        // curve (`getMaxLocalRawBrightness`, then `f / (4 - 3f)`, then lerped towards the
        // dimension's ambient light), so a torch-lit cave scores close to 1.0 in it. The shader
        // reads the sky slot to decide how much sky there is to reflect, and with the combined
        // value in that slot the wheel mirrored a full daylight sky underground — the one place the
        // reflection was supposed to hand over to the torches. The raw layers answer the question
        // the shader is actually asking: how much sky reaches this block.
        BlockPos lightPos = BlockPos.containing(entity.getX(), entity.getY() + height, entity.getZ());
        int blockI = entity.level().getBrightness(LightLayer.BLOCK, lightPos);
        int skyI = entity.level().getBrightness(LightLayer.SKY, lightPos);
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
            if (wheelShader != null && AdaptionConfig.WHEEL_SHADER_ENABLED.get()) {
                drawMeshShader(mesh, poseStack, tint, packedLight, entity, partialTick);
            } else {
                if (wheelShader == null && !shaderMissingLogged) {
                    shaderMissingLogged = true;
                    LOGGER.warn("[{}] The wheel metal shader {} is not loaded; drawing the wheel with "
                            + "the old per-vertex lighting", AdaptionWheel.MODID, WHEEL_SHADER);
                }
                drawMesh(mesh, poseStack, bufferSource, tint, packedLight, entity, height + bob, spinAngle, partialTick);
            }
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

    /** Roughness of the corroded metal the wheel is made of. */
    private static final float METAL_ROUGHNESS = 0.42f;

    /** A quad becomes two triangles in the same winding the QUADS path used. */
    private static final int[] TRIANGLE_CORNERS = {0, 1, 2, 0, 2, 3};

    /** The corners are already in world space, so {@code addVertex} must not transform them again. */
    private static final Matrix4f IDENTITY = new Matrix4f();
    private static final Vector3f POS_SCRATCH = new Vector3f();
    private static final Vector3f SHADER_NORMAL_SCRATCH = new Vector3f();

    /**
     * Draws the wheel with {@code wheel_metal}: a Cook-Torrance metal that reflects an analytic
     * environment, evaluated per pixel rather than baked into vertex colours.
     *
     * <p><b>Why an immediate draw and not a {@code RenderType}.</b> A render type batches every
     * quad sharing it under one set of uniforms, so per-wheel values — the sun direction, the sky
     * colour, the eye — would have to be vertex attributes. Issuing one draw per wheel lets a
     * single program serve every player at once; a swing-sized wheel is one draw call, and the
     * vertex buffer is rebuilt each frame because the pose (spin, bob, scale) is baked into it.
     *
     * <p><b>The positions are world-space and the matrix is view-projection.</b> The entity
     * renderer's pose stack is already camera-relative (it translated by {@code entity - camera}),
     * so the camera position has to be added back on the CPU — the shader needs a real world
     * position to reflect the sun and the sky around. {@code WheelProj} is
     * {@code projection * modelView} because in 1.21.1 the camera's <i>view</i> lives in the
     * model-view matrix, not in the projection: a projection-only matrix places the wheel at
     * unviewed world coordinates, off screen, with no error anywhere.
     */
    private static void drawMeshShader(ObjMesh mesh, PoseStack poseStack, int tint, int packedLight,
                                       LivingEntity entity, float partialTick) {
        ShaderInstance shader = wheelShader;
        if (shader == null) {
            return;
        }

        PoseStack.Pose pose = poseStack.last();
        Matrix4f poseMatrix = pose.pose();
        Matrix3f normalMatrix = pose.normal();

        ClientLevel level = (ClientLevel) entity.level();
        Vec3 eye = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        float eyeX = (float)eye.x;
        float eyeY = (float)eye.y;
        float eyeZ = (float)eye.z;

        // Same celestial geometry as the lit path: the sun travels along X, and the moon is the
        // opposite point of the sky, so "night" is decided before the flip.
        float celestialAngle = level.getSunAngle(partialTick);
        float sunY = Mth.cos(celestialAngle);
        boolean night = sunY < 0.0f;

        float blockAmount = LightTexture.block(packedLight) / 15.0f;
        float skyAmount = LightTexture.sky(packedLight) / 15.0f;
        Vec3 skyColor = level.getSkyColor(eye, partialTick);

        Matrix4f viewProj = new Matrix4f(RenderSystem.getProjectionMatrix())
                .mul(RenderSystem.getModelViewMatrix());
        shader.safeGetUniform("WheelProj").set(viewProj);
        shader.safeGetUniform("WheelCamera").set(eyeX, eyeY, eyeZ);
        // The wheel's own origin in world space: the pose stack's origin is the wheel's centre, so
        // transforming (0,0,0) and adding the eye is where it actually sits. The fragment stage
        // uses it to keep the corrosion noise anchored to the wheel instead of to the world.
        POS_SCRATCH.set(0.0F, 0.0F, 0.0F);
        poseMatrix.transformPosition(POS_SCRATCH);
        float centreX = POS_SCRATCH.x + eyeX;
        float centreY = POS_SCRATCH.y + eyeY;
        float centreZ = POS_SCRATCH.z + eyeZ;
        shader.safeGetUniform("WheelCentre").set(centreX, centreY, centreZ);
        shader.safeGetUniform("WheelSun").set(-Mth.sin(celestialAngle), night ? -sunY : sunY, 0.0f);
        shader.safeGetUniform("WheelSky").set((float)skyColor.x, (float)skyColor.y, (float)skyColor.z, skyAmount);
        shader.safeGetUniform("WheelParams").set(METAL_ROUGHNESS, night ? 1.0f : 0.0f, blockAmount, 1.0f);
        float groundLight = 0.35f + 0.65f * Math.max(skyAmount, blockAmount);
        shader.safeGetUniform("WheelGround").set(
                0.20f * groundLight, 0.17f * groundLight, 0.13f * groundLight, 1.0f);

        // The wheel's own light, once the wearer has adapted to darkness. Modulated by how dark it
        // is where the wheel is: an emissive gold wheel in full noon sunlight is not a light, it is
        // a bug report, while in the dark it is the one thing in the scene that is not lit by
        // something else. The lightmap lift and this are two halves of one adaptation -- the lift
        // makes the world visible, this makes the wheel a source in it.
        float glow = (entity instanceof Player wearer ? darknessGlowFor(wearer) : 0.0F)
                * (0.35f + 0.65f * (1.0f - Math.max(skyAmount, blockAmount)));
        shader.safeGetUniform("WheelGlow").set(glow);

        // Reflected light sources: the glowing blocks within eight blocks, so the metal has
        // something to show indoors. Missing slots are handed over as intensity zero, which the
        // shader early-outs on.
        WheelLights.Source[] lights = WheelLights.around(level, centreX, centreY, centreZ);
        for (int i = 0; i < WheelLights.MAX; i++) {
            if (i < lights.length) {
                WheelLights.Source light = lights[i];
                shader.safeGetUniform(LIGHT_POS_UNIFORMS[i]).set(light.x(), light.y(), light.z(), light.intensity());
                shader.safeGetUniform(LIGHT_COL_UNIFORMS[i]).set(light.red(), light.green(), light.blue(), light.radius());
            } else {
                shader.safeGetUniform(LIGHT_POS_UNIFORMS[i]).set(0.0F, 0.0F, 0.0F, 0.0F);
                shader.safeGetUniform(LIGHT_COL_UNIFORMS[i]).set(0.0F, 0.0F, 0.0F, 0.0F);
            }
        }

        float red = ((tint >> 16) & 0xFF) / 255.0F;
        float green = ((tint >> 8) & 0xFF) / 255.0F;
        float blue = (tint & 0xFF) / 255.0F;
        float alpha = (TINT_ALPHA & 0xFF) / 255.0F;

        // The world pass leaves face culling on, and a fullscreen-style immediate draw is the
        // classic place it silently eats geometry; the wheel's own winding is correct but this is
        // cheap insurance and matches the cutout render type the lit path uses.
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        try {
            shader.setSampler("Sampler0",
                    Minecraft.getInstance().getTextureManager().getTexture(DharmaChakraModel.TEXTURE).getId());
            shader.apply();

            BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES,
                    DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);
            float[] corners = mesh.quadCorners();
            for (int i = 0; i < corners.length; i += ObjMesh.STRIDE * 4) {
                for (int corner : TRIANGLE_CORNERS) {
                    int o = i + corner * ObjMesh.STRIDE;
                    POS_SCRATCH.set(corners[o], corners[o + 1], corners[o + 2]);
                    poseMatrix.transformPosition(POS_SCRATCH);
                    POS_SCRATCH.add(eyeX, eyeY, eyeZ);

                    SHADER_NORMAL_SCRATCH.set(corners[o + 5], corners[o + 6], corners[o + 7]).mul(normalMatrix);
                    if (SHADER_NORMAL_SCRATCH.lengthSquared() < 1.0e-6F) {
                        SHADER_NORMAL_SCRATCH.set(0.0F, 1.0F, 0.0F);
                    } else {
                        SHADER_NORMAL_SCRATCH.normalize();
                    }

                    buffer.addVertex(IDENTITY, POS_SCRATCH.x, POS_SCRATCH.y, POS_SCRATCH.z)
                            .setUv(corners[o + 3], corners[o + 4])
                            .setColor(red, green, blue, alpha)
                            .setNormal(SHADER_NORMAL_SCRATCH.x(), SHADER_NORMAL_SCRATCH.y(), SHADER_NORMAL_SCRATCH.z());
                }
            }
            BufferUploader.draw(buffer.buildOrThrow());
        } finally {
            shader.clear();
            RenderSystem.enableCull();
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
    private static void drawMesh(ObjMesh mesh, PoseStack poseStack, MultiBufferSource buffers, int tint, int packedLight, LivingEntity entity, float wheelCenterY, float spinAngle, float partialTick) {
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(DharmaChakraModel.TEXTURE));
        PoseStack.Pose pose = poseStack.last();

        float red = ((tint >> 16) & 0xFF) / 255.0F;
        float green = ((tint >> 8) & 0xFF) / 255.0F;
        float blue = (tint & 0xFF) / 255.0F;
        float alpha = (TINT_ALPHA & 0xFF) / 255.0F;

        Matrix3f normalMatrix = pose.normal();

        // Sun/moon direction in world space, from the same angle vanilla uses to draw the sun.
        // **The axis is the part that is easy to get wrong, and it is invisible at noon.** Vanilla
        // places the sun quad at (0, 100, 0) inside `YP(-90) * XP(getSunAngle)`, and rotating that
        // point through the pair gives (-sin, cos, 0) — so the sun travels along **X**, and at
        // noon (angle 0) it is straight up. A light vector of (0, cos, -sin) is 90 degrees off in
        // azimuth: a flat disc whose faces point up hides that completely, and only the spheres
        // show it, which is why it survived a pass of "looks fine in game".
        ClientLevel level = (ClientLevel) entity.level();
        float celestialAngle = level.getSunAngle(partialTick);
        float sunY = Mth.cos(celestialAngle);
        // Below the horizon the sun is not the light source any more; the moon is, and it sits at
        // exactly the opposite point of the sky. Flipping the whole vector is what "the moon is up"
        // means, and it keeps the highlight on the thing that is actually shining.
        boolean night = sunY < 0.0f;
        float wLightX = -Mth.sin(celestialAngle);
        float wLightY = night ? -sunY : sunY;
        float wLightZ = 0.0f;

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
                } else if (night) {
                    // Pale moonlight. It has to be the flipped-sun flag, not an elevation test:
                    // after the flip the light is always above the horizon, so "low" would have
                    // meant dawn and dusk, i.e. the two moments the light is at its warmest.
                    specR = 0.70f; specG = 0.78f; specB = 1.00f;
                } else {
                    specR = 1.00f; specG = 0.95f; specB = 0.82f;
                }
                // --- RIM: the authored material ends in an emissive Fresnel outline, which is what
                // draws the bright gold edge in the reference render. Added to the colour, so it
                // brightens the sampled metal at grazing angles instead of painting over it.
                float nDotV = Math.min(1.0f, Math.abs(NORMAL_SCRATCH.dot(VIEW_SCRATCH)));
                float rim = (float)Math.pow(1.0f - nDotV, RIM_POWER) * RIM_STRENGTH;
                rim *= 0.35f + 0.65f * Math.max(skyAmount, blockAmount);

                float cr = Math.min(1.0f, baseR + spec * specR + rim * RIM_R);
                float cg = Math.min(1.0f, baseG + spec * specG + rim * RIM_G);
                float cb = Math.min(1.0f, baseB + spec * specB + rim * RIM_B);

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

    /**
     * Whether this wearer's wheel carries its own light: 1 when they have adapted to darkness, 0
     * otherwise.
     *
     * <p>The local player is asked through {@link ClientAdaption#active}, which is the mirror that
     * knows about the panel's off switch — reading the raw adapted set is the mistake that made
     * every other client-side effect ignore the switch. Anyone else on screen is read off the wheel
     * item they are wearing, because that is the only per-player state a client has for them; the
     * switch lives in the player attachment, which is not synced for other players, so a wheel
     * turned off still glows for an observer. That is a smaller wrong than not glowing at all.
     */
    private static float darknessGlowFor(Player player) {
        if (player.isLocalPlayer()) {
            return ClientAdaption.active(Concepts.ENV_DARKNESS) ? 1.0F : 0.0F;
        }

        boolean adapted = CuriosApi.getCuriosInventory(player)
                .flatMap(handler -> handler.findFirstCurio(ModItems.MAHORAGA_WHEEL.get()))
                .map(top.theillusivec4.curios.api.SlotResult::stack)
                .map(stack -> stack.get(ModDataComponents.WHEEL_DATA.get()))
                .map(ru.adaptionwheel.data.WheelData::adapted)
                .map(adaptedKeys -> adaptedKeys.contains(Concepts.ENV_DARKNESS))
                .orElse(false);
        return adapted ? 1.0F : 0.0F;
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
