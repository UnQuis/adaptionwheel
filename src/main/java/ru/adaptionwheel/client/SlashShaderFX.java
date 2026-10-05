package ru.adaptionwheel.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.slf4j.Logger;
import ru.adaptionwheel.AdaptionWheel;

import java.io.IOException;

/**
 * Draws the cursed slash as a procedural blade instead of a flying sprite.
 *
 * <p>The shape lives entirely in {@code slash.fsh} — a tapered bowed blade with a white-hot edge
 * and a halo — so the cut stays crisp at any distance and any resolution, which a fixed-size PNG
 * could not do: the old sprite was blurry up close and dissolved into its own pixels at range.
 *
 * <p>Unlike the impact frame this is <b>not</b> batched through a {@code RenderType}, and it is not
 * a post pass. It issues one immediate draw per slash with its own uniform values, which is what
 * lets a single program serve every slash on screen with a different roll, size and tint. The
 * caller keeps owning the motion — growth, opacity, roll — on the CPU exactly as before; only the
 * pixels changed.
 *
 * <p>The transform is deliberately explicit rather than relying on {@code ModelViewMat}/{@code ProjMat}:
 * see {@link #viewProjection()}.
 */
@EventBusSubscriber(modid = AdaptionWheel.MODID, value = Dist.CLIENT)
public final class SlashShaderFX {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation SHADER =
            ResourceLocation.fromNamespaceAndPath(AdaptionWheel.MODID, "slash");

    /**
     * How far the blade's belly dips, in half-heights of its own quad. Directly meaningful: it
     * measured out at 0.40 gives a blade spanning ~53% of the quad's width with a visible bow.
     */
    private static final float BOW = 0.40f;
    /** Half-thickness at the belly, then the white core inside it. */
    private static final float THICKNESS = 0.16f;
    private static final float CORE_WIDTH = 0.045f;
    /** Higher tapers harder, so the tips come to a sharper point. */
    private static final float TAPER = 1.35f;

    private static ShaderInstance shader;

    private SlashShaderFX() {
    }

    /**
     * Standard alpha, NOT additive. Additive can only brighten, so against a blown-out sky it
     * saturates and the slash disappears — which is exactly what happened when the sprite was
     * replaced. The blade has to be able to darken the background to read on it.
     */
    private static void bladeBlend() {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
    }

    /**
     * View-projection for this draw.
     *
     * <p>Both halves are needed and getting this wrong is silent: vanilla's transform is
     * {@code ProjMat * ModelViewMat * pos}, and in 1.21.1 the camera's view lives in the
     * <i>model-view</i> matrix — {@code getProjectionMatrix()} is the projection alone. Feeding only
     * the projection places the blade in unviewed world coordinates, off screen, which looks
     * exactly like the slash not rendering at all.
     */
    private static final Matrix4f VIEW_PROJECTION = new Matrix4f();

    private static Matrix4f viewProjection() {
        VIEW_PROJECTION.set(RenderSystem.getProjectionMatrix());
        return VIEW_PROJECTION.mul(RenderSystem.getModelViewMatrix());
    }

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(), SHADER, DefaultVertexFormat.POSITION_TEX),
                    instance -> shader = instance);
        } catch (IOException e) {
            LOGGER.error("[{}] Could not load the slash shader; slashes will not draw",
                    AdaptionWheel.MODID, e);
        }
    }

    public static boolean ready() {
        return shader != null;
    }

    /**
     /**
     * One slash: a world-space quad billboarded on {@code side}/{@code up}, shaped by the shader.
     *
     * @param alpha      0..1 master fade, driven by the entity's life ratio
     * @param glowAlpha  0..1 halo strength; the halo is part of the same draw, not a second sprite
     */
    public static void draw(Matrix4f pose, Vec3 side, Vec3 up, Vec3 center,
                            float width, float height,
                            int[] tint, float alpha, int[] glow, float glowAlpha) {
        ShaderInstance active = shader;
        if (active == null || alpha <= 0.01f) {
            return;
        }

        bladeBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        // Same trap as the impact frame: world passes leave culling on, and this quad's winding
        // is not guaranteed to survive the pose stack's basis.
        RenderSystem.disableCull();

        try {
            active.safeGetUniform("SlashProj").set(viewProjection());
            active.safeGetUniform("SlashShape").set(BOW, THICKNESS, CORE_WIDTH, TAPER);
            active.safeGetUniform("SlashTint").set(
                    ((tint[0] & 0xFF) / 255f), ((tint[1] & 0xFF) / 255f), ((tint[2] & 0xFF) / 255f), alpha);
            active.safeGetUniform("SlashGlow").set(
                    ((glow[0] & 0xFF) / 255f), ((glow[1] & 0xFF) / 255f), ((glow[2] & 0xFF) / 255f), glowAlpha);
            active.apply();

            Vec3 halfSide = side.scale(width * 0.5);
            Vec3 halfUp = up.scale(height * 0.5);
            BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,
                    DefaultVertexFormat.POSITION_TEX);
            corner(buffer, pose, center.subtract(halfSide).add(halfUp), 0f, 0f);
            corner(buffer, pose, center.subtract(halfSide).subtract(halfUp), 0f, 1f);
            corner(buffer, pose, center.add(halfSide).subtract(halfUp), 1f, 1f);
            corner(buffer, pose, center.add(halfSide).add(halfUp), 1f, 0f);
            BufferUploader.draw(buffer.buildOrThrow());
        } finally {
            // Unlike the impact frame on the GUI hook, this runs INSIDE the world pass, so nothing
            // downstream re-establishes GL state for us: leaving culling off makes every later quad
            // in the frame shade back faces. `buildOrThrow` and the uniform lookups can all throw,
            // so the restore cannot sit on the success path either.
            active.clear();
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
        }
    }

    /** The pose stack is baked into the vertex on the CPU, as the sprite path did; the shader then
     * applies the view-projection, matching how vanilla transforms entity geometry. */
    private static void corner(BufferBuilder buffer, Matrix4f pose, Vec3 position, float u, float v) {
        Vector4f world = new Vector4f((float) position.x, (float) position.y, (float) position.z, 1f);
        pose.transform(world);
        buffer.addVertex(world.x, world.y, world.z).setUv(u, v);
    }
}