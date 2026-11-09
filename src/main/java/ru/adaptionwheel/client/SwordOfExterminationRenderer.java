package ru.adaptionwheel.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import org.slf4j.Logger;
import ru.adaptionwheel.AdaptionWheel;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Optional;

/**
 * Draws the Sword of Extermination's real 3D blade instead of a flat sprite.
 *
 * <p>The blade is not "held" the way a vanilla sword is: its base sits <b>inside the forearm</b> and
 * the blade runs along the arm, so the sword reads as a part of the arm rather than as an object
 * gripped by the fist. That is the whole point of the model, and it is why this is a custom item
 * renderer rather than a {@code handheld} JSON model: the placement has to be expressed relative to
 * the arm's frame, and the arm's frame is only known here.
 *
 * <p><b>The arm's frame.</b> {@code ItemInHandLayer} places a third-person item with
 * {@code translateToHand(arm)} (origin at the shoulder, arm local +Y pointing down the arm) followed
 * by {@code XP(-90)}, {@code YP(180)} and a small offset. In the frame this renderer receives, that
 * leaves +Y pointing towards the player's front and -Z pointing down the arm, past the hand. A
 * model whose blade runs along +Y therefore only needs {@code XP(-90)} to lie parallel to the
 * arm's axis — and the placement then moves it onto the outer face, so it follows every arm
 * animation for free — and the sink
 * constant buries the base in the forearm instead of leaving it at the fist. {@link #ARM_TILT} is
 * a knob that stays at zero: tilting the blade away from the arm was tried and is precisely what
 * makes it read as a held object instead of as part of the arm.
 *
 * <p><b>The item box is 0..1, not -0.5..0.5.</b> {@code ItemRenderer} applies
 * {@code translate(-0.5, -0.5, -0.5)} <em>before</em> it hands the pose to a custom renderer, so
 * the box spans 0..1 here. Everything below is written around a centred box, so this method's first
 * act is to move the origin back to the centre. Leaving that out is not a small offset: the blade
 * hangs half a block below the hand, at the hem of the robe — which reads as "the sword grows out of
 * the leg" — and the GUI icon lands half a slot up and to the left. Both symptoms, one cause, and
 * neither throws or logs anything.
 *
 * <p>First person is a different frame: {@code ItemInHandRenderer} has already translated the pose
 * to the hand in <b>view</b> space (+X right, +Y up, -Z forward), and vanilla draws no arm for a
 * non-empty stack — so the blade has to enter from the bottom-right corner on its own, which is
 * what the sink and the smaller scale there arrange.
 *
 * <p>The GUI does not use the mesh at all: the inventory icon stays the author's 32x32 sprite,
 * drawn as a quad. A 3D blade in a 16-pixel slot is unreadable, and the sprite is already the
 * item's face everywhere else.
 */
