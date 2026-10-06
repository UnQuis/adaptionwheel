package ru.adaptionwheel.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import ru.adaptionwheel.AdaptionWheel;

import java.io.IOException;

/**
 * Draws the cursed slash as a real swept volume rather than a camera-facing quad.
 *
 * <p>The geometry is {@link SlashBladeMesh}: the same sine arch and taper the fragment shader used
 * to evaluate per pixel, now built as a tube with a genuine cross-section. The fragment stage is
 * left with nothing but colouring -- {@code SlashShape} is gone from it entirely, because the shape
 * no longer lives in the shader.
 *
 * <p>Unlike the impact frame this is <b>not</b> batched through a {@code RenderType}, and it is not
 * a post pass. A {@code RenderType} draws every primitive sharing it under one set of uniforms, so
 * the per-slash tint and fade would have nowhere to live -- the rift is violet and the cursed slash
 * cyan, and both fade out at the end of their life. Issuing one immediate draw per slash keeps both
 * on the uniform, and the mesh is a few hundred triangles, so batching would buy nothing anyway.
 *
 * <p>The transform is deliberately explicit rather than relying on {@code ModelViewMat}/{@code ProjMat}:
 * see {@link #viewProjection()}.
 */
@EventBusSubscriber(modid = AdaptionWheel.MODID, value = Dist.CLIENT)
public final class SlashShaderFX {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation SHADER =
            ResourceLocation.fromNamespaceAndPath(AdaptionWheel.MODID, "slash");

    private static ShaderInstance shader;

    private SlashShaderFX() {
    }

    /**
     * Standard alpha, NOT additive. Additive can only brighten, so against a blown-out sky it
     * saturates and the slash disappears — which is exactly what happened when the sprite was
     * replaced. The blade has to be able to darken the background to read on it.
     *
     * <p>The two-tone ink shader measures out at 5.9x the 5% Weber threshold against a noon sky at
     * its ink core, so the tint is passed straight through and the shader darkens nothing itself.
     * The halo is the marginal part at 1.3x, which is why the ink is what carries the silhouette.
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
     * One slash: a swept tube in world space, oriented by its caller.
     *
     * @param alpha 0..1 master fade, driven by the entity's life ratio; it rides the uniform
     *              because a vertex attribute cannot carry it without widening the format
     * @param glow  0..1 aura strength, same reason
     */
    public static void draw(PoseStack poseStack, float length, float curveAmount,
                            float glowHalf, float coreHalf, float taperPower,
                            int[] tint, float alpha, int[] glow, float glowAlpha) {
        ShaderInstance active = shader;
        if (active == null || alpha <= 0.01f) {
            return;
        }

        bladeBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        // Culling is ON, which is the opposite of every other pass in this mod and is correct only
        // for this mesh. The tube is closed and its traversal emits outward-facing normals
        // consistently (measured: 280 of 280 non-degenerate triangles agree), so only the near wall
        // is drawn and the blade composites exactly once. With NO_CULL both walls composite and a
        // 0.6-authored aura lands at 0.84, which reads as a denser, flatter blade. The degenerate
        // rings at the two tips cannot defeat this: a zero-area triangle is culled whatever its
        // winding.
        RenderSystem.enableCull();

        try {
            active.safeGetUniform("SlashProj").set(viewProjection());
            active.safeGetUniform("SlashTint").set(
                    ((tint[0] & 0xFF) / 255f), ((tint[1] & 0xFF) / 255f), ((tint[2] & 0xFF) / 255f), alpha);
            active.safeGetUniform("SlashGlow").set(
                    ((glow[0] & 0xFF) / 255f), ((glow[1] & 0xFF) / 255f), ((glow[2] & 0xFF) / 255f), glowAlpha);
            active.apply();

            BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES,
                    DefaultVertexFormat.POSITION_TEX);
            // PoseStack rather than a bare Matrix4f because Pose, the thing addVertex(pose, ...)
            // wants, is package-private in blaze3d and cannot be constructed from here.
            SlashBladeMesh.build(buffer, poseStack.last(),
                    length, curveAmount, glowHalf, coreHalf, taperPower);
            BufferUploader.draw(buffer.buildOrThrow());
        } finally {
            // This runs INSIDE the world pass, so nothing downstream re-establishes GL state for
            // us; leaving blending off would make every later quad in the frame composite wrongly.
            // `buildOrThrow` and the uniform lookups can all throw, so the restore cannot sit on
            // the success path either.
            active.clear();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
        }
    }
}