package ru.adaptionwheel.client.mixin.accessor;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import net.minecraft.client.renderer.PostPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/** Exposes PostPass's own per-pass dynamic uniform buffers -- the same map impact-frames' own
 *  ShaderHelper writes into, just reached through an accessor instead of assuming the field is
 *  public, which it is not. */
@Mixin(PostPass.class)
public interface PostPassAccessor {
    @Accessor("customUniforms")
    Map<String, com.mojang.renderpearl.api.buffers.GpuBuffer> adaptionwheel$customUniforms();
}