public final class SwordOfExterminationRenderer extends BlockEntityWithoutLevelRenderer {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final ResourceLocation BLADE_MODEL = ResourceLocation.fromNamespaceAndPath(
            AdaptionWheel.MODID, "models/entity/soe.obj");
    private static final ResourceLocation BLADE_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            AdaptionWheel.MODID, "textures/item/soe_blade.png");
    private static final ResourceLocation ICON_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            AdaptionWheel.MODID, "textures/item/sword_of_extermination.png");

    /** Null until a reload has read the OBJ, and again if reading it failed. */
    private static volatile ObjMesh bladeMesh;

    /** One per client; the constructor needs the client's render dispatchers, so it is lazy. */
    private static volatile SwordOfExterminationRenderer instance;

    /**
     * Base-to-tip length of the blade in blocks. The model is 9.06 source units long, so this is
     * also the scale denominator. A vanilla sword is one block; this one is longer on purpose —
     * it has to read as an arm's length of steel, not as a dagger.
     */
    private static final float BLADE_LENGTH = 1.25F;

    /**
     * How far up the forearm the base sits, in blocks. The layer's offset puts the model's origin
     * at the hand, and a sword that starts at the hand still looks gripped; sinking it by a third
     * of a block puts the base inside the forearm, so the steel appears to grow out of the arm.
     */
    private static final float ARM_SINK = 0.30F;

    /**
     * Tilt away from the arm's own axis, in degrees. Zero is the requirement, not a default: the
     * blade must stay parallel to the forearm, so it reads as the arm continuing into steel and
     * follows every arm animation for free. A non-zero value was tried to lift the tip off the
     * ground and was wrong — it makes the sword look gripped and angled rather than grown. A
     * 1.25-block blade reaches the ground with the arm at rest; that is the model being long, not
     * the placement being off.
     */
    private static final float ARM_TILT = 0.0F;

    /**
     * Roll of the blade about its own long axis, in degrees. The mesh is flat in its XY plane
     * (width along X, thickness along Z), so {@code XP(-90)} alone turns the broad face towards the
     * player's front — from the front the blade reads as a plate. The requirement is that the face
     * is parallel to the arm's <b>outer</b> side, so the sword is seen edge-on from the front and
     * broad from the side; a quarter turn about the blade's own axis is exactly that.
     *
     * <p>Applied <em>after</em> {@code XP(-90)}, so it turns about model +Y — the axis that
     * {@code XP(-90)} has already laid down the arm — and not about the frame's own Y, which is the
     * player's front.
     */
    private static final float BLADE_ROLL = 90.0F;

    /**
     * Half the width and half the depth of a player's arm, in blocks (the vanilla arm box is
     * 4&times;4 pixels). The layer's frame puts the item origin on the arm's <b>centre line</b> in X
     * and on its <b>front face</b> in Y, so these are the two offsets that move the blade from
     * there onto the outer face: one radius sideways, one radius back to the arm's mid-depth.
     *
     * <p>Measured against the classic 4-pixel arm. A slim (3-pixel) arm is 1.5 pixels narrower, so
     * the blade would sit that much off its surface; the layer hands the renderer no model, so the
     * width cannot be asked for and the common case is used.
     */
    private static final float ARM_RADIUS = 0.125F;

    /** First-person scale, relative to {@link #BLADE_LENGTH}. */
    private static final float FIRST_PERSON_SCALE = 0.85F;

    /** First-person upward tilt, in degrees. */
    private static final float FIRST_PERSON_TILT = 30.0F;

    /** First-person sink towards the camera, in blocks, so the base leaves the frame. */
    private static final float FIRST_PERSON_SINK = 0.18F;

    /** Dropped and framed items: one block tall, standing on the bottom of the item box. */
    private static final float LOOSE_LENGTH = 1.0F;

    /**
     * Light direction in <b>view</b> space, not world space: the pose this renderer receives is
     * already relative to the camera, so a world-space vector would need the camera rotation put
     * back in. A view-space light also means the blade is never lit from behind the viewer, which
     * matters because its two faces are the only surfaces it has.
     */
    private static final Vector3f LIGHT = new Vector3f(0.30F, 0.70F, 0.65F).normalize();
    private static final Vector3f NORMAL_SCRATCH = new Vector3f();
    private static final float AMBIENT = 0.50F;

    private SwordOfExterminationRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    /** Lazily built on the render thread, the first time an item is drawn. */
    public static SwordOfExterminationRenderer get() {
        SwordOfExterminationRenderer local = instance;
        if (local == null) {
            synchronized (SwordOfExterminationRenderer.class) {
                local = instance;
                if (local == null) {
                    local = new SwordOfExterminationRenderer();
                    instance = local;
                }
            }
        }
        return local;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose,
                             MultiBufferSource buffers, int light, int overlay) {
        pose.pushPose();
        // ItemRenderer has already applied translate(-0.5, -0.5, -0.5), so the item box spans
        // 0..1 here; everything below is written around a centred box.
        pose.translate(0.5F, 0.5F, 0.5F);

        if (context == ItemDisplayContext.GUI) {
            drawIcon(pose, buffers, light, overlay);
            pose.popPose();
            return;
        }

        ObjMesh mesh = bladeMesh;
        if (mesh == null) {
            pose.popPose();
            return;
        }

        switch (context) {
            case FIRST_PERSON_RIGHT_HAND, FIRST_PERSON_LEFT_HAND -> {
                float scale = BLADE_LENGTH * FIRST_PERSON_SCALE / mesh.height();
                // -Z is forward in view space, so XP(-90) lays the blade down the view axis; the
                // tilt lifts the tip towards the centre of the screen.
                pose.mulPose(Axis.XP.rotationDegrees(-90.0F + FIRST_PERSON_TILT));
                pose.translate(0.0F, -FIRST_PERSON_SINK, 0.0F);
                pose.scale(scale, scale, scale);
            }
            case THIRD_PERSON_RIGHT_HAND, THIRD_PERSON_LEFT_HAND -> {
                float scale = BLADE_LENGTH / mesh.height();
                boolean left = context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
                // -Z is down the arm in the layer's frame, so XP(-90) puts the blade on the arm's
                // own axis; BLADE_ROLL then turns its flat face from the player's front onto the
                // arm's outer face.
                pose.mulPose(Axis.XP.rotationDegrees(-90.0F + ARM_TILT));
                pose.mulPose(Axis.YP.rotationDegrees(BLADE_ROLL));
                // A roll alone leaves the plate centred on the arm's axis, buried in it: ObjMesh
                // centres the mesh, and the origin it is centred on is the arm's centre line and
                // front face. After the roll local X -> frame -Y (depth) and local Z -> frame +X
                // (sideways), so these two moves put the blade's long axis in the outer face.
                pose.translate(ARM_RADIUS, 0.0F, left ? -ARM_RADIUS : ARM_RADIUS);
                pose.translate(0.0F, -ARM_SINK, 0.0F);
                pose.scale(scale, scale, scale);
            }
            case GROUND, FIXED, NONE -> {
                float scale = LOOSE_LENGTH / mesh.height();
                pose.translate(0.0F, -0.5F, 0.0F);
                pose.scale(scale, scale, scale);
            }
            default -> {
                pose.popPose();
                return;
            }
        }

        // ObjMesh is centred on its bounding box; the blade has to hang from its base.
        pose.translate(0.0F, mesh.height() * 0.5F, 0.0F);
        drawBlade(mesh, pose, buffers, light, overlay);
        pose.popPose();
    }

    /**
     * The inventory icon: the original sprite, drawn as a quad filling the item's one-block box.
     * The GUI frame has +Y up, so the texture's top edge (v = 0) belongs at +0.5.
     */
    private static void drawIcon(PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(ICON_TEXTURE));
        PoseStack.Pose last = pose.last();
        vertex(consumer, last, -0.5F, -0.5F, 0.0F, 1.0F, light, overlay);
        vertex(consumer, last, 0.5F, -0.5F, 1.0F, 1.0F, light, overlay);
        vertex(consumer, last, 0.5F, 0.5F, 1.0F, 0.0F, light, overlay);
        vertex(consumer, last, -0.5F, 0.5F, 0.0F, 0.0F, light, overlay);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose,
                               float x, float y, float u, float v, int light, int overlay) {
        consumer.addVertex(pose, x, y, 0.0F)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(0.0F, 0.0F, 1.0F);
    }

    /**
     * Pushes the OBJ's triangle soup into the entity vertex format.
     *
     * <p>Four corners per triangle, not three: the render type's mode is {@code QUADS}, and three
     * vertices per triangle are grouped across triangle boundaries into slivers that leave holes in
     * the mesh. See {@link ObjMesh#quadCorners()}.
     *
     * <p>Shading rides in the vertex colour, which the entity shader multiplies with the texture;
     * that is what lets an unlit-looking flat-shaded mesh go through a vanilla render type with no
     * custom shader. The light is a fixed view-space direction (see {@link #LIGHT}), so the blade is
     * always lit from the viewer's side rather than from a sun the item cannot see.
     */
    private static void drawBlade(ObjMesh mesh, PoseStack pose, MultiBufferSource buffers,
                                  int light, int overlay) {
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(BLADE_TEXTURE));
        PoseStack.Pose last = pose.last();
        Matrix3f normalMatrix = last.normal();
        float[] corners = mesh.quadCorners();
        for (int i = 0; i < corners.length; i += ObjMesh.STRIDE * 4) {
            NORMAL_SCRATCH.set(corners[i + 5], corners[i + 6], corners[i + 7]).mul(normalMatrix).normalize();
            // Two-sided: a blade is a shell, and its two flat faces are equally visible depending
            // on which side of the player the camera sits. Without abs() the face turned away from
            // the light would go flat dark from the third-person camera.
            float shade = AMBIENT + (1.0F - AMBIENT) * Math.abs(NORMAL_SCRATCH.dot(LIGHT));

            for (int corner = 0; corner < 4; corner++) {
                int o = i + corner * ObjMesh.STRIDE;
                consumer.addVertex(last, corners[o], corners[o + 1], corners[o + 2])
                        .setColor(shade, shade, shade, 1.0F)
                        .setUv(corners[o + 3], corners[o + 4])
                        .setOverlay(overlay)
                        .setLight(light)
                        .setNormal(NORMAL_SCRATCH.x(), NORMAL_SCRATCH.y(), NORMAL_SCRATCH.z());
            }
        }
    }

    /**
     * Reads the OBJ during a resource reload rather than on the first frame the sword is seen: the
     * parse belongs behind the loading screen, and a resource pack that replaces the model should
     * take effect on F3+T like every other asset.
     */
    public static final class MeshReloadListener extends SimplePreparableReloadListener<ObjMesh> {

        @Override
        protected ObjMesh prepare(ResourceManager resources, ProfilerFiller profiler) {
            Optional<Resource> resource = resources.getResource(BLADE_MODEL);
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
}
