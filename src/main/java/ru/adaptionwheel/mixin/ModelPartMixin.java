package ru.adaptionwheel.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.adaptionwheel.client.slashcut.CapturaCorpo;

/** Records the root model part of an entity currently being drawn, once per draw. */
@Mixin(ModelPart.class)
public abstract class ModelPartMixin {

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V",
            at = @At("HEAD"))
    private void adaptionwheel$captureModel(PoseStack pose, VertexConsumer consumer,
                                            int packedLight, int packedOverlay, int color,
                                            CallbackInfo ci) {
        CapturaCorpo.captureRootIfNeeded((ModelPart) (Object) this, pose, packedLight, color);
    }
}
