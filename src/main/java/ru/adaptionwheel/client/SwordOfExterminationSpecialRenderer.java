package ru.adaptionwheel.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.special.NoDataSpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import org.slf4j.Logger;
import ru.adaptionwheel.AdaptionWheel;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * 26.3 port of the 1.21.1 {@code SwordOfExterminationRenderer} (BEWLR).
 *
 * <p>The blade is not "held" the way a vanilla sword is: its base sits <b>inside the forearm</b> and
 * the blade runs along the arm, so the sword reads as a part of the arm rather than as an object
 * gripped by the fist. 26.3 removed BEWLR in favour of special item models; this is registered as a
 * {@link NoDataSpecialModelRenderer} and selected per display context from the item definition so that
 * GUI/ground/fixed use the regular icon model while hand contexts render this 3D blade.
 *
 * <p><b>The arm's frame.</b> {@code ItemInHandLayer} places a third-person item with
 * {@code translateToHand(arm)} (origin at the shoulder, arm local +Y pointing down the arm) followed
 * by {@code XP(-90)}, {@code YP(180)} and a small offset. In the frame this renderer receives, that
 * leaves +Y pointing towards the player's front and -Z pointing down the arm, past the hand. A
 * model whose blade runs along +Y therefore only needs {@code XP(-90)} to lie parallel to the
 * arm's axis — and the placement then moves it onto the outer face, so it follows every arm
 * animation for free — and the sink constant buries the base in the forearm instead of leaving it at
 * the fist. {@link #ARM_TILT} stays at zero.
 *
 * <p><b>The item box is 0..1, not -0.5..0.5.</b> The pose is re-centred by {@code translate(0.5, 0.5, 0.5)}
 * first, matching the 1.21.1 contract after {@code ItemRenderer}'s pre-translate.
 *
 * <p>First person is a different frame: {@code ItemInHandRenderer} has already translated the pose
 * to the hand in <b>view</b> space (+X right, +Y up, -Z forward), and vanilla draws no arm for a
 * non-empty stack — so the blade has to enter from the bottom-right corner on its own.
 *
 * <p>GUI does not reach this renderer: the item definition routes GUI/ground/fixed/on_shelf to the
 * regular sprite model.
 */
public final class SwordOfExterminationSpecialRenderer implements NoDataSpecialModelRenderer {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Identifier BLADE_MODEL = Identifier.fromNamespaceAndPath(
            AdaptionWheel.MODID, "models/entity/soe.obj");
    private static final Identifier BLADE_TEXTURE = Identifier.fromNamespaceAndPath(
            AdaptionWheel.MODID, "textures/item/soe_blade.png");

    /** Null until a reload has read the OBJ, and again if reading it failed. */
    private static volatile ObjMesh bladeMesh;

    private static final float BLADE_LENGTH = 1.25F;
    private static final float ARM_SINK = 0.04F;
    private static final float ARM_TILT = 18.0F;
    private static final float BLADE_ROLL = 90.0F;
    private static final float ARM_RADIUS = 0.06F;
    private static final float FIRST_PERSON_SCALE = 0.82F;
    private static final float FIRST_PERSON_TILT = 22.0F;
    private static final float FIRST_PERSON_SINK = 0.10F;

    private static final Vector3f LIGHT = new Vector3f(0.30F, 0.70F, 0.65F).normalize();
    private static final Vector3f NORMAL_SCRATCH = new Vector3f();
    private static final float AMBIENT = 0.50F;

    private final boolean firstPerson;
    private final boolean leftHand;

    public SwordOfExterminationSpecialRenderer(boolean firstPerson, boolean leftHand) {
        this.firstPerson = firstPerson;
        this.leftHand = leftHand;
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay,
                       boolean hasFoil, int outlineColor) {
        ObjMesh mesh = bladeMesh;
        if (mesh == null) {
            return;
        }

        poseStack.pushPose();
        // ItemRenderer has already applied translate(-0.5, -0.5, -0.5), so the item box spans
        // 0..1 here; everything below is written around a centred box.
        poseStack.translate(0.5F, 0.5F, 0.5F);

        if (firstPerson) {
            float scale = BLADE_LENGTH * FIRST_PERSON_SCALE / mesh.height();
            // First-person: extend the blade from the hand forward and slightly toward screen centre.
            poseStack.rotate(Axis.XP, (float) Math.toRadians(-82.0F + FIRST_PERSON_TILT));
            poseStack.rotate(Axis.YP, (float) Math.toRadians(leftHand ? 8.0F : -8.0F));
            poseStack.rotate(Axis.ZP, (float) Math.toRadians(leftHand ? -10.0F : 10.0F));
            poseStack.translate(leftHand ? -0.04F : 0.04F, -FIRST_PERSON_SINK, -0.05F);
            poseStack.scale(scale, scale, scale);
        } else {
            float scale = BLADE_LENGTH / mesh.height();
            // Third-person: orient the blade so it emerges from the hand/fist and points forward-down
            // in a natural way instead of lying flat/crooked along the forearm.
            poseStack.rotate(Axis.XP, (float) Math.toRadians(-72.0F - ARM_TILT));
            poseStack.rotate(Axis.YP, (float) Math.toRadians(leftHand ? 10.0F : -10.0F));
            poseStack.rotate(Axis.ZP, (float) Math.toRadians(leftHand ? -18.0F : 18.0F));
            poseStack.translate(leftHand ? -0.02F : 0.02F, -ARM_SINK, -0.02F);
            poseStack.scale(scale, scale, scale);
        }

        // ObjMesh is centred on its bounding box; the blade has to hang from its base.
        poseStack.translate(0.0F, mesh.height() * 0.5F, 0.0F);
        drawBlade(mesh, poseStack, collector, light, overlay);
        poseStack.popPose();
    }

    @Override
    public void getExtents(Consumer<org.joml.Vector3fc> output) {
        // Conservative cube around the blade; used for culling/particle bounds.
        output.accept(new Vector3f(-0.75F, -0.75F, -0.75F));
        output.accept(new Vector3f(0.75F, 0.75F, 0.75F));
    }

    /**
     * Pushes the OBJ's triangle soup into the entity vertex format.
     *
     * <p>Four corners per triangle, not three: the render type's mode is QUADS. Shading rides in
     * the vertex colour, which the entity shader multiplies with the texture. The light is a fixed
     * view-space direction (see {@link #LIGHT}). Two-sided via abs() because the blade is a shell.
     */
    private static void drawBlade(ObjMesh mesh, PoseStack poseStack, SubmitNodeCollector collector,
                                  int light, int overlay) {
        RenderType renderType = RenderTypes.entityCutoutNoCull(BLADE_TEXTURE);
        float[] corners = mesh.quadCorners();
        collector.submitCustomGeometry(poseStack, renderType, (pose, consumer) -> {
            Matrix3f normalMatrix = pose.normal();
            for (int i = 0; i < corners.length; i += ObjMesh.STRIDE * 4) {
                NORMAL_SCRATCH.set(corners[i + 5], corners[i + 6], corners[i + 7]).mul(normalMatrix).normalize();
                float shade = AMBIENT + (1.0F - AMBIENT) * Math.abs(NORMAL_SCRATCH.dot(LIGHT));

                for (int corner = 0; corner < 4; corner++) {
                    int o = i + corner * ObjMesh.STRIDE;
                    consumer.addVertex(pose, corners[o], corners[o + 1], corners[o + 2])
                            .setColor(shade, shade, shade, 1.0F)
                            .setUv(corners[o + 3], corners[o + 4])
                            .setOverlay(overlay)
                            .setLight(light)
                            .setNormal(NORMAL_SCRATCH.x(), NORMAL_SCRATCH.y(), NORMAL_SCRATCH.z());
                }
            }
        });
    }

    /** Render types helper: prefer a no-cull entity cutout for the thin double-sided blade. */
    private static final class RenderTypes {
        private RenderTypes() {
        }

        static RenderType entityCutoutNoCull(Identifier texture) {
            // 26.3: build from ENTITY_CUTOUT pipeline (no cull) with texture + lightmap + overlay.
            return RenderType.create("adaptionwheel_entity_cutout_no_cull",
                    RenderSetup.builder(net.minecraft.client.renderer.RenderPipelines.ENTITY_CUTOUT)
                            .withTexture("Sampler0", texture)
                            .useLightmap()
                            .useOverlay()
                            .setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE)
                            .createRenderSetup());
        }
    }

    /** Reload listener that reads the OBJ during a resource reload. */
    public static final class MeshReloadListener extends SimplePreparableReloadListener<ObjMesh> {

        @Override
        protected ObjMesh prepare(ResourceManager resources, ProfilerFiller profiler) {
            Optional<net.minecraft.server.packs.resources.Resource> resource = resources.getResource(BLADE_MODEL);
            if (resource.isEmpty()) {
                LOGGER.error("[{}] Sword model {} is not in the resource manager; the blade will not draw",
                        AdaptionWheel.MODID, BLADE_MODEL);
                return null;
            }

            try (BufferedReader reader = resource.get().openAsReader()) {
                ObjMesh mesh = ObjMesh.parse(reader);
                LOGGER.info("[{}] Sword model {}: {} faces -> {} triangles, {} units long",
                        AdaptionWheel.MODID, BLADE_MODEL, mesh.faceCount(), mesh.triangleCount(), mesh.height());
                return mesh;
            } catch (IOException | RuntimeException e) {
                LOGGER.error("[{}] Could not read sword model {}; the blade will not draw",
                        AdaptionWheel.MODID, BLADE_MODEL, e);
                return null;
            }
        }

        @Override
        protected void apply(ObjMesh mesh, ResourceManager resources, ProfilerFiller profiler) {
            bladeMesh = mesh;
        }
    }

    /** Unbaked special model renderer for registration and JSON codec. */
    public record Unbaked(boolean firstPerson, boolean leftHand) implements NoDataSpecialModelRenderer.Unbaked {
        public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.BOOL.optionalFieldOf("first_person", false).forGetter(Unbaked::firstPerson),
                Codec.BOOL.optionalFieldOf("left_hand", false).forGetter(Unbaked::leftHand)
        ).apply(instance, Unbaked::new));

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }

        @Override
        public SpecialModelRenderer<Void> bake(SpecialModelRenderer.BakingContext context) {
            return new SwordOfExterminationSpecialRenderer(firstPerson, leftHand);
        }
    }
}
