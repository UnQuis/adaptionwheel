package ru.adaptionwheel.client.mixin;

import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.client.DimensionImpactFX;
import ru.adaptionwheel.client.mixin.accessor.PostChainAccessor;

/**
 * Runs the dimension-impact full-screen pass right after the HUD finishes compositing, so the
 * flash covers world and GUI alike. The injection point (immediately after
 * {@code GuiRenderer.render()} inside {@code GameRenderer.render}) and the FrameGraphBuilder /
 * PostChain / LevelTargetBundle call sequence below are copied from impact-frames' own
 * GameRendererMixin, which is confirmed working against 26.3 -- this part of the pipeline has no
 * documented public replacement for the old ShaderInstance/PostChain API, so matching a working
 * example beats guessing at it.
 */
@Mixin(GameRenderer.class)
public abstract class DimensionImpactMixin {

    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private CrossFrameResourcePool resourcePool;

    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/render/GuiRenderer;render()V", shift = At.Shift.AFTER))
    private void adaptionwheel$dimensionImpact(CallbackInfo ci) {
        if (!DimensionImpactFX.active()) {
            return;
        }
        Identifier id = Identifier.fromNamespaceAndPath(AdaptionWheel.MODID, "dimension_impact");
        PostChain chain = this.minecraft.getShaderManager().getPostChain(id, LevelTargetBundle.MAIN_TARGETS);
        if (chain == null) {
            return;
        }

        PostPass pass = ((PostChainAccessor) (Object) chain).adaptionwheel$passes().getFirst();
        var target = this.minecraft.gameRenderer.mainRenderTarget();
        DimensionImpactFX.writeUniforms(pass, target.width, target.height);

        FrameGraphBuilder frame = new FrameGraphBuilder();
        PostChain.TargetBundle targets = PostChain.TargetBundle.of(PostChain.MAIN_TARGET_ID,
                frame.importExternal("main", target));
        chain.addToFrame(frame, target.width, target.height, targets);
        frame.execute(this.resourcePool);
    }
}